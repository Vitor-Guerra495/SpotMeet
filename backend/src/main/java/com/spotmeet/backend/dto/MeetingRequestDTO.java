package com.spotmeet.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.spotmeet.backend.model.Meeting.MeetingModality;
import com.spotmeet.backend.model.Meeting.MeetingPriority;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Meeting creation and editing by the committee president.
 *
 * With allDay the server keeps only the dates: 00:00 of the first day to 23:59 of the last one.
 * The recurrence is read only when scheduling; editing changes only the chosen meeting.
 */
public class MeetingRequestDTO {

    // Web address with a dot in the host; the scheme is added by the setter when missing.
    private static final String LINK_PATTERN = "(?i)^(|https?://[^\\s/?#:@]+\\.[^\\s/?#:@]+(:\\d{1,5})?([/?#]\\S*)?)$";

    @NotBlank(message = "O título da reunião é obrigatório.")
    @Size(min = 2, max = 100, message = "O título deve ter entre 2 e 100 caracteres.")
    private String title;

    @Size(max = 2000, message = "A pauta não pode exceder 2000 caracteres.")
    private String agenda;

    @Size(max = 100, message = "O local não pode exceder 100 caracteres.")
    private String location;

    @Size(max = 300, message = "O link não pode exceder 300 caracteres.")
    @Pattern(regexp = LINK_PATTERN, message = "Informe um endereço válido, como meet.google.com/abc-defg-hij.")
    private String link;

    private MeetingPriority priority;

    private MeetingModality modality;

    private Boolean allDay;

    // Null invites every active member; the president is always invited.
    private List<Long> invitedUserIds;

    @NotNull(message = "A data de início é obrigatória.")
    private LocalDateTime startsAt;

    @NotNull(message = "A data de término é obrigatória.")
    private LocalDateTime endsAt;

    // File sent before by POST /api/committees/{id}/meeting-files; null means no attachment.
    private Long attachmentFileId;

    @Valid
    private MeetingRecurrenceDTO recurrence;

    public MeetingRequestDTO() {}

    @JsonIgnore
    @AssertTrue(message = "O término deve ser depois do início.")
    public boolean isEndAfterStart() {
        if (startsAt == null || endsAt == null) return true;
        return wholeDay() ? !endsAt.toLocalDate().isBefore(startsAt.toLocalDate()) : endsAt.isAfter(startsAt);
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = ValidationPatterns.normalizeOrganizationName(title); }

    public String getAgenda() { return agenda; }
    public void setAgenda(String agenda) { this.agenda = agenda; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getLink() { return link; }
    public void setLink(String link) {
        String value = link == null ? null : link.trim();
        if (value != null && !value.isEmpty() && !value.toLowerCase(Locale.ROOT).matches("^https?://.*")) {
            value = "https://" + value;
        }
        this.link = value;
    }

    public MeetingPriority getPriority() { return priority; }
    public void setPriority(MeetingPriority priority) { this.priority = priority; }

    public MeetingModality getModality() { return modality; }
    public void setModality(MeetingModality modality) { this.modality = modality; }

    /** Not a bean property, so it does not clash with allDay in the JSON. */
    public boolean wholeDay() { return Boolean.TRUE.equals(allDay); }
    public Boolean getAllDay() { return allDay; }
    public void setAllDay(Boolean allDay) { this.allDay = allDay; }

    public List<Long> getInvitedUserIds() { return invitedUserIds; }
    public void setInvitedUserIds(List<Long> invitedUserIds) { this.invitedUserIds = invitedUserIds; }

    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }

    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }

    public Long getAttachmentFileId() { return attachmentFileId; }
    public void setAttachmentFileId(Long attachmentFileId) { this.attachmentFileId = attachmentFileId; }

    public MeetingRecurrenceDTO getRecurrence() { return recurrence; }
    public void setRecurrence(MeetingRecurrenceDTO recurrence) { this.recurrence = recurrence; }
}
