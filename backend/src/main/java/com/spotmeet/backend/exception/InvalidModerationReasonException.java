package com.spotmeet.backend.exception;

public class InvalidModerationReasonException extends IllegalArgumentException {
    public InvalidModerationReasonException() {
        super("Informe um motivo de 3 a 500 caracteres, sem caracteres de controle.");
    }
}
