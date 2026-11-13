package com.spotmeet.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * Text of the minutes. A draft can be saved incomplete; topics and decisions are required to send it.
 */
public class MinutesRequestDTO {

    @Size(max = 4000, message = "Os assuntos tratados podem ter até 4000 caracteres.")
    private String topics;

    @Size(max = 4000, message = "As decisões tomadas podem ter até 4000 caracteres.")
    private String decisions;

    @Size(max = 2000, message = "As observações podem ter até 2000 caracteres.")
    private String notes;

    public MinutesRequestDTO() {}

    public String getTopics() { return topics; }
    public void setTopics(String topics) { this.topics = topics; }

    public String getDecisions() { return decisions; }
    public void setDecisions(String decisions) { this.decisions = decisions; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
