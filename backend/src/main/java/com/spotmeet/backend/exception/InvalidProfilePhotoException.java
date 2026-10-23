package com.spotmeet.backend.exception;

public class InvalidProfilePhotoException extends RuntimeException {
    public InvalidProfilePhotoException(String message) { super(message); }
}
