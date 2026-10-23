package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.OrganizationRequestDTO;
import com.spotmeet.backend.dto.OrganizationResponseDTO;
import com.spotmeet.backend.dto.ValidationPatterns;
import com.spotmeet.backend.model.AccessRequest;
import com.spotmeet.backend.model.AuditLog;
import com.spotmeet.backend.model.Committee;
import com.spotmeet.backend.model.CommitteeMember;
import com.spotmeet.backend.model.Organization;
import com.spotmeet.backend.model.OrganizationMember;
import com.spotmeet.backend.model.OrganizationMember.OrganizationRole;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.AccessRequestRepository;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.CommitteeMemberRepository;
import com.spotmeet.backend.repository.CommitteeRepository;
import com.spotmeet.backend.repository.OrganizationMemberRepository;
import com.spotmeet.backend.repository.OrganizationInvitationRepository;
import com.spotmeet.backend.repository.OrganizationRepository;
import com.spotmeet.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Organization management service.
 *
 * Security:
 *   - The owner is always taken from the JWT (never from the body).
 *   - Names and keys preserve case; uniqueness checks ignore case and extra whitespace.
 *   - Uniqueness checks and writes share a transaction lock (409 on duplicate).
 *   - Every critical action is recorded in AuditLog (LGPD).
 *   - Organizations start with approved=false (SysAdmin must approve).
 */
@Service
public class OrganizationService {

    @Autowired
    private OrganizationRepository organizationRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepo;

    @Autowired
    private CommitteeRepository committeeRepo;

    @Autowired
    private CommitteeMemberRepository committeeMemberRepo;

    @Autowired
    private AccessRequestRepository accessRequestRepo;

    @Autowired
    private OrganizationInvitationRepository invitationRepo;

    @Autowired
    private AuditLogRepository auditLogRepo;

