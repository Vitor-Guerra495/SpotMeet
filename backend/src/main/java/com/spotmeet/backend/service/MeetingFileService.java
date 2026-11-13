package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.MeetingResponseDTO.AttachmentDTO;
import com.spotmeet.backend.model.*;
import com.spotmeet.backend.repository.MeetingFileRepository;
import com.spotmeet.backend.repository.MeetingRepository;
import com.spotmeet.backend.repository.MeetingSeriesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Agenda attachment: the president sends the file, then saves the meeting pointing to it.
 *
 * Rules:
 *   - PDF, Word document (DOC or DOCX) or JPG/PNG image, up to 10 MB; the type comes from the content, not from the name.
 *   - Only the president of the committee sends; whoever sees the meeting downloads.
 *   - Files that no meeting uses are deleted after one day.
 */
@Service
public class MeetingFileService {

    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final String INVALID = "Envie um PDF, um documento do Word ou uma imagem JPG ou PNG de até 10 MB.";

    public record Download(String fileName, String contentType, byte[] data) {}

    private record Kind(String contentType, String extension) {}

    @Autowired private MeetingFileRepository fileRepo;
    @Autowired private MeetingRepository meetingRepo;
    @Autowired private MeetingSeriesRepository seriesRepo;
    @Autowired private MeetingAccess access;

    @Transactional
    public AttachmentDTO upload(Long committeeId, String email, MultipartFile upload) {
        Committee committee = access.findCommittee(committeeId);
        User president = access.findUser(email);
        access.requirePresident(president, committee);

        byte[] data = read(upload);
        Kind kind = detect(data);
        fileRepo.deleteUnusedBefore(LocalDateTime.now().minusDays(1));

        MeetingFile file = new MeetingFile();
        file.setCommittee(committee);
        file.setUploadedBy(president);
        file.setFileName(cleanName(upload.getOriginalFilename(), kind.extension()));
        file.setContentType(kind.contentType());
        file.setSizeBytes(data.length);
        file.setData(data);
        MeetingFile saved = fileRepo.save(file);
        return new AttachmentDTO(saved.getId(), saved.getFileName(), saved.getContentType(), saved.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public Download download(Long meetingId, String email) {
        Meeting meeting = access.findMeeting(meetingId);
        User user = access.findUser(email);
        access.requireViewer(user, meeting.getCommittee());
        MeetingFile file = meeting.getAttachment();
        if (file == null) throw new IllegalArgumentException("Esta reunião não tem anexo.");
        return new Download(file.getFileName(), file.getContentType(), file.getData());
    }

    /** File chosen in the form: the one the meeting already has, or one sent by the president for this committee. */
    MeetingFile resolve(Long fileId, Committee committee, User president, MeetingFile current) {
        if (fileId == null) return null;
        if (current != null && current.getId().equals(fileId)) return current;
        MeetingFile file = fileRepo.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("O anexo não foi encontrado. Envie o arquivo de novo."));
        boolean mine = file.getUploadedBy() != null && file.getUploadedBy().getId().equals(president.getId());
        if (!file.getCommittee().getId().equals(committee.getId()) || !mine) {
            throw new SecurityException("Este anexo não pertence a esta comissão.");
        }
        return file;
    }

    /** Deletes a replaced file once no meeting or series points to it. */
    void deleteIfUnused(MeetingFile file) {
        if (file == null) return;
        if (!meetingRepo.existsByAttachmentId(file.getId()) && !seriesRepo.existsByAttachmentId(file.getId())) {
            fileRepo.delete(file);
        }
    }

    private byte[] read(MultipartFile upload) {
        if (upload == null || upload.isEmpty() || upload.getSize() > MAX_BYTES) throw new IllegalArgumentException(INVALID);
        try (InputStream input = upload.getInputStream()) {
            byte[] data = input.readNBytes(MAX_BYTES + 1);
            if (data.length == 0 || data.length > MAX_BYTES) throw new IllegalArgumentException(INVALID);
            return data;
        } catch (IOException exception) {
            throw new IllegalArgumentException(INVALID);
        }
    }

    private static Kind detect(byte[] data) {
        if (startsWith(data, 0x25, 0x50, 0x44, 0x46, 0x2D)) return new Kind("application/pdf", ".pdf");
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) return new Kind("image/jpeg", ".jpg");
        if (startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return new Kind("image/png", ".png");
        if (startsWith(data, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) return new Kind("application/msword", ".doc");
        if (startsWith(data, 0x50, 0x4B, 0x03, 0x04) && isWordDocument(data)) {
            return new Kind("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");
        }
        throw new IllegalArgumentException(INVALID);
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }

    // A DOCX is a ZIP that has word/document.xml inside.
    private static boolean isWordDocument(byte[] data) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if ("word/document.xml".equals(entry.getName())) return true;
            }
            return false;
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    // Keeps the name the user sees, without folders or control characters, and with the extension of the real type.
    private static String cleanName(String original, String extension) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|*?:]", "").trim();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        if (base.isBlank()) base = "anexo";
        String fixedExtension = ".jpg".equals(extension) && name.toLowerCase(Locale.ROOT).endsWith(".jpeg") ? ".jpeg" : extension;
        int room = 150 - fixedExtension.length();
        return (base.length() > room ? base.substring(0, room) : base) + fixedExtension;
    }
}
