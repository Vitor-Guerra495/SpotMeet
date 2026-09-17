package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.AccessRequestResponseDTO;
import com.spotmeet.backend.dto.OrganizationResponseDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.AccessRequest.AccessRequestStatus;
import com.spotmeet.backend.model.OrganizationMember.OrganizationRole;
import com.spotmeet.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Access, membership and role hierarchy management inside an Organization.
 *
 * Role hierarchy:
 *   ORG_OWNER      -> Absolute owner. Can promote a subowner to vice owner and promote/demote any role.
 *   ORG_VICE_OWNER -> Vice owner. Approves join requests, manages committees and promotes members to subowner.
 *   ORG_SUBOWNER   -> Subowner. Manages committees and their members.
 *   MEMBER         -> Regular member. Can view and request access.
 */
@Service
public class MembershipService {

    @Autowired private AccessRequestRepository accessRequestRepo;
    @Autowired private OrganizationRepository organizationRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private OrganizationMemberRepository organizationMemberRepo;
    @Autowired private CommitteeMemberRepository committeeMemberRepo;
    @Autowired private AuditLogRepository auditLogRepo;

    // Authorization helpers

    private User findUserByEmail(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado nao encontrado."));
    }

    /**
     * Requires the executor to be an active member of the organization or SYSADMIN.
     */
    public void requireOrganizationMember(User executor, Long organizationId) {
        if ("SYSADMIN".equalsIgnoreCase(executor.getRole())) {
            return;
        }

        Organization org = organizationRepo.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organizacao nao encontrada."));

        if (org.getOwner() != null && org.getOwner().getId().equals(executor.getId())) {
            return;
        }

        if (!organizationMemberRepo.existsByUserIdAndOrganizationId(executor.getId(), organizationId)) {
            throw new SecurityException("Acesso negado. Voce nao e membro desta organizacao.");
        }
    }

    /**
     * Requires the executor to be owner (ORG_OWNER), vice owner (ORG_VICE_OWNER) or SYSADMIN.
     */
    public void requireOwnerOrViceOwner(User executor, Long organizationId) {
        if ("SYSADMIN".equalsIgnoreCase(executor.getRole())) {
            return;
        }

        Organization org = organizationRepo.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organizacao nao encontrada."));

        if (org.getOwner() != null && org.getOwner().getId().equals(executor.getId())) {
            return;
        }

        organizationMemberRepo.findByUserIdAndOrganizationId(executor.getId(), organizationId)
                .filter(m -> m.getRole() == OrganizationRole.ORG_OWNER || m.getRole() == OrganizationRole.ORG_VICE_OWNER)
                .orElseThrow(() -> new SecurityException(
                    "Acesso negado. Apenas o Dono ou Vice-Lider da organizacao pode realizar esta operacao."
                ));
    }

    /**
     * Requires the executor to be exclusively the owner (ORG_OWNER) or SYSADMIN.
     */
    public void requireOwner(User executor, Long organizationId) {
        if ("SYSADMIN".equalsIgnoreCase(executor.getRole())) {
            return;
        }

        Organization org = organizationRepo.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organizacao nao encontrada."));

        if (org.getOwner() != null && org.getOwner().getId().equals(executor.getId())) {
            return;
        }

        organizationMemberRepo.findByUserIdAndOrganizationId(executor.getId(), organizationId)
                .filter(m -> m.getRole() == OrganizationRole.ORG_OWNER)
                .orElseThrow(() -> new SecurityException(
                    "Acesso negado. Operacao restrita exclusivamente ao Dono da organizacao."
                ));
    }

    // 1. Request access by key (any authenticated user)

