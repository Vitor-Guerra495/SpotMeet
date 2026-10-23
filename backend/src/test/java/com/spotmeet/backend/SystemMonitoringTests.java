package com.spotmeet.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.controller.AdminController;
import com.spotmeet.backend.dto.SystemDetailsDTO;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.service.AdminService;
import com.spotmeet.backend.service.SystemMonitoringService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SystemMonitoringTests {
    private SystemMonitoringService monitoring;
    private SimpleMeterRegistry registry;
    private DataSource source;
    private Connection connection;
    private DatabaseMetaData metadata;
    private Statement probe;
    private Statement summary;
    private ResultSet probeResult;
    private ResultSet summaryResult;
    private AdminService admin;
    private UserRepository users;
    private User executor;
    private MockMvc mvc;

    @BeforeEach void setUp() throws Exception {
        registry = new SimpleMeterRegistry();
        source = mock(DataSource.class);
        connection = mock(Connection.class);
        metadata = mock(DatabaseMetaData.class);
        probe = mock(Statement.class); summary = mock(Statement.class);
        probeResult = mock(ResultSet.class); summaryResult = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        var statementIndex = new java.util.concurrent.atomic.AtomicInteger();
        when(connection.createStatement()).thenAnswer(call -> statementIndex.getAndIncrement() % 2 == 0 ? probe : summary);
        when(connection.getCatalog()).thenReturn("qa_monitoring");
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductVersion()).thenReturn("18.0");
        when(probe.executeQuery("SELECT 1")).thenReturn(probeResult);
        when(probeResult.next()).thenReturn(true); when(probeResult.getInt(1)).thenReturn(1);
        when(summary.executeQuery(anyString())).thenReturn(summaryResult);
        when(summaryResult.next()).thenReturn(true);
        Map<String, Long> values = Map.of("size_bytes", 123456L, "users", 3L, "organizations", 2L,
                "organization_members", 4L, "access_requests", 1L, "organization_invitations", 2L,
                "committees", 1L, "committee_members", 3L, "audit_logs", 9L);
        when(summaryResult.getLong(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        monitoring = new SystemMonitoringService();
        ReflectionTestUtils.setField(monitoring, "dataSource", source);
        ReflectionTestUtils.setField(monitoring, "meterRegistry", registry);
        ReflectionTestUtils.setField(monitoring, "version", "0.0.1-SNAPSHOT");
        users = mock(UserRepository.class);
        executor = new User(); executor.setId(1L); executor.setEmail("admin@example.com"); executor.setRole("SYSADMIN"); executor.setEmailVerified(true);
        when(users.findByEmail(executor.getEmail())).thenReturn(Optional.of(executor));
        admin = new AdminService();
        ReflectionTestUtils.setField(admin, "userRepo", users);
        ReflectionTestUtils.setField(admin, "monitoringService", monitoring);
        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "adminService", admin);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }
    @AfterEach void closeRegistry() { registry.close(); }
    private Timer timer(String method, String route, String status, double... durations) {
        Timer timer = Timer.builder("http.server.requests").tags("method", method, "uri", route, "status", status).register(registry);
        for (double duration : durations) timer.record((long) (duration * 1_000_000), TimeUnit.NANOSECONDS);
        return timer;
    }
    private UsernamePasswordAuthenticationToken identity() {
        return new UsernamePasswordAuthenticationToken(executor.getEmail(), "unused", List.of());
    }

    @Test void databaseDetailsComeFromMetadataAndReadQueriesOnly() throws Exception {
        var result = monitoring.collect().getDatabase();
        assertEquals("ONLINE", result.getStatus()); assertEquals("qa_monitoring", result.getName());
        assertEquals("18.0", result.getVersion()); assertEquals(123456L, result.getSizeBytes());
        assertEquals(8, result.getTableRows().size()); assertEquals(2L, result.getTableRows().get("organization_invitations"));
        assertNotNull(result.getResponseTimeMs()); assertTrue(result.getResponseTimeMs() >= 0);
        verify(connection).setReadOnly(true); verify(connection).close();
        verify(probe).setQueryTimeout(3); verify(summary).setQueryTimeout(3);
        verify(probe).close(); verify(summary).close(); verify(probeResult).close(); verify(summaryResult).close();
        verify(connection, never()).commit(); verify(summary, never()).executeUpdate(anyString());
    }
    @Test void databaseCredentialsAndConnectionUrlAreNeverRead() throws Exception {
        var result = monitoring.collect();
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);
        verify(metadata, never()).getURL(); verify(metadata, never()).getUserName();
        for (String text : List.of("password", "token", "jwt", "email", "jdbc:", "username")) assertFalse(json.contains(text));
    }
    @Test void missingHikariPoolDoesNotInventZeroConnections() {
        var result = monitoring.collect().getDatabase();
        assertEquals("ONLINE", result.getStatus()); assertNull(result.getPool());
    }
    @Test void hikariPoolReportsItsRealMeasurements() throws Exception {
        HikariDataSource hikari = mock(HikariDataSource.class); HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
        when(source.isWrapperFor(HikariDataSource.class)).thenReturn(true);
        when(source.unwrap(HikariDataSource.class)).thenReturn(hikari);
        when(hikari.getHikariPoolMXBean()).thenReturn(pool); when(hikari.getMaximumPoolSize()).thenReturn(10);
        when(pool.getActiveConnections()).thenReturn(2); when(pool.getIdleConnections()).thenReturn(4);
        when(pool.getThreadsAwaitingConnection()).thenReturn(1); when(pool.getTotalConnections()).thenReturn(6);
        var result = monitoring.collect().getDatabase().getPool();
        assertEquals(2, result.getActive()); assertEquals(4, result.getIdle()); assertEquals(1, result.getWaiting());
        assertEquals(6, result.getTotal()); assertEquals(10, result.getMaximum());
    }
    @Test void unavailablePoolDoesNotHideOtherDatabaseDetails() throws Exception {
        when(source.isWrapperFor(HikariDataSource.class)).thenThrow(new SQLException("private detail"));
        var result = monitoring.collect().getDatabase();
        assertEquals("ONLINE", result.getStatus()); assertNull(result.getPool()); assertNotNull(result.getSizeBytes());
    }
    @Test void connectionFailureReturnsUnavailableValuesAndKeepsApiMetrics() throws Exception {
        when(source.getConnection()).thenThrow(new SQLException("password and jdbc private detail"));
        timer("GET", "/api/users/me", "200", 10);
        var result = monitoring.collect();
        assertEquals("FAILURE", result.getDatabase().getStatus()); assertNull(result.getDatabase().getSizeBytes());
        assertNull(result.getDatabase().getResponseTimeMs()); assertNull(result.getDatabase().getTableRows());
        assertEquals(1, result.getApi().getTotalRequests());
        assertFalse(new ObjectMapper().findAndRegisterModules().writeValueAsString(result).contains("private detail"));
    }
    @Test void queryTimeoutPreservesProbeButDoesNotClaimFullDatabaseSuccess() throws Exception {
        when(summary.executeQuery(anyString())).thenThrow(new SQLTimeoutException("internal SQL"));
        var result = monitoring.collect().getDatabase();
        assertEquals("WARNING", result.getStatus()); assertEquals("18.0", result.getVersion());
        assertNotNull(result.getResponseTimeMs()); assertNull(result.getSizeBytes()); assertNull(result.getTableRows());
        verify(connection).close(); assertFalse(result.getDetails().contains("internal SQL"));
    }
    @Test void invalidProbeDoesNotPublishAMeasurement() throws Exception {
        when(probeResult.getInt(1)).thenReturn(0);
        var result = monitoring.collect().getDatabase();
        assertEquals("FAILURE", result.getStatus()); assertNull(result.getResponseTimeMs()); verify(summary, never()).executeQuery(anyString());
    }
    @Test void emptySummaryResultIsReportedAsIncomplete() throws Exception {
        when(summaryResult.next()).thenReturn(false);
        var result = monitoring.collect().getDatabase();
        assertEquals("WARNING", result.getStatus()); assertNull(result.getSizeBytes()); assertNull(result.getTableRows());
    }
    @Test void weightedAverageUsesAllSamplesRatherThanAnAverageOfRoutes() {
        timer("GET", "/api/users/me", "200", 100, 100, 100);
        timer("POST", "/api/organizations", "201", 400);
        var result = monitoring.collect().getApi();
        assertEquals(4, result.getTotalRequests()); assertEquals(175.0, result.getAverageResponseTimeMs(), 0.001);
        assertTrue(result.isRequestMetricsAvailable()); assertEquals(0, result.getClientErrors()); assertEquals(0, result.getServerErrors());
    }
    @Test void httpErrorsAreCountedByStatusWithoutExposingExceptionTags() throws Exception {
        timer("GET", "/api/users/me", "401", 10, 20); timer("POST", "/api/organizations", "409", 30);
        timer("GET", "/api/admin/reports", "500", 40); timer("GET", "/api/users/me", "503", 50, 60);
        var result = monitoring.collect().getApi();
        assertEquals(6, result.getTotalRequests()); assertEquals(3, result.getClientErrors()); assertEquals(3, result.getServerErrors());
        assertEquals(35.0, result.getAverageResponseTimeMs(), 0.001);
    }
    @Test void topThreeRoutesGroupStatusesButKeepMethodsSeparate() {
        timer("GET", "/api/organizations/{organizationId}/members", "200", 10, 20);
        timer("GET", "/api/organizations/{organizationId}/members", "403", 30);
        timer("POST", "/api/organizations", "201", 100, 100);
        timer("GET", "/api/users/me", "200", 50);
        timer("GET", "/api/organizations", "200", 60);
        var routes = monitoring.collect().getApi().getTopRoutes();
        assertEquals(3, routes.size()); assertEquals(3, routes.get(0).getRequests());
        assertEquals("/api/organizations/{organizationId}/members", routes.get(0).getRoute());
        assertEquals(20.0, routes.get(0).getAverageResponseTimeMs(), 0.001);
        assertEquals("POST", routes.get(1).getMethod()); assertEquals("/api/organizations", routes.get(2).getRoute());
    }
    @Test void monitoringAndActuatorCannotDominateTheBusinessRouteRanking() {
        for (String route : List.of("/api/admin/system/status", "/api/admin/system/details", "/api/admin/reports", "/actuator/info")) {
            timer("GET", route, "200", 10, 10, 10);
        }
        timer("GET", "/api/users/me", "200", 20);
        var result = monitoring.collect().getApi();
        assertEquals(13, result.getTotalRequests()); assertEquals(1, result.getTopRoutes().size());
        assertEquals("/api/users/me", result.getTopRoutes().get(0).getRoute());
    }
    @Test void unknownRoutesQueryStringsAndAddressesAreNotPublished() {
        for (String route : List.of("UNKNOWN", "NOT_FOUND", "/api/users/me?token=private", "/api/private@example.com")) timer("GET", route, "404", 5);
        var result = monitoring.collect().getApi();
        assertEquals(4, result.getTotalRequests()); assertEquals(4, result.getClientErrors()); assertTrue(result.getTopRoutes().isEmpty());
    }
    @Test void registryWithoutRequestsDoesNotInventAnAverageOrActiveMeasurement() {
        var result = monitoring.collect().getApi();
        assertFalse(result.isRequestMetricsAvailable()); assertEquals(0, result.getTotalRequests());
        assertNull(result.getAverageResponseTimeMs()); assertNull(result.getActiveRequests());
        assertNull(result.getTomcatCurrentThreads()); assertNull(result.getTomcatBusyThreads());
        assertTrue(result.getTopRoutes().isEmpty()); assertTrue(result.getJvmThreads() > 0);
        assertTrue(result.getUptimeSeconds() >= 0); assertTrue(result.getCountersSince().isBefore(Instant.now()));
    }
    @Test void activeRequestsFollowTheExistingLongTaskTimers() {
        LongTaskTimer timer = LongTaskTimer.builder("http.server.requests.active").register(registry);
        var first = timer.start(); var second = timer.start();
        assertEquals(2L, monitoring.collect().getApi().getActiveRequests());
        first.stop(); assertEquals(1L, monitoring.collect().getApi().getActiveRequests());
        second.stop(); assertEquals(0L, monitoring.collect().getApi().getActiveRequests());
    }
    @Test void tomcatThreadMeasurementsAreAggregatedAcrossConnectors() {
        Gauge.builder("tomcat.threads.current", () -> 4).tag("name", "connector1").register(registry);
        Gauge.builder("tomcat.threads.current", () -> 2).tag("name", "connector2").register(registry);
        Gauge.builder("tomcat.threads.busy", () -> 1).register(registry);
        var result = monitoring.collect().getApi(); assertEquals(6, result.getTomcatCurrentThreads()); assertEquals(1, result.getTomcatBusyThreads());
    }
    @ParameterizedTest @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, -1})
    void invalidGaugeValuesAreUnavailableRatherThanMisleading(double value) {
        Gauge.builder("tomcat.threads.current", () -> value).register(registry);
        assertNull(monitoring.collect().getApi().getTomcatCurrentThreads());
    }
    @Test void versionComesFromConfigurationAndDoesNotClaimAProductionRelease() {
        assertEquals("0.0.1-SNAPSHOT", monitoring.collect().getApi().getVersion());
    }
    @ParameterizedTest @ValueSource(strings = {"", "@project.version@"})
    void missingOrUnfilteredBuildVersionIsReportedAsUnknown(String value) {
        ReflectionTestUtils.setField(monitoring, "version", value); assertEquals("unknown", monitoring.getVersion());
    }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "SYSADMIN"})
    void existingAdministrativeRolesCanReadDetails(String role) throws Exception {
        executor.setRole(role);
        mvc.perform(get("/api/admin/system/details").principal(identity())).andExpect(status().isOk());
        verify(users).findByEmail(executor.getEmail());
    }
    @ParameterizedTest @ValueSource(strings = {"USER", "ORG_OWNER", "ORG_VICE_OWNER", ""})
    void rolesAreReadFromTheDatabaseBeforeCollectingAnyMetrics(String role) throws Exception {
        executor.setRole(role);
        mvc.perform(get("/api/admin/system/details").principal(identity()).param("role", "SYSADMIN"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(source); assertTrue(registry.getMeters().isEmpty());
    }
    @Test void removedExecutorCannotReadDetails() throws Exception {
        when(users.findByEmail(executor.getEmail())).thenReturn(Optional.empty());
        mvc.perform(get("/api/admin/system/details").principal(identity())).andExpect(status().isForbidden());
        verifyNoInteractions(source);
    }
    @Test void unauthenticatedDetailsReturn401() throws Exception {
        mvc.perform(get("/api/admin/system/details")).andExpect(status().isUnauthorized()); verifyNoInteractions(source, users);
    }
    @Test void userParametersCannotSelectTablesOrExecuteSql() throws Exception {
        mvc.perform(get("/api/admin/system/details").principal(identity()).param("table", "users; DROP TABLE users"))
                .andExpect(status().isOk());
        verify(summary).executeQuery(argThat(sql -> sql.startsWith("SELECT pg_database_size") && !sql.contains("DROP")));
    }
    @Test void unexpectedControllerErrorsDoNotExposeMessages() throws Exception {
        AdminService failing = mock(AdminService.class);
        when(failing.getSystemDetails(executor.getEmail())).thenThrow(new IllegalStateException("private SQL and password"));
        when(failing.getSystemStatus(executor.getEmail())).thenThrow(new IllegalStateException("private SQL and password"));
        AdminController controller = new AdminController(); ReflectionTestUtils.setField(controller, "adminService", failing);
        MockMvc failingMvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String route : List.of("details", "status")) {
            var result = failingMvc.perform(get("/api/admin/system/" + route).principal(identity())).andExpect(status().isInternalServerError()).andReturn();
            assertFalse(result.getResponse().getContentAsString().contains("private SQL"));
        }
    }
}
