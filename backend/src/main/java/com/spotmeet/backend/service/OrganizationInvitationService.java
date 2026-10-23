package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.InviteCandidateDTO;
import com.spotmeet.backend.dto.OrganizationInvitationRequestDTO;
import com.spotmeet.backend.dto.OrganizationInvitationResponseDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.model.AccessRequest.AccessRequestStatus;
import com.spotmeet.backend.model.OrganizationInvitation.InvitationStatus;
import com.spotmeet.backend.model.OrganizationMember.OrganizationRole;
import com.spotmeet.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class OrganizationInvitationService {
    private static final int MIN_SEARCH_LENGTH = 3;
    private static final int MAX_SEARCH_RESULTS = 10;

    @Autowired private OrganizationInvitationRepository invitationRepo;
    @Autowired private OrganizationRepository organizationRepo;
    @Autowired private OrganizationMemberRepository memberRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private AccessRequestRepository accessRequestRepo;
    @Autowired private AuditLogRepository auditLogRepo;

    private User authenticatedUser(String email) {
        return userRepo.findByEmail(email)
                .filter(User::isEmailVerified)
                .orElseThrow(() -> new SecurityException("Usuário autenticado não encontrado ou não confirmado."));
    }

    private void requireOwnerOrVice(User executor, Organization organization) {
        if (organization.getOwner().getId().equals(executor.getId())) return;
        memberRepo.findByUserIdAndOrganizationId(executor.getId(), organization.getId())
                .filter(member -> member.getRole() == OrganizationRole.ORG_VICE_OWNER)
                .orElseThrow(() -> new SecurityException("Somente o dono ou vice desta organização pode convidar."));
    }

    @Transactional
    public OrganizationInvitationResponseDTO invite(Long organizationId, OrganizationInvitationRequestDTO dto, String executorEmail) {
        User executor = authenticatedUser(executorEmail);
        Organization organization = organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        requireOwnerOrVice(executor, organization);
        if (!organization.isApproved()) {
            throw new IllegalStateException("A organização precisa estar aprovada para receber novos membros.");
        }

        User recipient = userRepo.findByEmail(dto.getEmail()).filter(User::isEmailVerified)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado ou cadastro não confirmado."));
        if (organization.getOwner().getId().equals(recipient.getId())
                || memberRepo.existsByUserIdAndOrganizationId(recipient.getId(), organizationId)) {
            throw new IllegalStateException("O usuário já pertence a esta organização.");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        OrganizationInvitation invitation = invitationRepo.findByOrganizationIdAndUserId(organizationId, recipient.getId())
                .orElseGet(OrganizationInvitation::new);
        if (invitation.getStatus() == InvitationStatus.PENDING && invitation.getExpiresAt() != null
                && invitation.getExpiresAt().isAfter(now)) {
            throw new IllegalStateException("Já existe um convite pendente para este usuário.");
        }
        invitation.setOrganization(organization);
        invitation.setUser(recipient);
        invitation.setInvitedBy(executor);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plus(24, ChronoUnit.HOURS));
        invitation.setRespondedAt(null);
        OrganizationInvitation saved = invitationRepo.save(invitation);
        auditLogRepo.save(new AuditLog(executor, "ORGANIZATION_INVITATION_SENT",
                "OrgId: " + organizationId + " | Usuário ID: " + recipient.getId()));
        return OrganizationInvitationResponseDTO.fromEntity(saved);
    }

    /** People the owner or vice can invite, found by part of the name or e-mail (at least 3 characters, up to 10 results). */
    @Transactional(readOnly = true)
    public List<InviteCandidateDTO> findCandidates(Long organizationId, String query, String executorEmail) {
        User executor = authenticatedUser(executorEmail);
        Organization organization = organizationRepo.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        requireOwnerOrVice(executor, organization);
        if (!organization.isApproved()) {
            throw new IllegalStateException("A organização precisa estar aprovada para receber novos membros.");
        }
        String term = query == null ? "" : query.trim();
        if (term.length() < MIN_SEARCH_LENGTH) return List.of();
        String escaped = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return userRepo.findInviteCandidates(escaped, organizationId, organization.getOwner().getId(),
                        Instant.now().truncatedTo(ChronoUnit.MILLIS), PageRequest.of(0, MAX_SEARCH_RESULTS)).stream()
                .map(user -> new InviteCandidateDTO(user.getName(), user.getEmail())).toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizationInvitationResponseDTO> listMine(String email) {
        User recipient = authenticatedUser(email);
        return invitationRepo.findPendingForRecipient(recipient.getId(), InvitationStatus.PENDING, Instant.now()).stream()
                .map(OrganizationInvitationResponseDTO::fromEntity).toList();
    }

    @Transactional
    public OrganizationInvitationResponseDTO respond(Long invitationId, boolean accept, String email) {
        User recipient = authenticatedUser(email);
        Long organizationId = invitationRepo.findOrganizationIdForRecipient(invitationId, recipient.getId())
                .orElseThrow(() -> new SecurityException("Convite não disponível para este usuário."));
        Organization organization = organizationRepo.findByIdForUpdate(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organização não encontrada."));
        OrganizationInvitation invitation = invitationRepo.findById(invitationId)
                .filter(found -> found.getUser().getId().equals(recipient.getId()))
                .orElseThrow(() -> new SecurityException("Convite não disponível para este usuário."));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalStateException("Este convite já foi respondido.");
        }
        if (!invitation.getExpiresAt().isAfter(now)) {
            throw new IllegalStateException("O convite expirou. Peça ao dono ou vice um novo convite.");
        }

        if (accept) {
            if (!organization.isApproved()) {
                throw new IllegalStateException("A organização precisa estar aprovada para receber novos membros.");
            }
            if (memberRepo.existsByUserIdAndOrganizationId(recipient.getId(), organizationId)) {
                throw new IllegalStateException("Você já pertence a esta organização.");
            }
            OrganizationMember member = new OrganizationMember();
            member.setUser(recipient);
            member.setOrganization(organization);
            member.setRole(OrganizationRole.MEMBER);
            memberRepo.save(member);
            for (AccessRequest request : accessRequestRepo.findByUserIdAndOrganizationId(recipient.getId(), organizationId)) {
                if (request.getStatus() == AccessRequestStatus.PENDING) {
                    request.setStatus(AccessRequestStatus.APPROVED);
                    accessRequestRepo.save(request);
                }
            }
            invitation.setStatus(InvitationStatus.ACCEPTED);
            auditLogRepo.save(new AuditLog(recipient, "ORGANIZATION_INVITATION_ACCEPTED",
                    "Convite ID: " + invitationId + " | OrgId: " + organizationId));
        } else {
            invitation.setStatus(InvitationStatus.REJECTED);
            auditLogRepo.save(new AuditLog(recipient, "ORGANIZATION_INVITATION_REJECTED",
                    "Convite ID: " + invitationId + " | OrgId: " + organizationId));
        }
        invitation.setRespondedAt(now);
        return OrganizationInvitationResponseDTO.fromEntity(invitationRepo.save(invitation));
    }
}
