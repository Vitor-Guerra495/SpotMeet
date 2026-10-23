package com.spotmeet.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class VerifyEmailDTO {

    @NotBlank(message = "E-mail é obrigatório.")
    @Email(regexp = ValidationPatterns.EMAIL, message = "Formato de e-mail inválido.")
    private String email;

    @NotBlank(message = "O código de verificação é obrigatório.")
    @Pattern(regexp = ValidationPatterns.OTP, message = "O código de verificação deve conter 6 dígitos numéricos.")
    private String code;

    public VerifyEmailDTO() {}

    public VerifyEmailDTO(String email, String code) {
        this.email = email;
        this.code = code;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
