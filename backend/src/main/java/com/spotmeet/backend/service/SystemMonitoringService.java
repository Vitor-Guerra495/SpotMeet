package com.spotmeet.backend.service;

import com.spotmeet.backend.dto.SystemDetailsDTO;
import com.spotmeet.backend.dto.SystemDetailsDTO.*;
import com.spotmeet.backend.dto.SystemStatusDTO.MemoryInfo;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class SystemMonitoringService {
    private static final List<String> TABLES = List.of("users", "organizations", "organization_members",
            "access_requests", "organization_invitations", "committees", "committee_members", "audit_logs");
    private static final Set<String> MONITORING_ROUTES = Set.of("/api/admin/system/status",
            "/api/admin/system/details", "/api/admin/reports");
    private static final String DATABASE_SUMMARY_SQL = """
            SELECT pg_database_size(current_database()) AS size_bytes,
              (SELECT count(*) FROM users) AS users,
              (SELECT count(*) FROM organizations) AS organizations,
              (SELECT count(*) FROM organization_members) AS organization_members,
              (SELECT count(*) FROM access_requests) AS access_requests,
              (SELECT count(*) FROM organization_invitations) AS organization_invitations,
              (SELECT count(*) FROM committees) AS committees,
              (SELECT count(*) FROM committee_members) AS committee_members,
              (SELECT count(*) FROM audit_logs) AS audit_logs
            """;

    @Autowired private DataSource dataSource;
    @Autowired private MeterRegistry meterRegistry;
    @Value("${info.app.version:unknown}") private String version;
    private MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private OperatingSystemMXBean operatingSystemBean = ManagementFactory.getOperatingSystemMXBean();

    public String getVersion() {
        return version == null || version.isBlank() || version.startsWith("@") ? "unknown" : version;
    }

    public long getUptimeSeconds() { return ManagementFactory.getRuntimeMXBean().getUptime() / 1000; }

    // Administrative authorization is enforced by AdminService before collection.
    public SystemDetailsDTO collect() { return new SystemDetailsDTO(databaseDetails(), apiDetails(), collectResources()); }

    public MemoryInfo collectResources() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long bytesPerMb = 1024 * 1024;
        MemoryInfo resources = new MemoryInfo(heap.getCommitted() / bytesPerMb, heap.getUsed() / bytesPerMb,
                (heap.getCommitted() - heap.getUsed()) / bytesPerMb, operatingSystemBean.getAvailableProcessors());
        resources.setName("Recursos da JVM");
        resources.setProcessCpuPercent(processCpuPercent());
        if (heap.getMax() <= 0) {
            resources.setStatus("WARNING");
            resources.setDetails("Limite do heap da JVM indisponível para monitoramento.");
            return resources;
        }
        double usage = (double) heap.getUsed() / heap.getMax();
        resources.setMaxMb(heap.getMax() / bytesPerMb);
        resources.setUsagePercent(usage * 100);
        resources.setStatus(usage > 0.90 ? "WARNING" : "ONLINE");
        resources.setDetails(usage > 0.90 ? "Uso do heap acima de 90% do limite da JVM."
                : "Heap da JVM dentro do limite de monitoramento.");
        return resources;
    }

    public String getOverallStatus(String database, String network, String resources) {
        for (String status : new String[]{database, network, resources}) {
            if (!"ONLINE".equals(status) && !"ACTIVE".equals(status)) return "DEGRADED";
        }
        return "OPERATIONAL";
    }

    private Double processCpuPercent() {
        try {
            if (operatingSystemBean instanceof com.sun.management.OperatingSystemMXBean bean) {
                double load = bean.getProcessCpuLoad();
                if (Double.isFinite(load) && load >= 0 && load <= 1) return load * 100;
            }
        } catch (RuntimeException ignored) {
            // CPU sampling may be unsupported on the current platform.
        }
        return null;
    }

    private DatabaseDetails databaseDetails() {
        DatabaseDetails details = new DatabaseDetails();
        details.setStatus("FAILURE");
        details.setDetails("Não foi possível consultar o banco de dados.");
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(3);
                long start = System.nanoTime();
                try (ResultSet result = statement.executeQuery("SELECT 1")) {
                    if (!result.next() || result.getInt(1) != 1) return details;
                }
                details.setResponseTimeMs((System.nanoTime() - start) / 1_000_000.0);
            }
            details.setStatus("WARNING");
            details.setDetails("Conexão ativa, mas os detalhes do banco estão incompletos.");
            details.setName(connection.getCatalog());
            details.setVersion(connection.getMetaData().getDatabaseProductVersion());
            details.setPool(connectionPool());
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(3);
                try (ResultSet result = statement.executeQuery(DATABASE_SUMMARY_SQL)) {
                    if (!result.next()) return details;
                    long size = result.getLong("size_bytes");
                    if (!result.wasNull()) details.setSizeBytes(size);
                    Map<String, Long> rows = new LinkedHashMap<>();
                    for (String table : TABLES) rows.put(table, result.getLong(table));
                    details.setTableRows(rows);
                }
            }
            details.setStatus("ONLINE");
            details.setDetails("Conexão e consultas de leitura concluídas.");
        } catch (SQLException | RuntimeException ignored) {
            // Keep unavailable values null and never expose connection errors.
        }
        return details;
    }

    private ConnectionPool connectionPool() {
        try {
            if (!dataSource.isWrapperFor(HikariDataSource.class)) return null;
            HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool == null) return null;
            return new ConnectionPool(pool.getActiveConnections(), pool.getIdleConnections(),
                    pool.getThreadsAwaitingConnection(), pool.getTotalConnections(), hikari.getMaximumPoolSize());
        } catch (SQLException | RuntimeException ignored) { return null; }
    }

    private ApiDetails apiDetails() {
        ApiDetails details = new ApiDetails();
        details.setVersion(getVersion());
        details.setCountersSince(Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()));
        details.setUptimeSeconds(getUptimeSeconds());
        details.setJvmThreads(ManagementFactory.getThreadMXBean().getThreadCount());
        Collection<Timer> timers = meterRegistry.find("http.server.requests").timers();
        details.setRequestMetricsAvailable(!timers.isEmpty());
        long total = 0, clientErrors = 0, serverErrors = 0;
        double timeMs = 0;
        Map<String, RouteDetails> routes = new HashMap<>();
        for (Timer timer : timers) {
            long count = timer.count();
            double duration = timer.totalTime(TimeUnit.MILLISECONDS);
            total += count;
            timeMs += duration;
            String status = timer.getId().getTag("status");
            if (status != null && status.length() == 3) {
                if (status.startsWith("4")) clientErrors += count;
                if (status.startsWith("5")) serverErrors += count;
            }
            String route = timer.getId().getTag("uri");
            String method = timer.getId().getTag("method");
            if (count > 0 && route != null && route.startsWith("/api/") && !MONITORING_ROUTES.contains(route)
                    && !route.contains("?") && !route.contains("@") && method != null) {
                routes.computeIfAbsent(method + " " + route, key -> new RouteDetails(method, route)).add(count, duration);
            }
        }
        details.setTotalRequests(total);
        details.setAverageResponseTimeMs(total == 0 ? null : timeMs / total);
        details.setClientErrors(clientErrors);
        details.setServerErrors(serverErrors);
        details.setTopRoutes(routes.values().stream().sorted(Comparator.comparingLong(RouteDetails::getRequests).reversed()
                .thenComparing(RouteDetails::getMethod).thenComparing(RouteDetails::getRoute)).limit(3).toList());
        Collection<LongTaskTimer> active = meterRegistry.find("http.server.requests.active").longTaskTimers();
        details.setActiveRequests(active.isEmpty() ? null : active.stream().mapToLong(LongTaskTimer::activeTasks).sum());
        details.setTomcatCurrentThreads(gaugeCount("tomcat.threads.current"));
        details.setTomcatBusyThreads(gaugeCount("tomcat.threads.busy"));
        return details;
    }

    private Integer gaugeCount(String name) {
        Collection<Gauge> gauges = meterRegistry.find(name).gauges();
        if (gauges.isEmpty()) return null;
        double count = 0;
        for (Gauge gauge : gauges) {
            double value = gauge.value();
            if (!Double.isFinite(value) || value < 0) return null;
            count += value;
        }
        return count > Integer.MAX_VALUE ? null : (int) count;
    }
}
