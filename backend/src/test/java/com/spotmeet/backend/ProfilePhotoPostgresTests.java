package com.spotmeet.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.dto.CriticalActionRequestDTO;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.UserProfilePhotoRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.security.JwtUtil;
import com.spotmeet.backend.service.AdminService;
import com.spotmeet.backend.service.ProfilePhotoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "SPOTMEET_PROFILE_PHOTO_QA", matches = "1")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.mail.username=", "spring.mail.password=", "spring.jpa.hibernate.ddl-auto=update"})
@AutoConfigureMockMvc
class ProfilePhotoPostgresTests {
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry properties) {
        String url = System.getenv("SPOTMEET_QA_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/spotmeet_lote8_qa")) {
            throw new IllegalStateException("An isolated photo QA database is required.");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "postgres"); properties.add("spring.datasource.password", () -> "");
        properties.add("spotmeet.jwt.secret", () -> Base64.getEncoder().encodeToString("isolated-profile-photo-fixture-key-12345".getBytes(StandardCharsets.UTF_8)));
    }
    @MockBean(name = "sysAdminBootstrap") ApplicationRunner bootstrap;
    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate sql;
    @Autowired UserRepository users;
    @Autowired UserProfilePhotoRepository photos;
    @Autowired ProfilePhotoService photoService;
    @Autowired AdminService adminService;
    @Autowired JwtUtil jwt;
    @Autowired PlatformTransactionManager transactions;

    private User user(String name, String role) {
        User user = new User(); user.setName(name); user.setEmail(name + "@example.com"); user.setRole(role);
        user.setPassword("unused-fixture-password"); user.setEmailVerified(true); return users.save(user);
    }
    private String authorization(User user) { return "Bearer " + jwt.generateToken(user.getEmail(), user.getRole()); }
    private MockMultipartFile file(int side) throws Exception {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output); return new MockMultipartFile("photo", "ignored.png", "image/png", output.toByteArray());
        } finally { image.flush(); }
    }
    private String upload(User user, int side, String extraUserId) throws Exception {
        String body = mvc.perform(multipart("/api/users/me/photo").file(file(side)).param("userId", extraUserId)
                        .with(request -> { request.setMethod("PUT"); return request; }).header("Authorization", authorization(user)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.userId").value(user.getId())).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("version").asText();
    }

    @Test void privatePhotoCrudPreservesProfilesEnforcesUploadLimitsAndCascadesReset() throws Exception {
        assertEquals(9, sql.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'", Integer.class));
        User alice = user("qaalice", "USER"), bob = user("qabob", "USER"), admin = user("qaadmin", "SYSADMIN");
        mvc.perform(get("/api/users/me/photo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/photo").header("Authorization", authorization(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageBase64").isEmpty());
        String first = upload(alice, 32, bob.getId().toString());
        assertEquals(1, photos.count());
        mvc.perform(get("/api/users/me/photo?userId=" + alice.getId()).header("Authorization", authorization(bob)))
                .andExpect(jsonPath("$.userId").value(bob.getId())).andExpect(jsonPath("$.imageBase64").isEmpty());
        String second = upload(alice, 64, bob.getId().toString()); assertNotEquals(first, second); assertEquals(1, photos.count());
        mvc.perform(put("/api/users/me").header("Authorization", authorization(alice)).contentType("application/json").content("{\"name\":\"Nome preservado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Nome preservado")).andExpect(jsonPath("$.imageData").doesNotExist());
        assertEquals(second, photoService.get(alice.getEmail()).version());
        mvc.perform(get("/api/users/me").header("Authorization", authorization(alice)))
                .andExpect(jsonPath("$.name").value("Nome preservado")).andExpect(jsonPath("$.imageBase64").doesNotExist());
        byte[] header = "--photo-boundary\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"large.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] tail = "\r\n--photo-boundary--\r\n".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream tooLarge = new ByteArrayOutputStream(); tooLarge.write(header); tooLarge.write(new byte[ProfilePhotoService.MAX_BYTES + 512]); tooLarge.write(tail);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/users/me/photo"))
                .timeout(Duration.ofSeconds(15)).header("Authorization", authorization(alice)).header("Content-Type", "multipart/form-data; boundary=photo-boundary")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(tooLarge.toByteArray())).build();
        HttpResponse<String> rejected = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(413, rejected.statusCode()); assertTrue(rejected.body().contains("message")); assertFalse(rejected.body().contains("large.jpg"));
        assertEquals(second, photoService.get(alice.getEmail()).version());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            try { photoService.save(alice.getEmail(), file(16)); } catch (Exception exception) { throw new IllegalStateException(exception); }
            status.setRollbackOnly();
        });
        assertEquals(second, photoService.get(alice.getEmail()).version());
        upload(bob, 32, alice.getId().toString()); upload(admin, 32, alice.getId().toString());
        mvc.perform(delete("/api/users/me/photo?userId=" + bob.getId()).header("Authorization", authorization(alice))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/users/me/photo").header("Authorization", authorization(alice))).andExpect(status().isNoContent());
        assertNull(photoService.get(alice.getEmail()).imageBase64()); assertNotNull(photoService.get(bob.getEmail()).imageBase64()); assertEquals(2, photos.count());
        assertEquals("bytea", sql.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_name='user_profile_photos' AND column_name='image_data'", String.class));
        String adminAuth = authorization(admin);
        CriticalActionRequestDTO reset = new CriticalActionRequestDTO(); reset.setConfirmation("CONFIRM_FULL_RESET"); reset.setReason("QA isolado de fotos");
        adminService.resetSystem(reset, admin.getEmail());
        assertEquals(1, users.count()); assertEquals(1, photos.count()); assertTrue(photos.findByUserId(admin.getId()).isPresent());
        mvc.perform(get("/api/users/me/photo").header("Authorization", adminAuth)).andExpect(status().isUnauthorized());
    }
}
