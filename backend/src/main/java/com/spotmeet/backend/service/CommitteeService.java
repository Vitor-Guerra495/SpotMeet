package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.CommitteeMemberResponseDTO;
import com.spotmeet.backend.dto.CommitteeRequestDTO;
import com.spotmeet.backend.dto.CommitteeResponseDTO;
import com.spotmeet.backend.dto.UserCommitteeLinkDTO;
import com.spotmeet.backend.dto.ValidationPatterns;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.CommitteeMember.CommitteeMembershipStatus;
import com.spotmeet.backend.model.CommitteeMember.CommitteeRole;
import com.spotmeet.backend.model.OrganizationMember.OrganizationRole;
import com.spotmeet.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Committee management service.
 *
 * Least privilege:
 *   - Only leaders (ORG_OWNER / ORG_VICE_OWNER / ORG_SUBOWNER / SYSADMIN) can create and delete
 *     committees and add/remove members.
 *   - Adding by a leader creates an invitation pending the user's acceptance.
 *   - A direct request by the user creates a join request pending leader approval.
 *
 * LGPD: creation, removal, acceptance and refusal of memberships are recorded in AuditLog.
 */
@Service
public class CommitteeService {

    @Autowired private CommitteeRepository committeeRepo;
    @Autowired private CommitteeMemberRepository committeeMemberRepo;
    @Autowired private OrganizationRepository organizationRepo;
    @Autowired private OrganizationMemberRepository organizationMemberRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private AuditLogRepository auditLogRepo;

    // Authorization helpers

    private User findUserByEmail(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }

