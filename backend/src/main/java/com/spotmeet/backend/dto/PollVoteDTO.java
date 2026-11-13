package com.spotmeet.backend.dto;

import com.spotmeet.backend.model.PollVote.VoteChoice;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Vote of a present person; the justification is only kept for an abstention. */
public class PollVoteDTO {

    @NotNull(message = "Escolha Sim, Não ou Abstenção.")
    private VoteChoice choice;

    @Size(max = 280, message = "A justificativa pode ter até 280 caracteres.")
    private String justification;

    public PollVoteDTO() {}

    public VoteChoice getChoice() { return choice; }
    public void setChoice(VoteChoice choice) { this.choice = choice; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }
}
