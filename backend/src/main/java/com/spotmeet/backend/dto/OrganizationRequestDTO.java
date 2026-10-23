package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Organization creation/edition DTO.
 *
 * Mass assignment protection:
 *   - 'approved' never comes from the client; always false in the service.
 *   - 'owner' is taken from the JWT in the controller, never from the body.
 */
public class OrganizationRequestDTO {

    @NotBlank(message = "Nome da organização é obrigatório.")
    @Size(max = 255, message = "O nome da organização deve ter até 255 caracteres.")
    private String name;

    /**
     * Access key: '#' followed by 2 to 50 alphanumeric characters, underscore or hyphen.
     * Case is preserved; uniqueness and lookup ignore case.
     */
    @NotBlank(message = "Chave da organização é obrigatória.")
    @Pattern(
        regexp = ValidationPatterns.ORGANIZATION_ACCESS_KEY,
        message = "Chave inválida. Use '#' seguido de 2 a 50 caracteres alfanuméricos, '_' ou '-'. Exemplo: #Minerva"
    )
    private String accessKey;

    /** Optional CNPJ. */
    @Size(max = 255, message = "O CNPJ deve ter até 255 caracteres.")
    private String cnpj;

    public OrganizationRequestDTO() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = ValidationPatterns.normalizeOrganizationName(name); }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = ValidationPatterns.normalizeOrganizationValue(accessKey); }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = ValidationPatterns.normalizeOrganizationValue(cnpj); }
}
