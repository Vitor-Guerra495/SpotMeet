package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for requesting access to an organization by access key.
 */
public class AccessRequestDTO {

    @NotBlank(message = "Chave da organizacao e obrigatoria.")
    private String accessKey;

    public AccessRequestDTO() {}

    public AccessRequestDTO(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
}
