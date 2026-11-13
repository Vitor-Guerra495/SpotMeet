package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.MeetingResponseDTO.AttachmentDTO;
import com.spotmeet.backend.service.MeetingFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Agenda attachment: the president sends the file before saving the meeting,
 * and whoever sees the meeting downloads it in the app.
 */
@RestController
@CrossOrigin(origins = "*")
public class MeetingFileController {

    @Autowired
    private MeetingFileService fileService;

    private String getEmail(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new SecurityException("Usuário não autenticado.");
        }
        return auth.getName();
    }

    /** POST /api/committees/{committeeId}/meeting-files: sends the attachment (president only). */
    @PostMapping(value = "/api/committees/{committeeId}/meeting-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentDTO> upload(@PathVariable Long committeeId, @RequestPart("file") MultipartFile file, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fileService.upload(committeeId, getEmail(auth), file));
    }

    /** GET /api/meetings/{meetingId}/attachment: downloads the attachment of the meeting. */
    @GetMapping("/api/meetings/{meetingId}/attachment")
    public ResponseEntity<byte[]> download(@PathVariable Long meetingId, Authentication auth) {
        MeetingFileService.Download file = fileService.download(meetingId, getEmail(auth));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(file.data());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<?> missingFile() {
        return ResponseEntity.badRequest().body(Map.of("message", "Selecione um arquivo para enviar."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> oversizedFile() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("message", "O anexo deve ter no máximo 10 MB."));
    }
}
