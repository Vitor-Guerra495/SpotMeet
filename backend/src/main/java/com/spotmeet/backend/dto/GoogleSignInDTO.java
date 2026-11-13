package com.spotmeet.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/auth/google. */
public class GoogleSignInDTO {

    @NotBlank(message = "O token do Google é obrigatório.")
    @Size(max = 4000, message = "Token do Google inválido.")
    private String idToken;

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }
}
