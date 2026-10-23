package com.spotmeet.backend;

import com.spotmeet.backend.config.SecurityConfig;
import com.spotmeet.backend.controller.AppReleaseController;
import com.spotmeet.backend.security.JwtAuthFilter;
import com.spotmeet.backend.security.JwtUtil;
import com.spotmeet.backend.service.AppReleaseService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppReleaseTests {
    private static final String PAGE = "https://expo.dev/accounts/qa-team/projects/SpotMeetApp/builds/11111111-2222-3333-4444-555555555555";
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean UserDetailsService users() { return mock(UserDetailsService.class); }
        @Bean JwtUtil jwt() { return mock(JwtUtil.class); }
        @Bean AppReleaseService releases() { return new AppReleaseService("1.10.0", "42", PAGE); }
        @Bean AppReleaseController controller(AppReleaseService releases) { return new AppReleaseController(releases); }
    }

    @BeforeAll static void startSecurity() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "spotmeet.jwt.expiration-ms=3600000",
                "spotmeet.jwt.secret=" + java.util.Base64.getEncoder().encodeToString(new byte[32]));
        context.register(TestConfig.class, SecurityConfig.class, JwtAuthFilter.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterAll static void closeSecurity() { context.close(); }

    @Test void onlyPublicReleaseMetadataIsReturnedWithoutAnAccountOrDatabase() throws Exception {
        mvc.perform(get("/api/app/android-release"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.applicationId").value("com.anonymous.SpotMeetApp"))
                .andExpect(jsonPath("$.version").value("1.10.0"))
                .andExpect(jsonPath("$.versionCode").value(42))
                .andExpect(jsonPath("$.downloadUrl").value(PAGE));
        verifyNoInteractions(context.getBean(UserDetailsService.class), context.getBean(JwtUtil.class));
    }

    @Test void anUnconfiguredReleaseDoesNotAdvertiseAnUnpublishedApk() throws Exception {
        var empty = MockMvcBuilders.standaloneSetup(new AppReleaseController(new AppReleaseService("", "", ""))).build();
        empty.perform(get("/api/app/android-release"))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
    }

    @Test void releaseRouteDoesNotMakeOtherOperationsPublic() throws Exception {
        for (var request : java.util.List.of(post("/api/app/android-release"), put("/api/app/android-release"),
                delete("/api/app/android-release"), get("/api/app/other"), get("/api/users/me"), get("/api/admin/system"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
        }
    }

    @Test void releaseDoesNotServeHtml() throws Exception {
        mvc.perform(get("/api/app/android-release").accept(MediaType.TEXT_HTML)).andExpect(status().isNotAcceptable());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1", "1.0", "v1.0.0", "1.0.0-beta", "01.0.0", "1.0.0\n", "1.0.0\u2028", " 1.0.0", "<script>"})
    void invalidVersionsDisableAdvertisement(String version) {
        assertTrue(new AppReleaseService(version, "42", PAGE).getAndroidRelease().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "1.2", "01", "2147483648", "9999999999", "1\n", " 42", "not-a-build"})
    void invalidBuildNumbersDisableAdvertisement(String build) {
        assertTrue(new AppReleaseService("1.0.0", build, PAGE).getAndroidRelease().isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "http://expo.dev/accounts/qa-team/projects/SpotMeetApp/builds/11111111-2222-3333-4444-555555555555",
            "https://expo.dev.evil.test/accounts/qa-team/projects/SpotMeetApp/builds/11111111-2222-3333-4444-555555555555",
            "https://expo.dev@evil.test/", "javascript:alert(1)", "file:///private/photo", "intent://package",
            "https://expo.dev/accounts/qa-team/projects/OtherApp/builds/11111111-2222-3333-4444-555555555555",
            "https://expo.dev/accounts/qa-team/projects/SpotMeetApp/builds/11111111-2222-3333-4444-555555555555?redirect=https://evil.test",
            "https://expo.dev/accounts/qa-team/projects/SpotMeetApp/builds/11111111-2222-3333-4444-555555555555#fragment",
            "https://expo.dev/accounts/qa-team/projects/SpotMeetApp/builds/../bad",
            "https://expo.dev/accounts/qa-team/projects/SpotMeetApp/builds/11111111-2222-3333-4444-555555555555\n"
    })
    void onlyExactExpoProjectBuildPagesCanBeAdvertised(String page) {
        assertTrue(new AppReleaseService("1.0.0", "42", page).getAndroidRelease().isEmpty());
    }

    @Test void largestAndroidBuildNumberAndTrailingSlashAreSupported() {
        var release = new AppReleaseService("999999999.0.0", "2147483647", PAGE + "/").getAndroidRelease().orElseThrow();
        assertEquals(Integer.MAX_VALUE, release.versionCode());
        assertEquals(PAGE + "/", release.downloadUrl());
    }
}
