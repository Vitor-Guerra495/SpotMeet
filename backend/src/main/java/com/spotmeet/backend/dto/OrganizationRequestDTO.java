package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Organization creation/edition DTO.
 *
 * Mass assignment protection:
 *   - 'approved' never comes from the client; always false in the service.
 *   - 'owner' is taken from the JWT in the controller, never from the body.
 */
public class OrganizationRequestDTO {

    @NotBlank(message = "Nome da organizacao e obrigatorio.")
    private String name;

    /**
     * Access key: '#' followed by 2 to 50 alphanumeric characters, underscore or hyphen.
     * Stored exactly as provided (case-sensitive).
     */
    @NotBlank(message = "Chave da organizacao e obrigatoria.")
    @Pattern(
        regexp = "^#[A-Za-z0-9_\\-]{2,50}$",
        message = "Chave invalida. Use '#' seguido de 2 a 50 caracteres alfanumericos, '_' ou '-'. Exemplo: #Minerva"
    )
    private String accessKey;

    /** Optional CNPJ. */
    private String cnpj;

    public OrganizationRequestDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }
}