    /**
     * Requires the executor to be a leader (ORG_OWNER, ORG_VICE_OWNER or ORG_SUBOWNER) of the organization or SYSADMIN.
     */
    public void requireOrganizationLeader(User executor, Long organizationId) {
        if ("SYSADMIN".equalsIgnoreCase(executor.getRole())) {
            return;
        }

        Organization org = organizationRepo.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));

        if (org.getOwner() != null && org.getOwner().getId().equals(executor.getId())) {
            return;
        }

        organizationMemberRepo.findByUserIdAndOrganizationId(executor.getId(), organizationId)
                .filter(m -> m.getRole() == OrganizationRole.ORG_OWNER
                          || m.getRole() == OrganizationRole.ORG_VICE_OWNER
                          || m.getRole() == OrganizationRole.ORG_SUBOWNER)
                .orElseThrow(() -> new SecurityException(
                    "Acesso negado. Apenas líderes (dono, vice-líder ou subdono) da organização podem realizar esta operação."
                ));
    }

    // 1. Create committee (leader only)

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommitteeResponseDTO createCommittee(Long organizationId, String leaderEmail, CommitteeRequestDTO dto) {
        User leader = findUserByEmail(leaderEmail);
        Organization org = organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        requireOrganizationLeader(leader, organizationId);

        String cleanName = validateName(dto.getName());
        checkNameAvailability(organizationId, cleanName, null);

        if (!org.isApproved()) {
            throw new IllegalStateException("Esta organização ainda não foi aprovada pelo administrador do sistema ou está bloqueada.");
        }

        Committee committee = new Committee();
        committee.setName(cleanName);
        committee.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        committee.setOrganization(org);

        Committee saved = committeeRepo.save(committee);

        auditLogRepo.save(new AuditLog(
            leader,
            "COMMITTEE_CREATED",
            "Comissão: " + saved.getName() + " | Org: " + org.getName() + " | ID: " + saved.getId()
        ));

        return CommitteeResponseDTO.fromEntity(saved, 0, 0);
    }

    // 2. List committees of the organization

    @Transactional(readOnly = true)
    public List<CommitteeResponseDTO> listCommittees(Long organizationId, String userEmail) {
        User user = findUserByEmail(userEmail);

        // The user must belong to the organization or be SYSADMIN
        if (!"SYSADMIN".equalsIgnoreCase(user.getRole())
                && !organizationMemberRepo.existsByUserIdAndOrganizationId(user.getId(), organizationId)) {
            throw new SecurityException("Acesso negado. Você não é membro desta organização.");
        }

        List<Committee> committees = committeeRepo.findByOrganizationIdOrderByNameAsc(organizationId);

        return committees.stream().map(c -> {
            long totalActive = committeeMemberRepo.countByCommitteeIdAndStatus(c.getId(), CommitteeMembershipStatus.ACTIVE);
            long totalPending = committeeMemberRepo.countByCommitteeIdAndStatus(c.getId(), CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE)
                    + committeeMemberRepo.countByCommitteeIdAndStatus(c.getId(), CommitteeMembershipStatus.PENDING_LEADER_APPROVAL);
            String president = committeeMemberRepo.findByCommitteeIdAndRole(c.getId(), CommitteeRole.COMMITTEE_ADMIN).stream()
                    .filter(m -> m.getStatus() == CommitteeMembershipStatus.ACTIVE)
                    .map(m -> m.getUser().getName()).findFirst().orElse(null);
            return CommitteeResponseDTO.fromEntity(c, totalActive, totalPending).withPresident(president);
        }).collect(Collectors.toList());
    }

    // 3. Update committee (leader only)

    /**
     * Updates the name and description of a committee.
     *
     * Security:
     *   - Only organization leaders (or SYSADMIN) may edit, same rule as creation.
     *   - The organization is never changed; a committee cannot be moved.
     *   - The name stays unique within the organization (the committee itself is ignored in the check).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommitteeResponseDTO updateCommittee(Long committeeId, String leaderEmail, CommitteeRequestDTO dto) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissão não encontrada."));

        User leader = findUserByEmail(leaderEmail);
        organizationRepo.findByIdForUpdate(committee.getOrganization().getId())
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        requireOrganizationLeader(leader, committee.getOrganization().getId());

        String cleanName = validateName(dto.getName());
        Long organizationId = committee.getOrganization().getId();
        checkNameAvailability(organizationId, cleanName, committeeId);

        String previousName = committee.getName();
        committee.setName(cleanName);
        committee.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);

        Committee saved = committeeRepo.save(committee);

        auditLogRepo.save(new AuditLog(
            leader,
            "COMMITTEE_UPDATED",
            "Comissão ID: " + saved.getId() + " | Nome: " + previousName + " -> " + saved.getName()
                + " | Org: " + saved.getOrganization().getName()
        ));

        long totalActive = committeeMemberRepo.countByCommitteeIdAndStatus(saved.getId(), CommitteeMembershipStatus.ACTIVE);
        long totalPending = committeeMemberRepo.countByCommitteeIdAndStatus(saved.getId(), CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE)
                + committeeMemberRepo.countByCommitteeIdAndStatus(saved.getId(), CommitteeMembershipStatus.PENDING_LEADER_APPROVAL);
        return CommitteeResponseDTO.fromEntity(saved, totalActive, totalPending);
    }

    private String validateName(String name) {
        String normalized = ValidationPatterns.normalizeOrganizationName(name);
        if (normalized == null || normalized.length() < 2 || normalized.length() > 50
                || !normalized.matches(ValidationPatterns.COMMITTEE_NAME)) {
            throw new IllegalArgumentException("Use de 2 a 50 caracteres: letras, números, espaços, '_' ou '-'.");
        }
        return normalized;
    }

    private void checkNameAvailability(Long organizationId, String name, Long excludedId) {
        if (committeeRepo.existsByNormalizedName(organizationId, name, ValidationPatterns.ORGANIZATION_WHITESPACE, excludedId)) {
            throw new IllegalArgumentException("Já existe uma comissão com esse nome nesta organização.");
        }
    }

    // 4. Delete committee (leader only)

    @Transactional
    public void deleteCommittee(Long committeeId, String leaderEmail) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissão não encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOrganizationLeader(leader, committee.getOrganization().getId());

        List<CommitteeMember> members = committeeMemberRepo.findByCommitteeId(committeeId);
        committeeMemberRepo.deleteAll(members);

        committeeRepo.delete(committee);

        auditLogRepo.save(new AuditLog(
            leader,
            "COMMITTEE_DELETED",
            "Comissão ID: " + committeeId + " | Nome: " + committee.getName() + " | Org: " + committee.getOrganization().getName()
        ));
    }

    // 5. List committee links of a member in the organization (for the modal)

    @Transactional(readOnly = true)
    public List<UserCommitteeLinkDTO> listUserCommitteeLinks(Long organizationId, Long targetUserId, String executorEmail) {
        User executor = findUserByEmail(executorEmail);

        // Any member of the organization or SYSADMIN can view the committees of a member
        if (!"SYSADMIN".equalsIgnoreCase(executor.getRole())
                && !organizationMemberRepo.existsByUserIdAndOrganizationId(executor.getId(), organizationId)) {
            throw new SecurityException("Acesso negado. Você não pertence a esta organização.");
        }

        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        if (!organizationMemberRepo.existsByUserIdAndOrganizationId(target.getId(), organizationId)) {
            throw new IllegalArgumentException("O usuário informado não pertence a esta organização.");
        }

        List<Committee> committees = committeeRepo.findByOrganizationIdOrderByNameAsc(organizationId);
        List<UserCommitteeLinkDTO> result = new ArrayList<>();

        for (Committee c : committees) {
            Optional<CommitteeMember> cmOpt = committeeMemberRepo.findByUserIdAndCommitteeId(target.getId(), c.getId());
            if (cmOpt.isPresent()) {
                CommitteeMember cm = cmOpt.get();
                result.add(new UserCommitteeLinkDTO(
                    c.getId(),
                    c.getName(),
                    c.getDescription(),
                    true,
                    cm.getStatus(),
                    cm.getRole(),
                    cm.getId()
                ));
            } else {
                result.add(new UserCommitteeLinkDTO(
                    c.getId(),
                    c.getName(),
                    c.getDescription(),
                    false,
                    null,
                    null,
                    null
                ));
            }
        }

        return result;
    }

    // 6. Leader adds a member to the committee (creates a pending invitation for the user)

    @Transactional
    public CommitteeMemberResponseDTO addUserToCommittee(Long committeeId, Long targetUserId, String leaderEmail) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissão não encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOrganizationLeader(leader, committee.getOrganization().getId());

        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));

        // The user must be an active member of the organization
        if (!organizationMemberRepo.existsByUserIdAndOrganizationId(target.getId(), committee.getOrganization().getId())) {
            throw new IllegalStateException("O usuário deve ser integrante ativo da organização para ser vinculado a uma comissão.");
        }

        Optional<CommitteeMember> existingOpt = committeeMemberRepo.findByUserIdAndCommitteeId(target.getId(), committeeId);

        CommitteeMember cm;
        if (existingOpt.isPresent()) {
            cm = existingOpt.get();
            if (cm.getStatus() == CommitteeMembershipStatus.ACTIVE) {
                throw new IllegalStateException("O usuário já é integrante ativo desta comissão.");
            }
            if (cm.getStatus() == CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE) {
                throw new IllegalStateException("Já existe um convite pendente aguardando aceite deste usuário.");
            }
            if (cm.getStatus() == CommitteeMembershipStatus.PENDING_LEADER_APPROVAL) {
                // The user had requested and the leader now adds: approve directly
                cm.setStatus(CommitteeMembershipStatus.ACTIVE);
                cm.setRespondedAt(LocalDateTime.now());
            } else {
                // Was DECLINED: reopen as a pending invitation
                cm.setStatus(CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE);
                cm.setInvitedBy(leader);
                cm.setRequestedAt(LocalDateTime.now());
                cm.setRespondedAt(null);
            }
        } else {
            cm = new CommitteeMember();
            cm.setUser(target);
            cm.setCommittee(committee);
            cm.setStatus(CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE);
            cm.setRole(CommitteeRole.MEMBER);
            cm.setInvitedBy(leader);
        }

        CommitteeMember saved = committeeMemberRepo.save(cm);

        auditLogRepo.save(new AuditLog(
            leader,
            "COMMITTEE_INVITATION_SENT",
            "Usuário: " + target.getEmail() + " convidado para comissão: " + committee.getName()
        ));

        return CommitteeMemberResponseDTO.fromEntity(saved);
    }

    // 7. Leader removes a member from the committee

    @Transactional
    public void removeUserFromCommittee(Long committeeId, Long targetUserId, String leaderEmail) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissão não encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOrganizationLeader(leader, committee.getOrganization().getId());

        CommitteeMember cm = committeeMemberRepo.findByUserIdAndCommitteeId(targetUserId, committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Vínculo do usuário com esta comissão não encontrado."));

        committeeMemberRepo.delete(cm);

        auditLogRepo.save(new AuditLog(
            leader,
            "COMMITTEE_MEMBER_REMOVED",
            "Usuário ID: " + targetUserId + " removido da comissão: " + committee.getName()
        ));
    }

    // 7.1 Active members of the committee, with the president marked by the role

    @Transactional(readOnly = true)
    public List<CommitteeMemberResponseDTO> listActiveMembers(Long committeeId, String userEmail) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissão não encontrada."));
        User user = findUserByEmail(userEmail);

        boolean member = committeeMemberRepo.existsByUserIdAndCommitteeIdAndStatus(
                user.getId(), committeeId, CommitteeMembershipStatus.ACTIVE);
        if (!member) {
            requireOrganizationLeader(user, committee.getOrganization().getId());
        }
        return committeeMemberRepo.findByCommitteeIdAndStatus(committeeId, CommitteeMembershipStatus.ACTIVE)
                .stream()
                .map(CommitteeMemberResponseDTO::fromEntity)
                .toList();
    }

    // 7.3 Committees where the user is the active president

    @Transactional(readOnly = true)
    public List<CommitteeMemberResponseDTO> listPresidedCommittees(String userEmail) {
        User user = findUserByEmail(userEmail);
        return committeeMemberRepo.findByUserIdAndStatus(user.getId(), CommitteeMembershipStatus.ACTIVE)
                .stream()
                .filter(m -> m.getRole() == CommitteeRole.COMMITTEE_ADMIN)
                .map(CommitteeMemberResponseDTO::fromEntity)
                .toList();
    }

    // 7.2 Leader designates the committee president (one per committee)

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CommitteeMemberResponseDTO assignPresident(Long committeeId, Long targetUserId, String leaderEmail) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissão não encontrada."));

        User leader = findUserByEmail(leaderEmail);
        organizationRepo.findByIdForUpdate(committee.getOrganization().getId())
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        requireOrganizationLeader(leader, committee.getOrganization().getId());

        CommitteeMember target = committeeMemberRepo.findByUserIdAndCommitteeId(targetUserId, committeeId)
                .filter(m -> m.getStatus() == CommitteeMembershipStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("O presidente deve ser integrante ativo da comissão."));

        if (target.getRole() == CommitteeRole.COMMITTEE_ADMIN) {
            return CommitteeMemberResponseDTO.fromEntity(target);
        }
        for (CommitteeMember current : committeeMemberRepo.findByCommitteeIdAndRole(committeeId, CommitteeRole.COMMITTEE_ADMIN)) {
            current.setRole(CommitteeRole.MEMBER);
            committeeMemberRepo.save(current);
        }
        target.setRole(CommitteeRole.COMMITTEE_ADMIN);
        CommitteeMember saved = committeeMemberRepo.save(target);

        auditLogRepo.save(new AuditLog(
            leader,
            "COMMITTEE_PRESIDENT_ASSIGNED",
            "Usuário ID: " + targetUserId + " | Comissão: " + committee.getName()
        ));

        return CommitteeMemberResponseDTO.fromEntity(saved);
    }

    // 8. Pending invitations of the user (accept/decline)

    @Transactional(readOnly = true)
    public List<CommitteeMemberResponseDTO> listMyInvitations(String userEmail) {
        User user = findUserByEmail(userEmail);

        return committeeMemberRepo.findByUserIdAndStatus(user.getId(), CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE)
                .stream()
                .map(CommitteeMemberResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommitteeMemberResponseDTO respondInvitation(Long committeeId, String userEmail, boolean accept) {
        User user = findUserByEmail(userEmail);

        CommitteeMember cm = committeeMemberRepo.findByUserIdAndCommitteeId(user.getId(), committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Nenhum convite encontrado para esta comissão."));

        if (cm.getStatus() != CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE) {
            throw new IllegalStateException("Apenas convites pendentes podem ser respondidos.");
        }

        if (accept) {
            cm.setStatus(CommitteeMembershipStatus.ACTIVE);
            cm.setRespondedAt(LocalDateTime.now());
            auditLogRepo.save(new AuditLog(
                user,
                "COMMITTEE_INVITATION_ACCEPTED",
                "Comissão: " + cm.getCommittee().getName() + " | Org: " + cm.getCommittee().getOrganization().getName()
            ));
        } else {
            cm.setStatus(CommitteeMembershipStatus.DECLINED);
            cm.setRespondedAt(LocalDateTime.now());
            auditLogRepo.save(new AuditLog(
                user,
                "COMMITTEE_INVITATION_DECLINED",
                "Comissão: " + cm.getCommittee().getName() + " | Org: " + cm.getCommittee().getOrganization().getName()
            ));
        }

        CommitteeMember saved = committeeMemberRepo.save(cm);
        return CommitteeMemberResponseDTO.fromEntity(saved);
    }

    // 9. Join requests by the user and leader approval

    @Transactional
    public CommitteeMemberResponseDTO requestToJoin(Long committeeId, String userEmail) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissão não encontrada."));

        User user = findUserByEmail(userEmail);

        if (!organizationMemberRepo.existsByUserIdAndOrganizationId(user.getId(), committee.getOrganization().getId())) {
            throw new IllegalStateException("Você deve ser membro da organização para solicitar entrada em suas comissões.");
        }

        Optional<CommitteeMember> cmOpt = committeeMemberRepo.findByUserIdAndCommitteeId(user.getId(), committeeId);
        if (cmOpt.isPresent()) {
            CommitteeMember existing = cmOpt.get();
            if (existing.getStatus() == CommitteeMembershipStatus.ACTIVE) {
                throw new IllegalStateException("Você já é integrante desta comissão.");
            }
            if (existing.getStatus() == CommitteeMembershipStatus.PENDING_LEADER_APPROVAL) {
                throw new IllegalStateException("Você já possui uma solicitação pendente de aprovação para esta comissão.");
            }
            if (existing.getStatus() == CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE) {
                // The leader had already invited: accept directly
                existing.setStatus(CommitteeMembershipStatus.ACTIVE);
                existing.setRespondedAt(LocalDateTime.now());
                CommitteeMember saved = committeeMemberRepo.save(existing);
                return CommitteeMemberResponseDTO.fromEntity(saved);
            }
            // Was DECLINED: reopen the request
            existing.setStatus(CommitteeMembershipStatus.PENDING_LEADER_APPROVAL);
            existing.setRequestedAt(LocalDateTime.now());
            existing.setRespondedAt(null);
            CommitteeMember saved = committeeMemberRepo.save(existing);
            return CommitteeMemberResponseDTO.fromEntity(saved);
        }

        CommitteeMember newMember = new CommitteeMember();
        newMember.setUser(user);
        newMember.setCommittee(committee);
        newMember.setStatus(CommitteeMembershipStatus.PENDING_LEADER_APPROVAL);
        newMember.setRole(CommitteeRole.MEMBER);

        CommitteeMember saved = committeeMemberRepo.save(newMember);

        auditLogRepo.save(new AuditLog(
            user,
            "COMMITTEE_JOIN_REQUESTED",
            "Comissão: " + committee.getName() + " | Org: " + committee.getOrganization().getName()
        ));

        return CommitteeMemberResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<CommitteeMemberResponseDTO> listPendingJoinRequests(Long committeeId, String leaderEmail) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissão não encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOrganizationLeader(leader, committee.getOrganization().getId());

        return committeeMemberRepo.findByCommitteeIdAndStatus(committeeId, CommitteeMembershipStatus.PENDING_LEADER_APPROVAL)
                .stream()
                .map(CommitteeMemberResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommitteeMemberResponseDTO respondJoinRequest(Long membershipId, String leaderEmail, boolean approve) {
        CommitteeMember cm = committeeMemberRepo.findById(membershipId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitação de comissão não encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOrganizationLeader(leader, cm.getCommittee().getOrganization().getId());

        if (cm.getStatus() != CommitteeMembershipStatus.PENDING_LEADER_APPROVAL) {
            throw new IllegalStateException("Apenas solicitações pendentes de aprovação do líder podem ser respondidas.");
        }

        if (approve) {
            cm.setStatus(CommitteeMembershipStatus.ACTIVE);
            cm.setRespondedAt(LocalDateTime.now());
            auditLogRepo.save(new AuditLog(
                leader,
                "COMMITTEE_JOIN_APPROVED",
                "Usuário: " + cm.getUser().getEmail() + " | Comissão: " + cm.getCommittee().getName()
            ));
        } else {
            cm.setStatus(CommitteeMembershipStatus.DECLINED);
            cm.setRespondedAt(LocalDateTime.now());
            auditLogRepo.save(new AuditLog(
                leader,
                "COMMITTEE_JOIN_REJECTED",
                "Usuário: " + cm.getUser().getEmail() + " | Comissão: " + cm.getCommittee().getName()
            ));
        }

        CommitteeMember saved = committeeMemberRepo.save(cm);
        return CommitteeMemberResponseDTO.fromEntity(saved);
    }

    /**
     * Lists every committee join request pending leader approval in the organization.
     * Visible to owner, vice owner and subowner.
     */
    @Transactional(readOnly = true)
    public List<CommitteeMemberResponseDTO> listPendingJoinRequestsForOrganization(Long organizationId, String executorEmail) {
        User executor = findUserByEmail(executorEmail);
        requireOrganizationLeader(executor, organizationId);

        return committeeMemberRepo.findByCommitteeOrganizationIdAndStatus(organizationId, CommitteeMembershipStatus.PENDING_LEADER_APPROVAL)
                .stream()
                .map(CommitteeMemberResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
