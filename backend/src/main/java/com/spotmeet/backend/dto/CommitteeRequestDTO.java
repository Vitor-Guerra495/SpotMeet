package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for committee creation/edition by a leader.
 * Validates that the name has no spaces.
 */
public class CommitteeRequestDTO {

    @NotBlank(message = "O nome da comissao e obrigatorio.")
    @Pattern(
        regexp = "^[A-Za-z0-9_-]{2,50}$",
        message = "O nome da comissao nao pode conter espacos e deve ter entre 2 e 50 caracteres (letras, numeros, '_' ou '-')."
    )
    private String name;

    @Size(max = 500, message = "A descricao nao pode exceder 500 caracteres.")
    private String description;

    public CommitteeRequestDTO() {}

    public CommitteeRequestDTO(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
