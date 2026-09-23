package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.CommitteeMemberResponseDTO;
import com.spotmeet.backend.dto.CommitteeRequestDTO;
import com.spotmeet.backend.dto.CommitteeResponseDTO;
import com.spotmeet.backend.dto.UserCommitteeLinkDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.CommitteeMember.CommitteeMembershipStatus;
import com.spotmeet.backend.model.CommitteeMember.CommitteeRole;
import com.spotmeet.backend.model.OrganizationMember.OrganizationRole;
import com.spotmeet.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado nao encontrado."));
    }

    /**
     * Requires the executor to be a leader (ORG_OWNER, ORG_VICE_OWNER or ORG_SUBOWNER) of the organization or SYSADMIN.
     */
    public void requireOrganizationLeader(User executor, Long organizationId) {
        if ("SYSADMIN".equalsIgnoreCase(executor.getRole())) {
            return;
        }

        Organization org = organizationRepo.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organizacao nao encontrada."));

        if (org.getOwner() != null && org.getOwner().getId().equals(executor.getId())) {
            return;
        }

        organizationMemberRepo.findByUserIdAndOrganizationId(executor.getId(), organizationId)
                .filter(m -> m.getRole() == OrganizationRole.ORG_OWNER
                          || m.getRole() == OrganizationRole.ORG_VICE_OWNER
                          || m.getRole() == OrganizationRole.ORG_SUBOWNER)
                .orElseThrow(() -> new SecurityException(
                    "Acesso negado. Apenas lideres (dono, vice-lider ou subdono) da organizacao podem realizar esta operacao."
                ));
    }

    // 1. Create committee (leader only)

    @Transactional
    public CommitteeResponseDTO createCommittee(Long organizationId, String leaderEmail, CommitteeRequestDTO dto) {
        User leader = findUserByEmail(leaderEmail);
        requireOrganizationLeader(leader, organizationId);

        String cleanName = dto.getName() != null ? dto.getName().trim() : "";
        if (cleanName.contains(" ")) {
            throw new IllegalArgumentException("O nome da comissao nao pode conter espacos.");
        }

        if (committeeRepo.existsByOrganizationIdAndName(organizationId, cleanName)) {
            throw new IllegalArgumentException("Ja existe uma comissao com o nome '" + cleanName + "' nesta organizacao.");
        }

        Organization org = organizationRepo.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organizacao nao encontrada."));

        if (!org.isApproved()) {
            throw new IllegalStateException("Esta organizacao ainda nao foi aprovada pelo administrador do sistema ou esta bloqueada.");
        }

        Committee committee = new Committee();
        committee.setName(cleanName);
        committee.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);
        committee.setOrganization(org);

        Committee saved = committeeRepo.save(committee);

        auditLogRepo.save(new AuditLog(
            leader,
            "COMMITTEE_CREATED",
            "Comissao: " + saved.getName() + " | Org: " + org.getName() + " | ID: " + saved.getId()
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
            throw new SecurityException("Acesso negado. Voce nao e membro desta organizacao.");
        }

        List<Committee> committees = committeeRepo.findByOrganizationIdOrderByNameAsc(organizationId);

        return committees.stream().map(c -> {
            long totalActive = committeeMemberRepo.countByCommitteeIdAndStatus(c.getId(), CommitteeMembershipStatus.ACTIVE);
            long totalPending = committeeMemberRepo.countByCommitteeIdAndStatus(c.getId(), CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE)
                    + committeeMemberRepo.countByCommitteeIdAndStatus(c.getId(), CommitteeMembershipStatus.PENDING_LEADER_APPROVAL);
            return CommitteeResponseDTO.fromEntity(c, totalActive, totalPending);
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
    @Transactional
    public CommitteeResponseDTO updateCommittee(Long committeeId, String leaderEmail, CommitteeRequestDTO dto) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissao nao encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOrganizationLeader(leader, committee.getOrganization().getId());

        String cleanName = dto.getName() != null ? dto.getName().trim() : "";
        if (cleanName.contains(" ")) {
            throw new IllegalArgumentException("O nome da comissao nao pode conter espacos.");
        }

        Long organizationId = committee.getOrganization().getId();
        boolean nameTakenByAnother = committeeRepo.findByOrganizationIdAndName(organizationId, cleanName)
                .filter(other -> !other.getId().equals(committeeId))
                .isPresent();
        if (nameTakenByAnother) {
            throw new IllegalArgumentException("Ja existe uma comissao com o nome '" + cleanName + "' nesta organizacao.");
        }

        String previousName = committee.getName();
        committee.setName(cleanName);
        committee.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : null);

        Committee saved = committeeRepo.save(committee);

        auditLogRepo.save(new AuditLog(
            leader,
            "COMMITTEE_UPDATED",
            "Comissao ID: " + saved.getId() + " | Nome: " + previousName + " -> " + saved.getName()
                + " | Org: " + saved.getOrganization().getName()
        ));

        long totalActive = committeeMemberRepo.countByCommitteeIdAndStatus(saved.getId(), CommitteeMembershipStatus.ACTIVE);
        long totalPending = committeeMemberRepo.countByCommitteeIdAndStatus(saved.getId(), CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE)
                + committeeMemberRepo.countByCommitteeIdAndStatus(saved.getId(), CommitteeMembershipStatus.PENDING_LEADER_APPROVAL);
        return CommitteeResponseDTO.fromEntity(saved, totalActive, totalPending);
    }

    // 4. Delete committee (leader only)

    @Transactional
    public void deleteCommittee(Long committeeId, String leaderEmail) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissao nao encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOrganizationLeader(leader, committee.getOrganization().getId());

        List<CommitteeMember> members = committeeMemberRepo.findByCommitteeId(committeeId);
        committeeMemberRepo.deleteAll(members);

        committeeRepo.delete(committee);

        auditLogRepo.save(new AuditLog(
            leader,
            "COMMITTEE_DELETED",
            "Comissao ID: " + committeeId + " | Nome: " + committee.getName() + " | Org: " + committee.getOrganization().getName()
        ));
    }

    // 5. List committee links of a member in the organization (for the modal)

    @Transactional(readOnly = true)
    public List<UserCommitteeLinkDTO> listUserCommitteeLinks(Long organizationId, Long targetUserId, String executorEmail) {
        User executor = findUserByEmail(executorEmail);

        // Any member of the organization or SYSADMIN can view the committees of a member
        if (!"SYSADMIN".equalsIgnoreCase(executor.getRole())
                && !organizationMemberRepo.existsByUserIdAndOrganizationId(executor.getId(), organizationId)) {
            throw new SecurityException("Acesso negado. Voce nao pertence a esta organizacao.");
        }

        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado."));

        if (!organizationMemberRepo.existsByUserIdAndOrganizationId(target.getId(), organizationId)) {
            throw new IllegalArgumentException("O usuario informado nao pertence a esta organizacao.");
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
                .orElseThrow(() -> new IllegalArgumentException("Comissao nao encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOrganizationLeader(leader, committee.getOrganization().getId());

        User target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado."));

        // The user must be an active member of the organization
        if (!organizationMemberRepo.existsByUserIdAndOrganizationId(target.getId(), committee.getOrganization().getId())) {
            throw new IllegalStateException("O usuario deve ser integrante ativo da organizacao para ser vinculado a uma comissao.");
        }

        Optional<CommitteeMember> existingOpt = committeeMemberRepo.findByUserIdAndCommitteeId(target.getId(), committeeId);

        CommitteeMember cm;
        if (existingOpt.isPresent()) {
            cm = existingOpt.get();
            if (cm.getStatus() == CommitteeMembershipStatus.ACTIVE) {
                throw new IllegalStateException("O usuario ja e integrante ativo desta comissao.");
            }
            if (cm.getStatus() == CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE) {
                throw new IllegalStateException("Ja existe um convite pendente aguardando aceite deste usuario.");
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
            "Usuario: " + target.getEmail() + " convidado para comissao: " + committee.getName()
        ));

        return CommitteeMemberResponseDTO.fromEntity(saved);
    }

    // 7. Leader removes a member from the committee

    @Transactional
    public void removeUserFromCommittee(Long committeeId, Long targetUserId, String leaderEmail) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissao nao encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOrganizationLeader(leader, committee.getOrganization().getId());

        CommitteeMember cm = committeeMemberRepo.findByUserIdAndCommitteeId(targetUserId, committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Vinculo do usuario com esta comissao nao encontrado."));

        committeeMemberRepo.delete(cm);

        auditLogRepo.save(new AuditLog(
            leader,
            "COMMITTEE_MEMBER_REMOVED",
            "Usuario ID: " + targetUserId + " removido da comissao: " + committee.getName()
        ));
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
                .orElseThrow(() -> new IllegalArgumentException("Nenhum convite encontrado para esta comissao."));

        if (cm.getStatus() != CommitteeMembershipStatus.PENDING_USER_ACCEPTANCE) {
            throw new IllegalStateException("Apenas convites pendentes podem ser respondidos.");
        }

        if (accept) {
            cm.setStatus(CommitteeMembershipStatus.ACTIVE);
            cm.setRespondedAt(LocalDateTime.now());
            auditLogRepo.save(new AuditLog(
                user,
                "COMMITTEE_INVITATION_ACCEPTED",
                "Comissao: " + cm.getCommittee().getName() + " | Org: " + cm.getCommittee().getOrganization().getName()
            ));
        } else {
            cm.setStatus(CommitteeMembershipStatus.DECLINED);
            cm.setRespondedAt(LocalDateTime.now());
            auditLogRepo.save(new AuditLog(
                user,
                "COMMITTEE_INVITATION_DECLINED",
                "Comissao: " + cm.getCommittee().getName() + " | Org: " + cm.getCommittee().getOrganization().getName()
            ));
        }

        CommitteeMember saved = committeeMemberRepo.save(cm);
        return CommitteeMemberResponseDTO.fromEntity(saved);
    }

    // 9. Join requests by the user and leader approval

    @Transactional
    public CommitteeMemberResponseDTO requestToJoin(Long committeeId, String userEmail) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissao nao encontrada."));

        User user = findUserByEmail(userEmail);

        if (!organizationMemberRepo.existsByUserIdAndOrganizationId(user.getId(), committee.getOrganization().getId())) {
            throw new IllegalStateException("Voce deve ser membro da organizacao para solicitar entrada em suas comissoes.");
        }

        Optional<CommitteeMember> cmOpt = committeeMemberRepo.findByUserIdAndCommitteeId(user.getId(), committeeId);
        if (cmOpt.isPresent()) {
            CommitteeMember existing = cmOpt.get();
            if (existing.getStatus() == CommitteeMembershipStatus.ACTIVE) {
                throw new IllegalStateException("Voce ja e integrante desta comissao.");
            }
            if (existing.getStatus() == CommitteeMembershipStatus.PENDING_LEADER_APPROVAL) {
                throw new IllegalStateException("Voce ja possui uma solicitacao pendente de aprovacao para esta comissao.");
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
            "Comissao: " + committee.getName() + " | Org: " + committee.getOrganization().getName()
        ));

        return CommitteeMemberResponseDTO.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<CommitteeMemberResponseDTO> listPendingJoinRequests(Long committeeId, String leaderEmail) {
        Committee committee = committeeRepo.findById(committeeId)
                .orElseThrow(() -> new IllegalArgumentException("Comissao nao encontrada."));

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
                .orElseThrow(() -> new IllegalArgumentException("Solicitacao de comissao nao encontrada."));

        User leader = findUserByEmail(leaderEmail);
        requireOrganizationLeader(leader, cm.getCommittee().getOrganization().getId());

        if (cm.getStatus() != CommitteeMembershipStatus.PENDING_LEADER_APPROVAL) {
            throw new IllegalStateException("Apenas solicitacoes pendentes de aprovacao do lider podem ser respondidas.");
        }

        if (approve) {
            cm.setStatus(CommitteeMembershipStatus.ACTIVE);
            cm.setRespondedAt(LocalDateTime.now());
            auditLogRepo.save(new AuditLog(
                leader,
                "COMMITTEE_JOIN_APPROVED",
                "Usuario: " + cm.getUser().getEmail() + " | Comissao: " + cm.getCommittee().getName()
            ));
        } else {
            cm.setStatus(CommitteeMembershipStatus.DECLINED);
            cm.setRespondedAt(LocalDateTime.now());
            auditLogRepo.save(new AuditLog(
                leader,
                "COMMITTEE_JOIN_REJECTED",
                "Usuario: " + cm.getUser().getEmail() + " | Comissao: " + cm.getCommittee().getName()
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
