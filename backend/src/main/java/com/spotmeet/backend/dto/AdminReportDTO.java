package com.spotmeet.backend.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Aggregated system report with LGPD masking.
 */
public class AdminReportDTO {

    private long totalUsers;
    private long verifiedUsers;
    private Map<String, Long> usersByRole;

    private long totalOrganizations;
    private long approvedOrganizations;
    private long pendingOrganizations;

    private long totalCommittees;
    private long totalAuditLogs;

    private List<AuditActivityDTO> recentActivities;
    private LocalDateTime generatedAt;

    public AdminReportDTO() {}

    public static class AuditActivityDTO {
        private Long id;
        private String action;
        private String executorName;
        private String executorEmailMasked;
        private String sanitizedDetails;
        private LocalDateTime createdAt;

        public AuditActivityDTO() {}

        public AuditActivityDTO(Long id, String action, String executorName,
                                String executorEmailMasked, String sanitizedDetails,
                                LocalDateTime createdAt) {
            this.id = id;
            this.action = action;
            this.executorName = executorName;
            this.executorEmailMasked = executorEmailMasked;
            this.sanitizedDetails = sanitizedDetails;
            this.createdAt = createdAt;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public String getExecutorName() { return executorName; }
        public void setExecutorName(String executorName) { this.executorName = executorName; }
        public String getExecutorEmailMasked() { return executorEmailMasked; }
        public void setExecutorEmailMasked(String executorEmailMasked) { this.executorEmailMasked = executorEmailMasked; }
        public String getSanitizedDetails() { return sanitizedDetails; }
        public void setSanitizedDetails(String sanitizedDetails) { this.sanitizedDetails = sanitizedDetails; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

    // Getters and setters
    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }
    public long getVerifiedUsers() { return verifiedUsers; }
    public void setVerifiedUsers(long verifiedUsers) { this.verifiedUsers = verifiedUsers; }
    public Map<String, Long> getUsersByRole() { return usersByRole; }
    public void setUsersByRole(Map<String, Long> usersByRole) { this.usersByRole = usersByRole; }
    public long getTotalOrganizations() { return totalOrganizations; }
    public void setTotalOrganizations(long totalOrganizations) { this.totalOrganizations = totalOrganizations; }
    public long getApprovedOrganizations() { return approvedOrganizations; }
    public void setApprovedOrganizations(long approvedOrganizations) { this.approvedOrganizations = approvedOrganizations; }
    public long getPendingOrganizations() { return pendingOrganizations; }
    public void setPendingOrganizations(long pendingOrganizations) { this.pendingOrganizations = pendingOrganizations; }
    public long getTotalCommittees() { return totalCommittees; }
    public void setTotalCommittees(long totalCommittees) { this.totalCommittees = totalCommittees; }
    public long getTotalAuditLogs() { return totalAuditLogs; }
    public void setTotalAuditLogs(long totalAuditLogs) { this.totalAuditLogs = totalAuditLogs; }
    public List<AuditActivityDTO> getRecentActivities() { return recentActivities; }
    public void setRecentActivities(List<AuditActivityDTO> recentActivities) { this.recentActivities = recentActivities; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
