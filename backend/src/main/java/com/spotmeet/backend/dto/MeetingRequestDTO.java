package com.spotmeet.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.spotmeet.backend.model.Meeting.MeetingPriority;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Meeting creation and editing by the committee president. */
public class MeetingRequestDTO {

    public static final long MAX_DURATION_HOURS = 24;

    @NotBlank(message = "O título da reunião é obrigatório.")
    @Size(min = 2, max = 100, message = "O título deve ter entre 2 e 100 caracteres.")
    private String title;

    @Size(max = 2000, message = "A pauta não pode exceder 2000 caracteres.")
    private String agenda;

    @Size(max = 100, message = "O local não pode exceder 100 caracteres.")
    private String location;

    @Size(max = 300, message = "O link não pode exceder 300 caracteres.")
    @Pattern(regexp = "^(|https?://\\S+)$", message = "O link deve começar com http:// ou https://.")
    private String link;

    private MeetingPriority priority;

    // Null invites every active member; the president is always invited.
    private List<Long> invitedUserIds;

    @NotNull(message = "A data de início é obrigatória.")
    private LocalDateTime startsAt;

    @NotNull(message = "A data de término é obrigatória.")
    private LocalDateTime endsAt;

    public MeetingRequestDTO() {}

    @JsonIgnore
    @AssertTrue(message = "O término deve ser depois do início.")
    public boolean isEndAfterStart() {
        return startsAt == null || endsAt == null || endsAt.isAfter(startsAt);
    }

    @JsonIgnore
    @AssertTrue(message = "A reunião pode durar no máximo 24 horas.")
    public boolean isWithinMaxDuration() {
        return startsAt == null || endsAt == null
            || !Duration.between(startsAt, endsAt).minusHours(MAX_DURATION_HOURS).isPositive();
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = ValidationPatterns.normalizeOrganizationName(title); }

    public String getAgenda() { return agenda; }
    public void setAgenda(String agenda) { this.agenda = agenda; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public MeetingPriority getPriority() { return priority; }
    public void setPriority(MeetingPriority priority) { this.priority = priority; }

    public List<Long> getInvitedUserIds() { return invitedUserIds; }
    public void setInvitedUserIds(List<Long> invitedUserIds) { this.invitedUserIds = invitedUserIds; }

    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }

    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
}
