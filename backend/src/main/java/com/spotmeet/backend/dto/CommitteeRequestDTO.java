package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Committee creation and editing by an organization leader. */
public class CommitteeRequestDTO {

    @NotBlank(message = "O nome da comissão é obrigatório.")
    @Size(min = 2, max = 50, message = "O nome da comissão deve ter entre 2 e 50 caracteres.")
    @Pattern(
        regexp = ValidationPatterns.COMMITTEE_NAME,
        message = "Use de 2 a 50 caracteres: letras, números, espaços, '_' ou '-'."
    )
    private String name;

    @Size(max = 500, message = "A descrição não pode exceder 500 caracteres.")
    private String description;

    public CommitteeRequestDTO() {}

    public CommitteeRequestDTO(String name, String description) {
        setName(name);
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = ValidationPatterns.normalizeOrganizationName(name); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
