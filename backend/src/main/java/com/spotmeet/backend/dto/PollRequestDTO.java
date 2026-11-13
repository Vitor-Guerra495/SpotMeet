package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Question of a new vote, with an optional description. */
public class PollRequestDTO {

    @NotBlank(message = "Escreva a pergunta da votação.")
    @Size(max = 150, message = "A pergunta pode ter até 150 caracteres.")
    private String question;

    @Size(max = 500, message = "A descrição pode ter até 500 caracteres.")
    private String description;

    public PollRequestDTO() {}

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
