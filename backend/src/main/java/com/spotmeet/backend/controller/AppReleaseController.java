package com.spotmeet.backend.controller;

import com.spotmeet.backend.dto.AndroidReleaseDTO;
import com.spotmeet.backend.service.AppReleaseService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class AppReleaseController {
    private final AppReleaseService releases;

    public AppReleaseController(AppReleaseService releases) {
        this.releases = releases;
    }

    @GetMapping(value = "/android-release", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AndroidReleaseDTO> androidRelease() {
        return releases.getAndroidRelease()
                .map(release -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(release))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }
}
