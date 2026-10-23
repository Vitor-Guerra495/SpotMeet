package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.ProfilePhotoResponseDTO;
import com.spotmeet.backend.exception.InvalidProfilePhotoException;
import com.spotmeet.backend.model.AuditLog;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.model.UserProfilePhoto;
import com.spotmeet.backend.repository.AuditLogRepository;
import com.spotmeet.backend.repository.UserProfilePhotoRepository;
import com.spotmeet.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProfilePhotoService {
    public static final int MAX_BYTES = 1024 * 1024;
    private final UserRepository users;
    private final UserProfilePhotoRepository photos;
    private final AuditLogRepository audit;

    public ProfilePhotoService(UserRepository users, UserProfilePhotoRepository photos, AuditLogRepository audit) {
        this.users = users; this.photos = photos; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ProfilePhotoResponseDTO get(String email) {
        User user = currentUser(email, false);
        return response(user, photos.findByUserId(user.getId()).orElse(null));
    }

    @Transactional
    public ProfilePhotoResponseDTO save(String email, MultipartFile file) {
        User user = currentUser(email, true);
        byte[] image = normalizeImage(file);
        UserProfilePhoto photo = photos.findByUserId(user.getId()).orElseGet(UserProfilePhoto::new);
        photo.setUser(user); photo.setImageData(image); photo.setVersion(UUID.randomUUID().toString());
        photos.save(photo);
        audit.save(new AuditLog(user, "PROFILE_PHOTO_UPDATED", "Foto de perfil alterada."));
        return response(user, photo);
    }

    @Transactional
    public void remove(String email) {
        User user = currentUser(email, true);
        photos.findByUserId(user.getId()).ifPresent(photo -> {
            photos.delete(photo);
            audit.save(new AuditLog(user, "PROFILE_PHOTO_REMOVED", "Foto de perfil removida."));
        });
    }

    private User currentUser(String email, boolean write) {
        if (email == null || email.isBlank()) throw new SecurityException("Usuário não autenticado.");
        User user = (write ? users.findByEmailForUpdate(email) : users.findByEmail(email))
                .filter(User::isEmailVerified).orElseThrow(() -> new SecurityException("Usuário não autenticado."));
        return user;
    }

    private ProfilePhotoResponseDTO response(User user, UserProfilePhoto photo) {
        return new ProfilePhotoResponseDTO(user.getId(), photo == null ? null : Base64.getEncoder().encodeToString(photo.getImageData()),
                photo == null ? null : photo.getVersion());
    }

    private byte[] normalizeImage(MultipartFile file) {
        String invalid = "Envie uma foto JPEG ou PNG quadrada, com até 512 × 512 pixels e 1 MB.";
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw new InvalidProfilePhotoException(invalid);
        String type = file.getContentType();
        if (!"image/jpeg".equals(type) && !"image/png".equals(type)) throw new InvalidProfilePhotoException(invalid);
        try (InputStream source = file.getInputStream()) {
            byte[] data = source.readNBytes(MAX_BYTES + 1);
            if (data.length == 0 || data.length > MAX_BYTES) throw new InvalidProfilePhotoException(invalid);
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new InvalidProfilePhotoException(invalid);
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!("jpeg".equals(format) && "image/jpeg".equals(type))
                            && !("png".equals(format) && "image/png".equals(type))) throw new InvalidProfilePhotoException(invalid);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    // Check dimensions before decoding compressed pixels.
                    if (width < 1 || width > 512 || width != height) throw new InvalidProfilePhotoException(invalid);
                    reader.addIIOReadWarningListener((ignored, warning) -> { throw new InvalidProfilePhotoException(invalid); });
                    BufferedImage decoded = reader.read(0);
                    if (decoded == null) throw new InvalidProfilePhotoException(invalid);
                    BufferedImage clean = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = clean.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
                        graphics.drawImage(decoded, 0, 0, null);
                    } finally { graphics.dispose(); decoded.flush(); }
                    try (var output = new ByteArrayOutputStream()) {
                        // A new JPEG drops source metadata and appended content.
                        if (!ImageIO.write(clean, "jpeg", output) || output.size() > MAX_BYTES) throw new InvalidProfilePhotoException(invalid);
                        return output.toByteArray();
                    } finally { clean.flush(); }
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new InvalidProfilePhotoException(invalid);
        }
    }
}
