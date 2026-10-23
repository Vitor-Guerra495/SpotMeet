package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.AccessRequestResponseDTO;
import com.spotmeet.backend.dto.OrganizationResponseDTO;
import com.spotmeet.backend.dto.OrganizationLeadershipResponseDTO;
import com.spotmeet.backend.dto.OrganizationPreviewDTO;
import com.spotmeet.backend.dto.ValidationPatterns;
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
    @Autowired private OrganizationInvitationRepository invitationRepo;
    @Autowired private CommitteeMemberRepository committeeMemberRepo;
    @Autowired private AuditLogRepository auditLogRepo;

    // Authorization helpers

    private User findUserByEmail(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }

    /**
     * Requires the executor to be an active member of the organization or SYSADMIN.
     */
    public void requireOrganizationMember(User executor, Long organizationId) {
        if ("SYSADMIN".equalsIgnoreCase(executor.getRole())) {
            return;
        }

        Organization org = organizationRepo.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));

        if (org.getOwner() != null && org.getOwner().getId().equals(executor.getId())) {
            return;
        }

        if (!organizationMemberRepo.existsByUserIdAndOrganizationId(executor.getId(), organizationId)) {
            throw new SecurityException("Acesso negado. Você não é membro desta organização.");
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
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));

        if (org.getOwner() != null && org.getOwner().getId().equals(executor.getId())) {
            return;
        }

        organizationMemberRepo.findByUserIdAndOrganizationId(executor.getId(), organizationId)
                .filter(m -> m.getRole() == OrganizationRole.ORG_OWNER || m.getRole() == OrganizationRole.ORG_VICE_OWNER)
                .orElseThrow(() -> new SecurityException(
                    "Acesso negado. Apenas o Dono ou Vice-Líder da organização pode realizar esta operação."
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
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));

        if (org.getOwner() != null && org.getOwner().getId().equals(executor.getId())) {
            return;
        }

        organizationMemberRepo.findByUserIdAndOrganizationId(executor.getId(), organizationId)
                .filter(m -> m.getRole() == OrganizationRole.ORG_OWNER)
                .orElseThrow(() -> new SecurityException(
                    "Acesso negado. Operação restrita exclusivamente ao Dono da organização."
                ));
    }

    // 1. Request access by key (any authenticated user)

    /** Finds the approved organization behind an access key; the same checks serve the preview and the request. */
    private Organization findApprovedByKey(String accessKey, boolean forUpdate) {
        String cleanKey = ValidationPatterns.normalizeOrganizationValue(accessKey);
        if (cleanKey == null || cleanKey.isEmpty()) {
            throw new IllegalArgumentException("Chave da organização é obrigatória.");
        }

        List<Long> matches = organizationRepo.findIdsByAccessKeyIgnoreCase(cleanKey, ValidationPatterns.ORGANIZATION_WHITESPACE);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Organização não encontrada. Verifique a chave e tente novamente.");
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Esta chave corresponde a mais de uma organização. Peça ao responsável para atualizar a chave.");
        }
        Organization org = (forUpdate ? organizationRepo.findByIdForUpdate(matches.get(0)) : organizationRepo.findById(matches.get(0)))
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        if (!cleanKey.equalsIgnoreCase(ValidationPatterns.normalizeOrganizationValue(org.getAccessKey()))) {
            throw new IllegalArgumentException("Organização não encontrada. Verifique a chave e tente novamente.");
        }

        if (!org.isApproved()) {
            throw new IllegalStateException("Esta organização ainda não foi aprovada pelo administrador do sistema ou está bloqueada.");
        }
        return org;
    }

    /** Name and responsible of the organization behind a key, shown before the user asks to join. */
    @Transactional(readOnly = true)
    public OrganizationPreviewDTO previewByKey(String accessKey) {
        Organization org = findApprovedByKey(accessKey, false);
        String owner = org.getOwner() != null ? org.getOwner().getName() : "Não informado";
        return new OrganizationPreviewDTO(org.getName(), org.getAccessKey(), owner);
    }

    @Transactional
    public AccessRequestResponseDTO requestAccess(String requesterEmail, String accessKey) {
        Organization org = findApprovedByKey(accessKey, true);

        User requester = findUserByEmail(requesterEmail);

        if (org.getOwner() != null && org.getOwner().getId().equals(requester.getId())) {
            throw new IllegalStateException("Você é o proprietário desta organização.");
        }

        if (organizationMemberRepo.existsByUserIdAndOrganizationId(requester.getId(), org.getId())) {
            throw new IllegalStateException("Você já é membro desta organização.");
        }

        List<AccessRequest> existing = accessRequestRepo.findByUserIdAndOrganizationId(requester.getId(), org.getId());
        AccessRequest request;
        if (!existing.isEmpty()) {
            request = existing.get(0);
            if (request.getStatus() == AccessRequestStatus.PENDING) {
                throw new IllegalStateException("Você já possui uma solicitação pendente para esta organização.");
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
        Long organizationId = accessRequestRepo.findOrganizationIdById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada."));
        organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        AccessRequest request = accessRequestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOwnerOrViceOwner(leader, request.getOrganization().getId());

        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new IllegalStateException(
                "Apenas solicitações PENDENTES podem ser aprovadas. Status atual: " + request.getStatus()
            );
        }

        if (!request.getOrganization().isApproved()) {
            throw new IllegalStateException("Esta organização ainda não foi aprovada pelo administrador do sistema ou está bloqueada.");
        }

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
            "Solicitação #" + requestId + " | Usuário: " + request.getUser().getEmail()
                + " | Org: " + request.getOrganization().getName()
        ));

        return AccessRequestResponseDTO.fromEntity(request);
    }

    // 3. Reject request (owner or vice owner)

    @Transactional
    public AccessRequestResponseDTO rejectAccess(Long requestId, String leaderEmail) {
        Long organizationId = accessRequestRepo.findOrganizationIdById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada."));
        organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        AccessRequest request = accessRequestRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOwnerOrViceOwner(leader, request.getOrganization().getId());

        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new IllegalStateException(
                "Apenas solicitações PENDENTES podem ser rejeitadas. Status atual: " + request.getStatus()
            );
        }

        request.setStatus(AccessRequestStatus.REJECTED);
        accessRequestRepo.save(request);

        auditLogRepo.save(new AuditLog(
            leader,
            "ACCESS_REJECTED",
            "Solicitação #" + requestId + " | Usuário: " + request.getUser().getEmail()
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
        organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário informado não encontrado."));

        OrganizationMember targetMembership = organizationMemberRepo
                .findByUserIdAndOrganizationId(target.getId(), organizationId)
                .orElseThrow(() -> new IllegalArgumentException("O usuário informado não pertence a esta organização."));

        if (targetMembership.getRole() == OrganizationRole.ORG_OWNER) {
            throw new IllegalStateException("Não é possível alterar o cargo do Dono da organização.");
        }

        if (targetMembership.getRole() == OrganizationRole.MEMBER) {
            // Only owners and vice owners can promote to subowner
            requireOwnerOrViceOwner(executor, organizationId);
            targetMembership.setRole(OrganizationRole.ORG_SUBOWNER);
            organizationMemberRepo.save(targetMembership);

            auditLogRepo.save(new AuditLog(
                executor,
                "ROLE_PROMOTED_SUBOWNER",
                "Usuário: " + target.getEmail() + " elevado a SUBDONO na Org ID: " + organizationId
            ));
        } else if (targetMembership.getRole() == OrganizationRole.ORG_SUBOWNER) {
            // Owner only
            requireOwner(executor, organizationId);
            targetMembership.setRole(OrganizationRole.ORG_VICE_OWNER);
            organizationMemberRepo.save(targetMembership);

            auditLogRepo.save(new AuditLog(
                executor,
                "ROLE_PROMOTED_VICE_OWNER",
                "Usuário: " + target.getEmail() + " elevado a VICE-LÍDER na Org ID: " + organizationId
            ));
        } else if (targetMembership.getRole() == OrganizationRole.ORG_VICE_OWNER) {
            throw new IllegalStateException("O integrante já possui o cargo máximo de Vice-Líder.");
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
        organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário informado não encontrado."));

        OrganizationMember targetMembership = organizationMemberRepo
                .findByUserIdAndOrganizationId(target.getId(), organizationId)
                .orElseThrow(() -> new IllegalArgumentException("O usuário informado não pertence a esta organização."));

        if (targetMembership.getRole() == OrganizationRole.ORG_OWNER) {
            throw new IllegalStateException("Não é possível alterar o cargo do Dono da organização.");
        }

        if (targetMembership.getRole() == OrganizationRole.ORG_VICE_OWNER) {
            requireOwner(executor, organizationId);
            targetMembership.setRole(OrganizationRole.ORG_SUBOWNER);
            organizationMemberRepo.save(targetMembership);

            auditLogRepo.save(new AuditLog(
                executor,
                "ROLE_DEMOTED_SUBOWNER",
                "Usuário: " + target.getEmail() + " rebaixado a SUBDONO na Org ID: " + organizationId
            ));
        } else if (targetMembership.getRole() == OrganizationRole.ORG_SUBOWNER) {
            requireOwnerOrViceOwner(executor, organizationId);
            targetMembership.setRole(OrganizationRole.MEMBER);
            organizationMemberRepo.save(targetMembership);

            auditLogRepo.save(new AuditLog(
                executor,
                "ROLE_DEMOTED_MEMBER",
                "Usuário: " + target.getEmail() + " rebaixado a MEMBRO na Org ID: " + organizationId
            ));
        } else if (targetMembership.getRole() == OrganizationRole.MEMBER) {
            throw new IllegalStateException("O integrante já está no nível base de Membro.");
        }

        return new OrganizationResponseDTO.MemberDTO(
            target.getId(),
            target.getName(),
            target.getEmail(),
            targetMembership.getRole().name()
        );
    }

    @Transactional
    public OrganizationLeadershipResponseDTO transferLeadership(Long organizationId, Long targetUserId, String executorEmail) {
        User executor = userRepo.findByEmail(executorEmail)
                .orElseThrow(() -> new SecurityException("Usuário autenticado não encontrado."));
        Organization organization = organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        User previousOwner = organization.getOwner();
        if (!"SYSADMIN".equalsIgnoreCase(executor.getRole())
                && (previousOwner == null || !previousOwner.getId().equals(executor.getId()))) {
            throw new SecurityException("Acesso negado. Somente o dono ou o SysAdmin pode transferir a liderança.");
        }
        if (!organization.isApproved()) {
            throw new IllegalStateException("A liderança só pode ser transferida depois que a organização for aprovada.");
        }

        List<OrganizationMember> owners = organizationMemberRepo
                .findByOrganizationIdAndRole(organizationId, OrganizationRole.ORG_OWNER);
        if (previousOwner == null || owners.size() != 1 || !owners.get(0).getUser().getId().equals(previousOwner.getId())) {
            throw new IllegalStateException("A liderança atual está inconsistente. A transferência não foi realizada.");
        }
        if (previousOwner.getId().equals(targetUserId)) {
            throw new IllegalStateException("O integrante informado já é o dono da organização.");
        }
        OrganizationMember target = organizationMemberRepo.findByUserIdAndOrganizationId(targetUserId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("O integrante informado não pertence a esta organização."));
        if (target.getRole() == null || target.getRole() == OrganizationRole.ORG_OWNER) {
            throw new IllegalStateException("O cargo do integrante está inconsistente. A transferência não foi realizada.");
        }

        OrganizationMember previousMembership = owners.get(0);
        previousMembership.setRole(OrganizationRole.MEMBER);
        target.setRole(OrganizationRole.ORG_OWNER);
        organization.setOwner(target.getUser());
        organizationMemberRepo.save(previousMembership);
        organizationMemberRepo.save(target);
        organizationRepo.save(organization);
        auditLogRepo.save(new AuditLog(executor, "ORGANIZATION_LEADER_CHANGED",
                "Organização ID: " + organizationId + " | Dono anterior ID: " + previousOwner.getId()
                        + " | Novo dono ID: " + targetUserId));
        return new OrganizationLeadershipResponseDTO(organizationId, previousOwner.getId(), targetUserId,
                previousMembership.getRole().name(), target.getRole().name());
    }

    // 5. Revoke an active member

    @Transactional
    public void revokeAccess(Long memberId, Long organizationId, String executorEmail) {
        User executor = findUserByEmail(executorEmail);

        if (executor.getId().equals(memberId)) {
            throw new IllegalArgumentException("Você não pode revogar o seu próprio acesso por esta ação.");
        }

        Organization org = organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));

        if (org.getOwner() != null && org.getOwner().getId().equals(memberId)) {
            throw new IllegalArgumentException("Não é possível remover o Dono da organização.");
        }

        OrganizationMember targetMembership = organizationMemberRepo
                .findByUserIdAndOrganizationId(memberId, organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Membro não encontrado nesta organização."));

        OrganizationRole executorRole = null;
        if ("SYSADMIN".equalsIgnoreCase(executor.getRole())) {
            executorRole = OrganizationRole.ORG_OWNER;
        } else if (org.getOwner() != null && org.getOwner().getId().equals(executor.getId())) {
            executorRole = OrganizationRole.ORG_OWNER;
        } else {
            OrganizationMember executorMembership = organizationMemberRepo
                    .findByUserIdAndOrganizationId(executor.getId(), organizationId)
                    .orElseThrow(() -> new SecurityException("Acesso negado. Você não pertence a esta organização."));
            executorRole = executorMembership.getRole();
        }

        // Authorization rules by role:
        // - MEMBER: cannot remove anyone
        // - ORG_SUBOWNER: can only remove regular members
        // - ORG_VICE_OWNER: can remove MEMBER and ORG_SUBOWNER
        // - ORG_OWNER / SYSADMIN: can remove anyone (except themselves)
        if (executorRole == OrganizationRole.MEMBER) {
            throw new SecurityException("Acesso negado. Membros comuns não podem remover integrantes da organização.");
        } else if (executorRole == OrganizationRole.ORG_SUBOWNER) {
            if (targetMembership.getRole() != OrganizationRole.MEMBER) {
                throw new SecurityException("Acesso negado. Subdonos só podem remover membros comuns da organização.");
            }
        } else if (executorRole == OrganizationRole.ORG_VICE_OWNER) {
            if (targetMembership.getRole() == OrganizationRole.ORG_OWNER || targetMembership.getRole() == OrganizationRole.ORG_VICE_OWNER) {
                throw new SecurityException("Acesso negado. Vice-Líderes não podem remover o Dono nem outros Vice-Líderes.");
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

        // A removed member must not regain access through an earlier invitation.
        invitationRepo.deleteByOrganizationIdAndUserId(organizationId, memberId);
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
