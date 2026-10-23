package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.AndroidReleaseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class AppReleaseService {
    private static final String APPLICATION_ID = "com.anonymous.SpotMeetApp";
    private static final Pattern VERSION = Pattern.compile("(?:0|[1-9][0-9]{0,8})(?:\\.(?:0|[1-9][0-9]{0,8})){2}");
    private static final Pattern BUILD_PAGE = Pattern.compile(
            "https://expo\\.dev/accounts/[A-Za-z0-9_-]{1,100}/projects/SpotMeetApp/builds/"
                    + "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}/?");
    private final Optional<AndroidReleaseDTO> release;

    public AppReleaseService(@Value("${spotmeet.app.android.version:}") String version,
                             @Value("${spotmeet.app.android.version-code:}") String versionCode,
                             @Value("${spotmeet.app.android.download-url:}") String downloadUrl) {
        this.release = configuredRelease(version, versionCode, downloadUrl);
    }

    public Optional<AndroidReleaseDTO> getAndroidRelease() {
        return release;
    }

    private Optional<AndroidReleaseDTO> configuredRelease(String version, String versionCode, String downloadUrl) {
        if (version == null || versionCode == null || downloadUrl == null
                || !VERSION.matcher(version).matches() || !versionCode.matches("[1-9][0-9]{0,9}")
                || !BUILD_PAGE.matcher(downloadUrl).matches()) return Optional.empty();
        long build = Long.parseLong(versionCode);
        if (build > Integer.MAX_VALUE) return Optional.empty();
        return Optional.of(new AndroidReleaseDTO(APPLICATION_ID, version, (int) build, downloadUrl));
    }
}
