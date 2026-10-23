package com.spotmeet.backend.controller;

import com.spotmeet.backend.exception.InvalidProfilePhotoException;
import com.spotmeet.backend.service.ProfilePhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/users/me/photo")
public class ProfilePhotoController {
    private final ProfilePhotoService photos;

    public ProfilePhotoController(ProfilePhotoService photos) { this.photos = photos; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(Authentication authentication) {
        return respond(authentication, () -> ResponseEntity.ok(photos.get(authentication.getName())));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> save(@RequestPart("photo") MultipartFile photo, Authentication authentication) {
        return respond(authentication, () -> ResponseEntity.ok(photos.save(authentication.getName(), photo)));
    }

    @DeleteMapping
    public ResponseEntity<?> remove(Authentication authentication) {
        return respond(authentication, () -> { photos.remove(authentication.getName()); return ResponseEntity.noContent().build(); });
    }

    private ResponseEntity<?> respond(Authentication authentication, Supplier<ResponseEntity<?>> operation) {
        if (authentication == null || authentication.getName() == null) return error(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        try {
            ResponseEntity<?> response = operation.get();
            return ResponseEntity.status(response.getStatusCode()).header("Cache-Control", "no-store").body(response.getBody());
        } catch (InvalidProfilePhotoException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (SecurityException exception) {
            return error(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        } catch (Exception exception) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível acessar a foto de perfil. Tente novamente.");
        }
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<?> missingPhoto() { return error(HttpStatus.BAD_REQUEST, "Selecione uma foto para enviar."); }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> oversizedPhoto() { return error(HttpStatus.PAYLOAD_TOO_LARGE, "A foto deve ter no máximo 1 MB."); }

    private ResponseEntity<?> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(Map.of("message", message));
    }
}
