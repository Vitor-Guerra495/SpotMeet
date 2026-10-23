package com.spotmeet.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.controller.ProfilePhotoController;
import com.spotmeet.backend.exception.GlobalExceptionHandler;
import com.spotmeet.backend.exception.InvalidProfilePhotoException;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.model.UserProfilePhoto;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.UserProfilePhotoRepository;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.service.ProfilePhotoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProfilePhotoTests {
    private User alice, bob;
    private UserRepository users;
    private UserProfilePhotoRepository photos;
    private AuditLogRepository audit;
    private ProfilePhotoService service;
    private MockMvc mvc;
    private final Map<Long, UserProfilePhoto> stored = new HashMap<>();

    @BeforeEach void setUp() {
        alice = user(1, "alice"); bob = user(2, "bob");
        users = mock(UserRepository.class); photos = mock(UserProfilePhotoRepository.class); audit = mock(AuditLogRepository.class);
        when(users.findByEmail(anyString())).thenAnswer(call -> List.of(alice, bob).stream().filter(user -> user.getEmail().equals(call.getArgument(0))).findFirst());
        when(users.findByEmailForUpdate(anyString())).thenAnswer(call -> users.findByEmail(call.getArgument(0)));
        when(photos.findByUserId(anyLong())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
        when(photos.save(any())).thenAnswer(call -> { UserProfilePhoto photo = call.getArgument(0); stored.put(photo.getUserId(), photo); return photo; });
        doAnswer(call -> { stored.remove(((UserProfilePhoto) call.getArgument(0)).getUserId()); return null; }).when(photos).delete(any());
        service = new ProfilePhotoService(users, photos, audit);
        mvc = MockMvcBuilders.standaloneSetup(new ProfilePhotoController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private User user(long id, String name) {
        User user = new User(); user.setId(id); user.setName(name); user.setEmail(name + "@example.com"); user.setEmailVerified(true); return user;
    }
    private UsernamePasswordAuthenticationToken principal(User user) { return new UsernamePasswordAuthenticationToken(user.getEmail(), null, user.getAuthorities()); }
    private byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        try (var out = new ByteArrayOutputStream()) { assertTrue(ImageIO.write(image, format, out)); return out.toByteArray(); }
        finally { image.flush(); }
    }
    private MockMultipartFile file(String format, int size) throws IOException {
        return new MockMultipartFile("photo", "profile." + format, "image/" + format, image(format, size, size));
    }
    private void rejected(MockMultipartFile file) {
        assertThrows(InvalidProfilePhotoException.class, () -> service.save(alice.getEmail(), file));
        verify(photos, never()).save(any()); verifyNoInteractions(audit);
    }

    @Test void noPhotoIsAnExplicitPrivateEmptyResponse() throws Exception {
        mvc.perform(get("/api/users/me/photo").principal(principal(alice)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.userId").value(1)).andExpect(jsonPath("$.imageBase64").isEmpty()).andExpect(jsonPath("$.version").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"jpeg", "png"})
    void storesOnlyAReencodedJpegForTheAuthenticatedAccount(String format) throws Exception {
        var result = service.save(alice.getEmail(), file(format, 32));
        byte[] bytes = Base64.getDecoder().decode(result.imageBase64());
        assertEquals(0xff, bytes[0] & 255); assertEquals(0xd8, bytes[1] & 255);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertEquals(32, decoded.getWidth()); assertEquals(32, decoded.getHeight());
        assertEquals(1L, result.userId()); assertEquals(36, result.version().length());
        assertEquals(Set.of(1L), stored.keySet()); assertEquals("USER", alice.getRole());
        verify(users).findByEmailForUpdate(alice.getEmail()); verify(audit).save(any());
    }
    @ParameterizedTest @ValueSource(ints = {1, 512})
    void acceptsBothDimensionBoundaries(int size) throws Exception { assertNotNull(service.save(alice.getEmail(), file("png", size)).imageBase64()); }
    @Test void replacementChangesVersionAndKeepsOneRow() throws Exception {
        String first = service.save(alice.getEmail(), file("jpeg", 8)).version();
        String second = service.save(alice.getEmail(), file("png", 16)).version();
        assertNotEquals(first, second); assertEquals(1, stored.size()); assertEquals(second, service.get(alice.getEmail()).version());
    }
    @Test void removeIsIdempotentAndCannotDeleteAnotherAccountsPhoto() throws Exception {
        service.save(alice.getEmail(), file("jpeg", 8)); service.save(bob.getEmail(), file("jpeg", 16));
        service.remove(alice.getEmail()); service.remove(alice.getEmail());
        assertNull(service.get(alice.getEmail()).imageBase64()); assertNotNull(service.get(bob.getEmail()).imageBase64()); assertEquals(Set.of(2L), stored.keySet());
    }
    @Test void declaredIdentifiersAndPathsDoNotSelectTheAccountOrFileLocation() throws Exception {
        var upload = new MockMultipartFile("photo", "../../private-image.jpg", "image/jpeg", image("jpeg", 16, 16));
        mvc.perform(multipart("/api/users/me/photo").file(upload).param("userId", "2").param("role", "SYSADMIN")
                        .with(request -> { request.setMethod("PUT"); return request; }).principal(principal(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(1)).andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.user").doesNotExist());
        mvc.perform(get("/api/users/me/photo").param("userId", "1").principal(principal(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(2)).andExpect(jsonPath("$.imageBase64").isEmpty());
        mvc.perform(delete("/api/users/me/photo").param("userId", "1").principal(principal(bob))).andExpect(status().isNoContent());
        assertNotNull(service.get(alice.getEmail()).imageBase64()); assertEquals("USER", alice.getRole());
    }
    @Test void transportAndSerializationDoNotExposeMetadataOrAppendedText() throws Exception {
        byte[] valid = image("jpeg", 8, 8), suffix = "<script>private-metadata</script>".getBytes(StandardCharsets.UTF_8);
        byte[] withSuffix = Arrays.copyOf(valid, valid.length + suffix.length); System.arraycopy(suffix, 0, withSuffix, valid.length, suffix.length);
        var result = service.save(alice.getEmail(), new MockMultipartFile("photo", "ignored.jpg", "image/jpeg", withSuffix));
        assertFalse(new String(Base64.getDecoder().decode(result.imageBase64()), StandardCharsets.ISO_8859_1).contains("private-metadata"));
        UserProfilePhoto photo = stored.get(1L);
        String json = new ObjectMapper().writeValueAsString(photo);
        assertFalse(json.contains("imageData")); assertFalse(json.contains("email")); assertFalse(json.contains("password"));
    }
    @ParameterizedTest @ValueSource(strings = {"image/svg+xml", "text/html", "image/gif", "application/octet-stream", "image/webp"})
    void rejectsUnsupportedDeclaredFormats(String type) throws Exception {
        rejected(new MockMultipartFile("photo", "fake.jpg", type, image("jpeg", 8, 8)));
    }
    @Test void rejectsADeclaredMimeThatDoesNotMatchDecodedContent() throws Exception {
        rejected(new MockMultipartFile("photo", "fake.jpg", "image/jpeg", image("png", 8, 8)));
    }
    @ParameterizedTest @ValueSource(strings = {"", "<svg onload=alert(1)>", "<script>alert(1)</script>", "SELECT * FROM users", "not an image"})
    void rejectsTextDisguisedAsAnImage(String text) {
        rejected(new MockMultipartFile("photo", "fake.jpg", "image/jpeg", text.getBytes(StandardCharsets.UTF_8)));
    }
    @ParameterizedTest @ValueSource(ints = {513, 2048})
    void checksCompressedDimensionsBeforeDecodingPixels(int size) throws Exception { rejected(file("png", size)); }
    @Test void rejectsANonSquareImage() throws Exception {
        rejected(new MockMultipartFile("photo", "wide.png", "image/png", image("png", 20, 10)));
    }
    @Test void rejectsANullUpload() { assertThrows(InvalidProfilePhotoException.class, () -> service.save(alice.getEmail(), null)); verify(photos, never()).save(any()); }
    @Test void rejectsAnOversizedUpload() { rejected(new MockMultipartFile("photo", "big.jpg", "image/jpeg", new byte[ProfilePhotoService.MAX_BYTES + 1])); }
    @Test void boundsTheStreamEvenIfTheDeclaredSizeIsFalse() {
        rejected(new MockMultipartFile("photo", "big.jpg", "image/jpeg", new byte[ProfilePhotoService.MAX_BYTES + 9]) {
            @Override public long getSize() { return 1; }
        });
    }
    @Test void rejectsUnreadableAndTruncatedImages() throws Exception {
        rejected(new MockMultipartFile("photo", "broken.jpg", "image/jpeg", new byte[]{(byte) 255, (byte) 216, (byte) 255}));
    }
    @Test void readErrorsDoNotExposeFilesystemDetails() throws Exception {
        var upload = new MockMultipartFile("photo", "private.jpg", "image/jpeg", new byte[]{1}) {
            @Override public InputStream getInputStream() throws IOException { throw new IOException("private filesystem detail"); }
        };
        mvc.perform(multipart("/api/users/me/photo").file(upload).with(request -> { request.setMethod("PUT"); return request; }).principal(principal(alice)))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("filesystem"))));
    }
    @Test void missingAndUnverifiedAccountsCannotReadOrWrite() throws Exception {
        alice.setEmailVerified(false);
        for (String email : new String[]{alice.getEmail(), "missing@example.com", null, ""}) {
            assertThrows(SecurityException.class, () -> service.get(email));
            assertThrows(SecurityException.class, () -> service.save(email, null));
            assertThrows(SecurityException.class, () -> service.remove(email));
        }
        verifyNoInteractions(photos, audit);
    }
    @Test void controllerRequiresAuthenticationAndAnUploadPart() throws Exception {
        mvc.perform(get("/api/users/me/photo")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/users/me/photo")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/users/me/photo").with(request -> { request.setMethod("PUT"); return request; }).principal(principal(alice)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(photos, audit);
    }
    @Test void multipartLimitErrorsReturnGenericJson() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        assertEquals(413, handler.handleUploadSize(new MaxUploadSizeExceededException(1)).getStatusCode().value());
        var response = handler.handleMultipart(new MultipartException("private upload location"));
        assertEquals(400, response.getStatusCode().value()); assertFalse(response.getBody().get("message").contains("location"));
    }
    @Test void unexpectedRepositoryErrorsHaveAGenericResponse() throws Exception {
        when(photos.findByUserId(1L)).thenThrow(new IllegalStateException("private JDBC detail"));
        mvc.perform(get("/api/users/me/photo").principal(principal(alice))).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("JDBC"))));
    }
    @Test void uploadRequiresMultipartInsteadOfBindingAnAccountFromJson() throws Exception {
        mvc.perform(put("/api/users/me/photo").principal(principal(alice)).contentType("application/json")
                        .content("{\"userId\":2,\"imageBase64\":\"private\"}"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(content().contentTypeCompatibleWith("application/json"));
        verifyNoInteractions(photos, audit);
    }
    @Test void photoDataCannotNegotiateAnHtmlResponse() throws Exception {
        mvc.perform(get("/api/users/me/photo").principal(principal(alice)).accept("text/html"))
                .andExpect(status().isNotAcceptable()).andExpect(content().contentTypeCompatibleWith("application/json"));
        verifyNoInteractions(photos, audit);
    }
}