    /**
     * Creates a new organization.
     *
     * @param ownerEmail e-mail taken from the JWT (never from the body).
     * @param dto        validated request data.
     * @return OrganizationResponseDTO without sensitive fields.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrganizationResponseDTO createOrganization(String ownerEmail, OrganizationRequestDTO dto) {
        String cleanKey = ValidationPatterns.normalizeOrganizationValue(dto.getAccessKey());
        String cleanName = ValidationPatterns.normalizeOrganizationName(dto.getName());
        String cleanCnpj = normalizeCnpj(dto.getCnpj());

        User owner = userRepo.findByEmail(ownerEmail)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));

        organizationRepo.lockForUniquenessCheck();
        checkAvailability(cleanName, cleanKey, cleanCnpj, null);

        Organization org = new Organization();
        org.setName(cleanName);
        org.setAccessKey(cleanKey);
        org.setOwner(owner);
        org.setCnpj(cleanCnpj);
        org.setApproved(false); // Pending until SysAdmin approval
        org.setStatus("PENDING");

        Organization saved = organizationRepo.save(org);

        OrganizationMember membership = new OrganizationMember();
        membership.setUser(owner);
        membership.setOrganization(saved);
        membership.setRole(OrganizationRole.ORG_OWNER);
        organizationMemberRepo.save(membership);

        auditLogRepo.save(new AuditLog(
            owner,
            "ORGANIZATION_CREATED",
            "Organização: " + saved.getName() + " | Chave: " + saved.getAccessKey() + " | ID: " + saved.getId()
        ));

        return OrganizationResponseDTO.forViewer(saved, owner);
    }

    /**
     * Lists all organizations of the authenticated user.
     *
     * @param userEmail e-mail taken from the JWT.
     */
    @Transactional(readOnly = true)
    public List<OrganizationResponseDTO> listMyOrganizations(String userEmail) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));

        return organizationMemberRepo.findByUserId(user.getId())
                .stream()
                .map(membership -> {
                    OrganizationResponseDTO dto = OrganizationResponseDTO.forViewer(membership.getOrganization(), user);
                    dto.setMyRole(membership.getRole().name());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Updates the name, access key and CNPJ of an organization.
     *
     * Security:
     *   - Authorization: owner, vice owner or SYSADMIN, the same rule used for deletion.
     *   - 'approved', 'status' and 'owner' are never taken from the body (mass assignment protection),
     *     so editing cannot be used to self-approve a pending organization.
     *   - The access key stays unique; changing it invalidates the previous key for new access requests.
     *   - A null CNPJ in the body keeps the stored value; a blank one clears it.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrganizationResponseDTO updateOrganization(Long organizationId, String executorEmail, OrganizationRequestDTO dto) {
        User executor = userRepo.findByEmail(executorEmail)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));

        organizationRepo.lockForUniquenessCheck();
        Organization org = organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));

        requireOwnerOrViceOwner(executor, org);

        String cleanName = ValidationPatterns.normalizeOrganizationName(dto.getName());
        String cleanKey = ValidationPatterns.normalizeOrganizationValue(dto.getAccessKey());
        String cleanCnpj = dto.getCnpj() == null ? org.getCnpj() : normalizeCnpj(dto.getCnpj());

        checkAvailability(cleanName, cleanKey, cleanCnpj, organizationId);

        String previousName = org.getName();
        String previousKey = org.getAccessKey();

        org.setName(cleanName);
        org.setAccessKey(cleanKey);

        // The CNPJ is optional and is not exposed by OrganizationResponseDTO, so a client that
        // cannot read it must not erase it: null means "unchanged", blank means "clear".
        org.setCnpj(cleanCnpj);

        Organization saved = organizationRepo.save(org);

        auditLogRepo.save(new AuditLog(
            executor,
            "ORGANIZATION_UPDATED",
            "Organização ID: " + saved.getId()
                + " | Nome: " + previousName + " -> " + saved.getName()
                + " | Chave: " + previousKey + " -> " + saved.getAccessKey()
        ));

        return OrganizationResponseDTO.forViewer(saved, executor);
    }

    /**
     * Sends a rejected organization back to the SysAdmin queue with the owner's justification.
     * Only the owner can do it, and only once: a second rejection is final.
     * The previous rejection reason is kept so the SysAdmin can compare it with the justification.
     */
    @Transactional
    public OrganizationResponseDTO resubmitOrganization(Long organizationId, String executorEmail, String justification) {
        User executor = userRepo.findByEmail(executorEmail)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
        Organization org = organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));

        if (org.getOwner() == null || !org.getOwner().getId().equals(executor.getId())) {
            throw new SecurityException("Somente o dono da organização pode pedir a reavaliação.");
        }
        if (!"REJECTED".equalsIgnoreCase(org.getStatus())) {
            throw new IllegalStateException("Somente uma organização recusada pode ser reenviada para avaliação.");
        }
        if (org.getResubmittedAt() != null) {
            throw new IllegalStateException("A reavaliação desta organização já foi usada. Exclua a organização e crie outra, se necessário.");
        }
        String note = justification == null ? "" : justification.trim();
        if (note.length() < 3 || note.length() > 500
                || note.codePoints().anyMatch(value -> Character.isISOControl(value) && value != 10 && value != 9)) {
            throw new IllegalArgumentException("Informe uma justificativa de 3 a 500 caracteres, sem caracteres de controle.");
        }

        org.setStatus("PENDING");
        org.setApproved(false);
        org.setResubmissionNote(note);
        org.setResubmittedAt(java.time.LocalDateTime.now());
        Organization saved = organizationRepo.save(org);

        auditLogRepo.save(new AuditLog(
            executor,
            "ORGANIZATION_RESUBMITTED",
            "Organização ID: " + saved.getId() + " (" + saved.getName() + ") reenviada para avaliação | Justificativa informada ("
                + note.length() + " caracteres)"
        ));

        return OrganizationResponseDTO.forViewer(saved, executor);
    }

    private void checkAvailability(String name, String accessKey, String cnpj, Long excludedId) {
        if (organizationRepo.existsByNormalizedName(name, ValidationPatterns.ORGANIZATION_WHITESPACE, excludedId)) {
            throw new IllegalArgumentException("Já existe uma organização com esse nome. Escolha outro nome.");
        }
        if (organizationRepo.existsByNormalizedAccessKey(accessKey, ValidationPatterns.ORGANIZATION_WHITESPACE, excludedId)) {
            throw new IllegalArgumentException("Já existe uma organização com essa chave de acesso. Escolha outra chave.");
        }
        if (cnpj != null && organizationRepo.existsByCnpjExcludingId(cnpj, excludedId)) {
            throw new IllegalArgumentException("Já existe uma organização com esse CNPJ.");
        }
    }

    private String normalizeCnpj(String value) {
        String normalized = ValidationPatterns.normalizeOrganizationValue(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    /**
     * Requires the executor to be the owner, a vice owner of the organization, or SYSADMIN.
     */
    private void requireOwnerOrViceOwner(User executor, Organization org) {
        if ("SYSADMIN".equalsIgnoreCase(executor.getRole())) {
            return;
        }

        if (org.getOwner() != null && org.getOwner().getId().equals(executor.getId())) {
            return;
        }

        OrganizationMember membership = organizationMemberRepo
                .findByUserIdAndOrganizationId(executor.getId(), org.getId())
                .orElseThrow(() -> new SecurityException("Acesso negado. Você não pertence a esta organização."));

        if (membership.getRole() != OrganizationRole.ORG_OWNER && membership.getRole() != OrganizationRole.ORG_VICE_OWNER) {
            throw new SecurityException("Acesso negado. Apenas o Dono ou Vice-Líder pode editar a organização.");
        }
    }

    /**
     * Permanently deletes the organization and all associated data in cascade:
     * committee memberships, committees, access requests and members.
     *
     * Authorization: owner (ORG_OWNER), vice owner (ORG_VICE_OWNER) or SYSADMIN only.
     */
    @Transactional
    public void deleteOrganization(Long organizationId, String executorEmail) {
        User executor = userRepo.findByEmail(executorEmail)
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));

        Organization org = organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));

        if (!"SYSADMIN".equalsIgnoreCase(executor.getRole())) {
            boolean isOwner = (org.getOwner() != null && org.getOwner().getId().equals(executor.getId()));
            if (!isOwner) {
                OrganizationMember membership = organizationMemberRepo
                        .findByUserIdAndOrganizationId(executor.getId(), organizationId)
                        .orElseThrow(() -> new SecurityException("Acesso negado. Você não pertence a esta organização."));

                if (membership.getRole() != OrganizationRole.ORG_OWNER && membership.getRole() != OrganizationRole.ORG_VICE_OWNER) {
                    throw new SecurityException("Acesso negado. Apenas o Dono ou Vice-Líder pode apagar toda a organização.");
                }
            }
        }

        invitationRepo.deleteByOrganizationId(organizationId);

        // 1. Delete committee memberships and committees
        List<Committee> committees = committeeRepo.findByOrganizationIdOrderByNameAsc(organizationId);
        for (Committee c : committees) {
            List<CommitteeMember> members = committeeMemberRepo.findByCommitteeId(c.getId());
            if (!members.isEmpty()) {
                committeeMemberRepo.deleteAll(members);
            }
        }
        if (!committees.isEmpty()) {
            committeeRepo.deleteAll(committees);
        }

        // 2. Delete access requests
        List<AccessRequest> requests = accessRequestRepo.findByOrganizationId(organizationId);
        if (!requests.isEmpty()) {
            accessRequestRepo.deleteAll(requests);
        }

        // 3. Delete organization members
        List<OrganizationMember> members = organizationMemberRepo.findByOrganizationId(organizationId);
        if (!members.isEmpty()) {
            organizationMemberRepo.deleteAll(members);
        }

        // 4. Delete the organization
        organizationRepo.delete(org);

        // 5. LGPD audit record
        auditLogRepo.save(new AuditLog(
            executor,
            "ORGANIZATION_DELETED",
            "Organização excluída ID: " + organizationId + " | Nome: " + org.getName() + " | Chave: " + org.getAccessKey()
        ));
    }
}
