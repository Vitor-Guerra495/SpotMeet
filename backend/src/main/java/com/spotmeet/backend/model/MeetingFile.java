package com.spotmeet.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Agenda attachment of a meeting (PDF, Word document or image, up to 10 MB).
 *
 * The president sends the file before saving the meeting; the meetings of a series share the same row.
 * Deleting the committee removes its files in the database.
 */
@Entity
@Table(name = "meeting_files")
public class MeetingFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Committee committee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User uploadedBy;

    @Column(name = "file_name", nullable = false, length = 150)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public MeetingFile() {}

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Committee getCommittee() { return committee; }
    public void setCommittee(Committee committee) { this.committee = committee; }

    public User getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(User uploadedBy) { this.uploadedBy = uploadedBy; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
