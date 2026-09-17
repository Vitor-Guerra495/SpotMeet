package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.OrganizationRequestDTO;
import com.spotmeet.backend.dto.OrganizationResponseDTO;
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
import com.spotmeet.backend.repository.OrganizationRepository;
import com.spotmeet.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Organization management service.
 *
 * Security:
 *   - The owner is always taken from the JWT (never from the body).
 *   - The access key is stored without case transformation (case-sensitive).
 *   - Key uniqueness is checked before persisting (409 on duplicate).
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
    private AuditLogRepository auditLogRepo;

    /**
     * Creates a new organization.
     *
     * @param ownerEmail e-mail taken from the JWT (never from the body).
     * @param dto        validated request data.
     * @return OrganizationResponseDTO without sensitive fields.
     */
    @Transactional
    public OrganizationResponseDTO createOrganization(String ownerEmail, OrganizationRequestDTO dto) {
        String cleanKey = dto.getAccessKey() != null ? dto.getAccessKey().trim() : "";
        String cleanName = dto.getName() != null ? dto.getName().trim() : "";

        if (organizationRepo.existsByAccessKey(cleanKey)) {
            throw new IllegalArgumentException(
                "Chave '" + cleanKey + "' ja esta em uso. Escolha uma chave diferente."
            );
        }

        User owner = userRepo.findByEmail(ownerEmail)
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado nao encontrado."));

        Organization org = new Organization();
        org.setName(cleanName);
        org.setAccessKey(cleanKey);
        org.setOwner(owner);
        org.setCnpj(dto.getCnpj());
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
            "Organizacao: " + saved.getName() + " | Chave: " + saved.getAccessKey() + " | ID: " + saved.getId()
        ));

        return OrganizationResponseDTO.fromEntity(saved);
    }

    /**
     * Lists all organizations of the authenticated user.
     *
     * @param userEmail e-mail taken from the JWT.
     */
    @Transactional(readOnly = true)
    public List<OrganizationResponseDTO> listMyOrganizations(String userEmail) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado nao encontrado."));

        return organizationMemberRepo.findByUserId(user.getId())
                .stream()
                .map(membership -> {
                    OrganizationResponseDTO dto = OrganizationResponseDTO.fromEntity(membership.getOrganization());
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
    @Transactional
    public OrganizationResponseDTO updateOrganization(Long organizationId, String executorEmail, OrganizationRequestDTO dto) {
        User executor = userRepo.findByEmail(executorEmail)
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado nao encontrado."));

        Organization org = organizationRepo.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organizacao nao encontrada."));

        requireOwnerOrViceOwner(executor, org);

        String cleanName = dto.getName() != null ? dto.getName().trim() : "";
        String cleanKey = dto.getAccessKey() != null ? dto.getAccessKey().trim() : "";

        boolean keyTakenByAnother = organizationRepo.findByAccessKey(cleanKey)
                .filter(other -> !other.getId().equals(organizationId))
                .isPresent();
        if (keyTakenByAnother) {
            throw new IllegalArgumentException(
                "Chave '" + cleanKey + "' ja esta em uso. Escolha uma chave diferente."
            );
        }

        String previousName = org.getName();
        String previousKey = org.getAccessKey();

        org.setName(cleanName);
        org.setAccessKey(cleanKey);

        // The CNPJ is optional and is not exposed by OrganizationResponseDTO, so a client that
        // cannot read it must not erase it: null means "unchanged", blank means "clear".
        if (dto.getCnpj() != null) {
            String cleanCnpj = dto.getCnpj().trim();
            org.setCnpj(cleanCnpj.isEmpty() ? null : cleanCnpj);
        }

        Organization saved = organizationRepo.save(org);

        auditLogRepo.save(new AuditLog(
            executor,
            "ORGANIZATION_UPDATED",
            "Organizacao ID: " + saved.getId()
                + " | Nome: " + previousName + " -> " + saved.getName()
                + " | Chave: " + previousKey + " -> " + saved.getAccessKey()
        ));

        return OrganizationResponseDTO.fromEntity(saved);
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
                .orElseThrow(() -> new SecurityException("Acesso negado. Voce nao pertence a esta organizacao."));

        if (membership.getRole() != OrganizationRole.ORG_OWNER && membership.getRole() != OrganizationRole.ORG_VICE_OWNER) {
            throw new SecurityException("Acesso negado. Apenas o Dono ou Vice-Lider pode editar a organizacao.");
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
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado nao encontrado."));

        Organization org = organizationRepo.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organizacao nao encontrada."));

        if (!"SYSADMIN".equalsIgnoreCase(executor.getRole())) {
            boolean isOwner = (org.getOwner() != null && org.getOwner().getId().equals(executor.getId()));
            if (!isOwner) {
                OrganizationMember membership = organizationMemberRepo
                        .findByUserIdAndOrganizationId(executor.getId(), organizationId)
                        .orElseThrow(() -> new SecurityException("Acesso negado. Voce nao pertence a esta organizacao."));

                if (membership.getRole() != OrganizationRole.ORG_OWNER && membership.getRole() != OrganizationRole.ORG_VICE_OWNER) {
                    throw new SecurityException("Acesso negado. Apenas o Dono ou Vice-Lider pode apagar toda a organizacao.");
                }
            }
        }

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
            "Organizacao excluida ID: " + organizationId + " | Nome: " + org.getName() + " | Chave: " + org.getAccessKey()
        ));
    }
}
