package com.spotmeet.backend.dto;

import com.spotmeet.backend.dto.SystemStatusDTO.MemoryInfo;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class SystemDetailsDTO {
    private final Instant timestamp;
    private final DatabaseDetails database;
    private final ApiDetails api;
    private final MemoryInfo resources;

    public SystemDetailsDTO(DatabaseDetails database, ApiDetails api, MemoryInfo resources) {
        this.timestamp = Instant.now();
        this.database = database;
        this.api = api;
        this.resources = resources;
    }
    public Instant getTimestamp() { return timestamp; }
    public DatabaseDetails getDatabase() { return database; }
    public ApiDetails getApi() { return api; }
    public MemoryInfo getResources() { return resources; }

    public static class DatabaseDetails {
        private String status;
        private String details;
        private String name;
        private String version;
        private Long sizeBytes;
        private Double responseTimeMs;
        private ConnectionPool pool;
        private Map<String, Long> tableRows;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public Long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
        public Double getResponseTimeMs() { return responseTimeMs; }
        public void setResponseTimeMs(Double value) { this.responseTimeMs = value; }
        public ConnectionPool getPool() { return pool; }
        public void setPool(ConnectionPool pool) { this.pool = pool; }
        public Map<String, Long> getTableRows() { return tableRows; }
        public void setTableRows(Map<String, Long> tableRows) { this.tableRows = tableRows; }
    }

    public static class ConnectionPool {
        private final int active;
        private final int idle;
        private final int waiting;
        private final int total;
        private final int maximum;

        public ConnectionPool(int active, int idle, int waiting, int total, int maximum) {
            this.active = active; this.idle = idle; this.waiting = waiting;
            this.total = total; this.maximum = maximum;
        }
        public int getActive() { return active; }
        public int getIdle() { return idle; }
        public int getWaiting() { return waiting; }
        public int getTotal() { return total; }
        public int getMaximum() { return maximum; }
    }

    public static class ApiDetails {
        private String version;
        private Instant countersSince;
        private long uptimeSeconds;
        private boolean requestMetricsAvailable;
        private long totalRequests;
        private Double averageResponseTimeMs;
        private long clientErrors;
        private long serverErrors;
        private Long activeRequests;
        private int jvmThreads;
        private Integer tomcatCurrentThreads;
        private Integer tomcatBusyThreads;
        private List<RouteDetails> topRoutes;

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public Instant getCountersSince() { return countersSince; }
        public void setCountersSince(Instant countersSince) { this.countersSince = countersSince; }
        public long getUptimeSeconds() { return uptimeSeconds; }
        public void setUptimeSeconds(long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }
        public boolean isRequestMetricsAvailable() { return requestMetricsAvailable; }
        public void setRequestMetricsAvailable(boolean value) { this.requestMetricsAvailable = value; }
        public long getTotalRequests() { return totalRequests; }
        public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
        public Double getAverageResponseTimeMs() { return averageResponseTimeMs; }
        public void setAverageResponseTimeMs(Double value) { this.averageResponseTimeMs = value; }
        public long getClientErrors() { return clientErrors; }
        public void setClientErrors(long clientErrors) { this.clientErrors = clientErrors; }
        public long getServerErrors() { return serverErrors; }
        public void setServerErrors(long serverErrors) { this.serverErrors = serverErrors; }
        public Long getActiveRequests() { return activeRequests; }
        public void setActiveRequests(Long activeRequests) { this.activeRequests = activeRequests; }
        public int getJvmThreads() { return jvmThreads; }
        public void setJvmThreads(int jvmThreads) { this.jvmThreads = jvmThreads; }
        public Integer getTomcatCurrentThreads() { return tomcatCurrentThreads; }
        public void setTomcatCurrentThreads(Integer value) { this.tomcatCurrentThreads = value; }
        public Integer getTomcatBusyThreads() { return tomcatBusyThreads; }
        public void setTomcatBusyThreads(Integer value) { this.tomcatBusyThreads = value; }
        public List<RouteDetails> getTopRoutes() { return topRoutes; }
        public void setTopRoutes(List<RouteDetails> topRoutes) { this.topRoutes = topRoutes; }
    }

    public static class RouteDetails {
        private final String method;
        private final String route;
        private long requests;
        private double totalTimeMs;

        public RouteDetails(String method, String route) { this.method = method; this.route = route; }
        public void add(long requests, double totalTimeMs) { this.requests += requests; this.totalTimeMs += totalTimeMs; }
        public String getMethod() { return method; }
        public String getRoute() { return route; }
        public long getRequests() { return requests; }
        public Double getAverageResponseTimeMs() { return requests == 0 ? null : totalTimeMs / requests; }
    }
}
