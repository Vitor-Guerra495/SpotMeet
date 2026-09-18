package com.spotmeet.backend.dto;

import java.time.LocalDateTime;

/**
 * Health and security status of the system and its subsystems (SysAdmin).
 */
public class SystemStatusDTO {

    private String overallStatus;
    private String version;
    private long uptimeSeconds;
    private LocalDateTime timestamp;

    private SubsystemInfo database;
    private SubsystemInfo network;
    private MemoryInfo memory;

    public SystemStatusDTO() {}

    public static class SubsystemInfo {
        private String name;
        private String status; // "ONLINE", "ACTIVE", "WARNING", "FAILURE"
        private String details;

        public SubsystemInfo() {}

        public SubsystemInfo(String name, String status, String details) {
            this.name = name;
            this.status = status;
            this.details = details;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
    }

    public static class MemoryInfo {
        private long totalMb;
        private long usedMb;
        private long freeMb;
        private int processors;

        public MemoryInfo() {}

        public MemoryInfo(long totalMb, long usedMb, long freeMb, int processors) {
            this.totalMb = totalMb;
            this.usedMb = usedMb;
            this.freeMb = freeMb;
            this.processors = processors;
        }

        public long getTotalMb() { return totalMb; }
        public void setTotalMb(long totalMb) { this.totalMb = totalMb; }
        public long getUsedMb() { return usedMb; }
        public void setUsedMb(long usedMb) { this.usedMb = usedMb; }
        public long getFreeMb() { return freeMb; }
        public void setFreeMb(long freeMb) { this.freeMb = freeMb; }
        public int getProcessors() { return processors; }
        public void setProcessors(int processors) { this.processors = processors; }
    }

    // Getters and setters
    public String getOverallStatus() { return overallStatus; }
    public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public SubsystemInfo getDatabase() { return database; }
    public void setDatabase(SubsystemInfo database) { this.database = database; }
    public SubsystemInfo getNetwork() { return network; }
    public void setNetwork(SubsystemInfo network) { this.network = network; }
    public MemoryInfo getMemory() { return memory; }
    public void setMemory(MemoryInfo memory) { this.memory = memory; }
}
