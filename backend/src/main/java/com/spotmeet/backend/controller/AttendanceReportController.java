package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.AttendanceReportDTO;
import com.spotmeet.backend.service.AttendanceReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Individual report route. The user comes from the token, so nobody reads the report of someone else.
 */
@RestController
@CrossOrigin(origins = "*")
public class AttendanceReportController {

    @Autowired
    private AttendanceReportService reportService;

    private String getEmail(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new SecurityException("Usuário não autenticado.");
        }
        return auth.getName();
    }

    /** GET /api/users/me/report?from=2026-10-01&to=2026-10-31&committeeId=3: meetings where the user registered an entry. */
    @GetMapping("/api/users/me/report")
    public ResponseEntity<AttendanceReportDTO> getReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long committeeId,
            Authentication auth) {
        return ResponseEntity.ok(reportService.report(getEmail(auth), from, to, committeeId));
    }
}