    @Transactional
    public AccessRequestResponseDTO requestAccess(String requesterEmail, String accessKey) {
        if (accessKey == null || accessKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Chave da organizacao e obrigatoria.");
        }
        String cleanKey = accessKey.trim();

        Organization org = organizationRepo.findByAccessKey(cleanKey)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Organizacao com a chave '" + cleanKey + "' nao encontrada. Verifique a chave e tente novamente."
                ));

        User requester = findUserByEmail(requesterEmail);

        if (org.getOwner() != null && org.getOwner().getId().equals(requester.getId())) {
            throw new IllegalStateException("Voce e o proprietario desta organizacao.");
        }

        if (organizationMemberRepo.existsByUserIdAndOrganizationId(requester.getId(), org.getId())) {
            throw new IllegalStateException("Voce ja e membro desta organizacao.");
        }

        List<AccessRequest> existing = accessRequestRepo.findByUserIdAndOrganizationId(requester.getId(), org.getId());
        AccessRequest request;
        if (!existing.isEmpty()) {
            request = existing.get(0);
            if (request.getStatus() == AccessRequestStatus.PENDING) {
                throw new IllegalStateException("Voce ja possui uma solicitacao pendente para esta organizacao.");
            }
            // Reopen the previous request (e.g. user removed or previously rejected)
            request.setStatus(AccessRequestStatus.PENDING);
            request.setRequestedAt(java.time.LocalDateTime.now());
        } else {
            request = new AccessRequest();
            request.setUser(requester);
            request.setOrganization(org);
            request.setStatus(AccessRequestStatus.PENDING);
            request.setRequestedAt(java.time.LocalDateTime.now());
        }

        AccessRequest saved = accessRequestRepo.save(request);

        auditLogRepo.save(new AuditLog(
            requester,
            "ACCESS_REQUESTED",
            "Solicitou acesso a org: " + org.getName() + " (chave: " + org.getAccessKey() + ")"
        ));

        return AccessRequestResponseDTO.fromEntity(saved);
    }

    // 2. Approve request (owner or vice owner)

    @Transactional
    public AccessRequestResponseDTO approveAccess(Long requestId, String leaderEmail) {
        AccessRequest request = accessRequestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitacao nao encontrada."));

        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new IllegalStateException(
                "Apenas solicitacoes PENDENTES podem ser aprovadas. Status atual: " + request.getStatus()
            );
        }

        User leader = findUserByEmail(leaderEmail);
        requireOwnerOrViceOwner(leader, request.getOrganization().getId());

        request.setStatus(AccessRequestStatus.APPROVED);
        accessRequestRepo.save(request);

        boolean alreadyMember = organizationMemberRepo.existsByUserIdAndOrganizationId(
            request.getUser().getId(), request.getOrganization().getId()
        );

        if (!alreadyMember) {
            OrganizationMember membership = new OrganizationMember();
            membership.setUser(request.getUser());
            membership.setOrganization(request.getOrganization());
            membership.setRole(OrganizationRole.MEMBER);
            organizationMemberRepo.save(membership);
        }

        auditLogRepo.save(new AuditLog(
            leader,
            "ACCESS_APPROVED",
            "Solicitacao #" + requestId + " | Usuario: " + request.getUser().getEmail()
                + " | Org: " + request.getOrganization().getName()
        ));

        return AccessRequestResponseDTO.fromEntity(request);
    }

    // 3. Reject request (owner or vice owner)

    @Transactional
    public AccessRequestResponseDTO rejectAccess(Long requestId, String leaderEmail) {
        AccessRequest request = accessRequestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitacao nao encontrada."));

        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new IllegalStateException(
                "Apenas solicitacoes PENDENTES podem ser rejeitadas. Status atual: " + request.getStatus()
            );
        }

        User leader = findUserByEmail(leaderEmail);
        requireOwnerOrViceOwner(leader, request.getOrganization().getId());

        request.setStatus(AccessRequestStatus.REJECTED);
        accessRequestRepo.save(request);

        auditLogRepo.save(new AuditLog(
            leader,
            "ACCESS_REJECTED",
            "Solicitacao #" + requestId + " | Usuario: " + request.getUser().getEmail()
                + " | Org: " + request.getOrganization().getName()
        ));

        return AccessRequestResponseDTO.fromEntity(request);
    }

    // 4. Role promotion and demotion

    /**
     * Promotes a member in the organization:
     *   - MEMBER -> ORG_SUBOWNER: allowed for owners and vice owners.
     *   - ORG_SUBOWNER -> ORG_VICE_OWNER: restricted to the owner.
     */
    @Transactional
    public OrganizationResponseDTO.MemberDTO promoteMember(Long organizationId, Long targetUserId, String executorEmail) {
        User executor = findUserByEmail(executorEmail);
        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario informado nao encontrado."));

        OrganizationMember targetMembership = organizationMemberRepo
                .findByUserIdAndOrganizationId(target.getId(), organizationId)
                .orElseThrow(() -> new IllegalArgumentException("O usuario informado nao pertence a esta organizacao."));

        if (targetMembership.getRole() == OrganizationRole.ORG_OWNER) {
            throw new IllegalStateException("Nao e possivel alterar o cargo do Dono da organizacao.");
        }

        if (targetMembership.getRole() == OrganizationRole.MEMBER) {
            // Only owners and vice owners can promote to subowner
            requireOwnerOrViceOwner(executor, organizationId);
            targetMembership.setRole(OrganizationRole.ORG_SUBOWNER);
            organizationMemberRepo.save(targetMembership);

            auditLogRepo.save(new AuditLog(
                executor,
                "ROLE_PROMOTED_SUBOWNER",
                "Usuario: " + target.getEmail() + " elevado a SUBDONO na Org ID: " + organizationId
            ));
        } else if (targetMembership.getRole() == OrganizationRole.ORG_SUBOWNER) {
            // Owner only
            requireOwner(executor, organizationId);
            targetMembership.setRole(OrganizationRole.ORG_VICE_OWNER);
            organizationMemberRepo.save(targetMembership);

            auditLogRepo.save(new AuditLog(
                executor,
                "ROLE_PROMOTED_VICE_OWNER",
                "Usuario: " + target.getEmail() + " elevado a VICE-LIDER na Org ID: " + organizationId
            ));
        } else if (targetMembership.getRole() == OrganizationRole.ORG_VICE_OWNER) {
            throw new IllegalStateException("O integrante ja possui o cargo maximo de Vice-Lider.");
        }

        return new OrganizationResponseDTO.MemberDTO(
            target.getId(),
            target.getName(),
            target.getEmail(),
            targetMembership.getRole().name()
        );
    }

    /**
     * Demotes a member in the organization:
     *   - ORG_VICE_OWNER -> ORG_SUBOWNER: owner only.
     *   - ORG_SUBOWNER -> MEMBER: owner or vice owner.
     */
    @Transactional
    public OrganizationResponseDTO.MemberDTO demoteMember(Long organizationId, Long targetUserId, String executorEmail) {
        User executor = findUserByEmail(executorEmail);
        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario informado nao encontrado."));

        OrganizationMember targetMembership = organizationMemberRepo
                .findByUserIdAndOrganizationId(target.getId(), organizationId)
                .orElseThrow(() -> new IllegalArgumentException("O usuario informado nao pertence a esta organizacao."));

        if (targetMembership.getRole() == OrganizationRole.ORG_OWNER) {
            throw new IllegalStateException("Nao e possivel alterar o cargo do Dono da organizacao.");
        }

        if (targetMembership.getRole() == OrganizationRole.ORG_VICE_OWNER) {
            requireOwner(executor, organizationId);
            targetMembership.setRole(OrganizationRole.ORG_SUBOWNER);
            organizationMemberRepo.save(targetMembership);

            auditLogRepo.save(new AuditLog(
                executor,
                "ROLE_DEMOTED_SUBOWNER",
                "Usuario: " + target.getEmail() + " rebaixado a SUBDONO na Org ID: " + organizationId
            ));
        } else if (targetMembership.getRole() == OrganizationRole.ORG_SUBOWNER) {
            requireOwnerOrViceOwner(executor, organizationId);
            targetMembership.setRole(OrganizationRole.MEMBER);
            organizationMemberRepo.save(targetMembership);

            auditLogRepo.save(new AuditLog(
                executor,
                "ROLE_DEMOTED_MEMBER",
                "Usuario: " + target.getEmail() + " rebaixado a MEMBRO na Org ID: " + organizationId
            ));
        } else if (targetMembership.getRole() == OrganizationRole.MEMBER) {
            throw new IllegalStateException("O integrante ja esta no nivel base de Membro.");
        }

        return new OrganizationResponseDTO.MemberDTO(
            target.getId(),
            target.getName(),
            target.getEmail(),
            targetMembership.getRole().name()
        );
    }

    // 5. Revoke an active member

    @Transactional
    public void revokeAccess(Long memberId, Long organizationId, String executorEmail) {
        User executor = findUserByEmail(executorEmail);

        if (executor.getId().equals(memberId)) {
            throw new IllegalArgumentException("Voce nao pode revogar o seu proprio acesso por esta acao.");
        }

        Organization org = organizationRepo.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organizacao nao encontrada."));

        if (org.getOwner() != null && org.getOwner().getId().equals(memberId)) {
            throw new IllegalArgumentException("Nao e possivel remover o Dono da organizacao.");
        }

        OrganizationMember targetMembership = organizationMemberRepo
                .findByUserIdAndOrganizationId(memberId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Membro nao encontrado nesta organizacao."));

        OrganizationRole executorRole = null;
        if ("SYSADMIN".equalsIgnoreCase(executor.getRole())) {
            executorRole = OrganizationRole.ORG_OWNER;
        } else if (org.getOwner() != null && org.getOwner().getId().equals(executor.getId())) {
            executorRole = OrganizationRole.ORG_OWNER;
        } else {
            OrganizationMember executorMembership = organizationMemberRepo
                    .findByUserIdAndOrganizationId(executor.getId(), organizationId)
                    .orElseThrow(() -> new SecurityException("Acesso negado. Voce nao pertence a esta organizacao."));
            executorRole = executorMembership.getRole();
        }

        // Authorization rules by role:
        // - MEMBER: cannot remove anyone
        // - ORG_SUBOWNER: can only remove regular members
        // - ORG_VICE_OWNER: can remove MEMBER and ORG_SUBOWNER
        // - ORG_OWNER / SYSADMIN: can remove anyone (except themselves)
        if (executorRole == OrganizationRole.MEMBER) {
            throw new SecurityException("Acesso negado. Membros comuns nao podem remover integrantes da organizacao.");
        } else if (executorRole == OrganizationRole.ORG_SUBOWNER) {
            if (targetMembership.getRole() != OrganizationRole.MEMBER) {
                throw new SecurityException("Acesso negado. Subdonos so podem remover membros comuns da organizacao.");
            }
        } else if (executorRole == OrganizationRole.ORG_VICE_OWNER) {
            if (targetMembership.getRole() == OrganizationRole.ORG_OWNER || targetMembership.getRole() == OrganizationRole.ORG_VICE_OWNER) {
                throw new SecurityException("Acesso negado. Vice-Lideres nao podem remover o Dono nem outros Vice-Lideres.");
            }
        }

        // Remove the member's committee memberships in this organization
        List<CommitteeMember> memberCommittees = committeeMemberRepo
                .findByUserIdAndCommitteeOrganizationId(memberId, organizationId);
        if (!memberCommittees.isEmpty()) {
            committeeMemberRepo.deleteAll(memberCommittees);
        }

        // Remove previous access requests of this member in this organization
        List<AccessRequest> memberRequests = accessRequestRepo
                .findByUserIdAndOrganizationId(memberId, organizationId);
        if (!memberRequests.isEmpty()) {
            accessRequestRepo.deleteAll(memberRequests);
        }

        organizationMemberRepo.delete(targetMembership);

        auditLogRepo.save(new AuditLog(
            executor,
            "ACCESS_REVOKED",
            "Membro removido ID: " + memberId + " | Cargo: " + targetMembership.getRole() + " | OrgId: " + organizationId
        ));
    }

    // 6. Listings

    /**
     * Lists pending join requests of the organization (owner and vice owner only).
     */
    @Transactional(readOnly = true)
    public List<AccessRequestResponseDTO> listPendingRequests(Long organizationId, String executorEmail) {
        User executor = findUserByEmail(executorEmail);
        requireOwnerOrViceOwner(executor, organizationId);

        return accessRequestRepo
                .findByOrganizationIdAndStatus(organizationId, AccessRequestStatus.PENDING)
                .stream()
                .map(AccessRequestResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Lists all active members of the organization (visible to every member).
     */
    @Transactional(readOnly = true)
    public List<OrganizationResponseDTO.MemberDTO> listMembers(Long organizationId, String executorEmail) {
        User executor = findUserByEmail(executorEmail);
        requireOrganizationMember(executor, organizationId);

        return organizationMemberRepo.findByOrganizationId(organizationId)
                .stream()
                .map(m -> new OrganizationResponseDTO.MemberDTO(
                    m.getUser().getId(),
                    m.getUser().getName(),
                    m.getUser().getEmail(),
                    m.getRole().name()
                ))
                .collect(Collectors.toList());
    }
}
