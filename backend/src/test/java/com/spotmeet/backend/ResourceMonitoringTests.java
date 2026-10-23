package com.spotmeet.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotmeet.backend.controller.AdminController;
import com.spotmeet.backend.dto.SystemStatusDTO.MemoryInfo;
import com.spotmeet.backend.model.User;
import com.spotmeet.backend.repository.UserRepository;
import com.spotmeet.backend.service.AdminService;
import com.spotmeet.backend.service.SystemMonitoringService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResourceMonitoringTests {
    private static final long MIB = 1024 * 1024;
    private SystemMonitoringService monitoring;
    private MemoryMXBean memory;
    private com.sun.management.OperatingSystemMXBean operatingSystem;
    private DataSource source;
    private Connection connection;
    private SimpleMeterRegistry registry;
    private UserRepository users;
    private User executor;
    private AdminService admin;
    private MockMvc mvc;

    @BeforeEach void setUp() throws Exception {
        memory = mock(MemoryMXBean.class);
        operatingSystem = mock(com.sun.management.OperatingSystemMXBean.class);
        when(memory.getHeapMemoryUsage()).thenReturn(new MemoryUsage(-1, 120 * MIB, 300 * MIB, 1000 * MIB));
        when(operatingSystem.getAvailableProcessors()).thenReturn(4);
        when(operatingSystem.getProcessCpuLoad()).thenReturn(0.25);
        source = mock(DataSource.class); connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.isValid(2)).thenReturn(true);
        registry = new SimpleMeterRegistry();
        monitoring = new SystemMonitoringService();
        ReflectionTestUtils.setField(monitoring, "memoryBean", memory);
        ReflectionTestUtils.setField(monitoring, "operatingSystemBean", operatingSystem);
        ReflectionTestUtils.setField(monitoring, "dataSource", source);
        ReflectionTestUtils.setField(monitoring, "meterRegistry", registry);
        ReflectionTestUtils.setField(monitoring, "version", "0.0.1-SNAPSHOT");
        users = mock(UserRepository.class);
        executor = new User(); executor.setId(1L); executor.setEmail("admin@example.com"); executor.setRole("SYSADMIN");
        when(users.findByEmail(executor.getEmail())).thenReturn(Optional.of(executor));
        admin = new AdminService();
        ReflectionTestUtils.setField(admin, "userRepo", users);
        ReflectionTestUtils.setField(admin, "dataSource", source);
        ReflectionTestUtils.setField(admin, "monitoringService", monitoring);
        ReflectionTestUtils.setField(admin, "serverPort", "0");
        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "adminService", admin);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach void closeRegistry() { registry.close(); }
    private UsernamePasswordAuthenticationToken identity() {
        return new UsernamePasswordAuthenticationToken(executor.getEmail(), "unused", List.of());
    }

    @Test void heapUsageUsesTheJvmLimitInsteadOfCommittedOrPhysicalMemory() {
        MemoryInfo result = monitoring.collectResources();
        assertEquals(300, result.getTotalMb()); assertEquals(120, result.getUsedMb()); assertEquals(180, result.getFreeMb());
        assertEquals(1000L, result.getMaxMb()); assertEquals(12.0, result.getUsagePercent(), 0.00001);
        assertEquals(4, result.getProcessors()); assertEquals(25.0, result.getProcessCpuPercent());
        assertEquals("Recursos da JVM", result.getName()); assertEquals("ONLINE", result.getStatus());
        verify(memory).getHeapMemoryUsage(); verify(memory, never()).gc();
        verify(operatingSystem, never()).getCpuLoad(); verify(operatingSystem, never()).getTotalMemorySize();
        verifyNoInteractions(source);
    }

    @ParameterizedTest @CsvSource({"0,ONLINE", "89,ONLINE", "90,ONLINE", "91,WARNING", "100,WARNING"})
    void warningRequiresHeapUsageStrictlyAboveNinetyPercent(long used, String expected) {
        when(memory.getHeapMemoryUsage()).thenReturn(new MemoryUsage(-1, used * MIB, 100 * MIB, 100 * MIB));
        MemoryInfo result = monitoring.collectResources();
        assertEquals(expected, result.getStatus()); assertEquals((double) used, result.getUsagePercent(), 0.00001);
    }

    @Test void aSingleByteAboveTheThresholdIsNotLostWhenConvertingToMegabytes() {
        when(memory.getHeapMemoryUsage()).thenReturn(new MemoryUsage(-1, 900 * MIB + 1, 1000 * MIB, 1000 * MIB));
        MemoryInfo result = monitoring.collectResources();
        assertEquals(900, result.getUsedMb()); assertTrue(result.getUsagePercent() > 90);
        assertEquals("WARNING", result.getStatus());
    }

    @Test void freeHeapIsCalculatedBeforeRoundingEachValue() {
        when(memory.getHeapMemoryUsage()).thenReturn(new MemoryUsage(-1, MIB + 1, 3 * MIB, 5 * MIB));
        MemoryInfo result = monitoring.collectResources();
        assertEquals(1, result.getUsedMb()); assertEquals(1, result.getFreeMb()); assertEquals(3, result.getTotalMb());
        assertEquals(100.0 * (MIB + 1) / (5 * MIB), result.getUsagePercent(), 0.000001);
    }

    @ParameterizedTest @ValueSource(longs = {-1, 0})
    void anUnknownOrZeroHeapLimitIsUnavailableAndTriggersWarning(long maximum) {
        when(memory.getHeapMemoryUsage()).thenReturn(new MemoryUsage(-1, 0, 0, maximum));
        MemoryInfo result = monitoring.collectResources();
        assertNull(result.getMaxMb()); assertNull(result.getUsagePercent()); assertEquals("WARNING", result.getStatus());
        assertTrue(result.getDetails().contains("indisponível")); assertEquals(25.0, result.getProcessCpuPercent());
    }

    @ParameterizedTest @ValueSource(doubles = {0, 0.25, 1})
    void validCpuSamplesArePercentagesIncludingRealZero(double load) {
        when(operatingSystem.getProcessCpuLoad()).thenReturn(load);
        MemoryInfo result = monitoring.collectResources();
        assertEquals(load * 100, result.getProcessCpuPercent()); assertEquals("ONLINE", result.getStatus());
    }

    @ParameterizedTest @ValueSource(doubles = {-1, 1.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidCpuSamplesDoNotBecomeZeroOrBreakTheHeapMeasurement(double load) {
        when(operatingSystem.getProcessCpuLoad()).thenReturn(load);
        MemoryInfo result = monitoring.collectResources();
        assertNull(result.getProcessCpuPercent()); assertEquals(12.0, result.getUsagePercent(), 0.00001);
        assertEquals("ONLINE", result.getStatus());
    }

    @Test void platformsWithoutTheExtendedCpuBeanStillReturnHeapMetrics() {
        OperatingSystemMXBean basic = mock(OperatingSystemMXBean.class);
        when(basic.getAvailableProcessors()).thenReturn(2);
        ReflectionTestUtils.setField(monitoring, "operatingSystemBean", basic);
        MemoryInfo result = monitoring.collectResources();
        assertEquals(2, result.getProcessors()); assertNull(result.getProcessCpuPercent()); assertEquals("ONLINE", result.getStatus());
    }

    @Test void unavailableCpuSamplingDoesNotExposeNativeErrorMessages() throws Exception {
        when(operatingSystem.getProcessCpuLoad()).thenThrow(new UnsupportedOperationException("private native path and token"));
        MemoryInfo result = monitoring.collectResources();
        assertNull(result.getProcessCpuPercent()); assertEquals("ONLINE", result.getStatus());
        assertFalse(new ObjectMapper().writeValueAsString(result).contains("private native"));
    }

    @ParameterizedTest @CsvSource({"ONLINE,ONLINE,ONLINE", "ACTIVE,ONLINE,ONLINE", "ONLINE,ACTIVE,ACTIVE", "ACTIVE,ACTIVE,ACTIVE"})
    void theOverallStateIsOperationalWhenAllSubsystemsAreHealthy(String database, String network, String resources) {
        assertEquals("OPERATIONAL", monitoring.getOverallStatus(database, network, resources));
    }

    static Stream<Arguments> degradedStates() {
        return Stream.of("WARNING", "FAILURE", "UNKNOWN", null).flatMap(value ->
                Stream.of(Arguments.of(value, "ONLINE", "ONLINE"), Arguments.of("ONLINE", value, "ONLINE"),
                        Arguments.of("ONLINE", "ONLINE", value)));
    }
    @ParameterizedTest @MethodSource("degradedStates")
    void warningsFailuresAndUnavailableStatesDegradeTheOverallState(String database, String network, String resources) {
        assertEquals("DEGRADED", monitoring.getOverallStatus(database, network, resources));
    }

    @Test void theLightweightStatusPreservesExistingFieldsAndAddsResourceMeasurements() {
        var result = admin.getSystemStatus(executor.getEmail());
        assertEquals(300, result.getMemory().getTotalMb()); assertEquals(120, result.getMemory().getUsedMb());
        assertEquals(1000L, result.getMemory().getMaxMb()); assertEquals(25.0, result.getMemory().getProcessCpuPercent());
        assertEquals("ONLINE", result.getMemory().getStatus()); assertEquals("DEGRADED", result.getOverallStatus());
        assertEquals("FAILURE", result.getNetwork().getStatus()); assertEquals("ONLINE", result.getDatabase().getStatus());
        assertEquals("0.0.1-SNAPSHOT", result.getVersion()); assertNotNull(result.getTimestamp());
        assertTrue(result.getUptimeSeconds() >= 0); verify(memory, never()).gc();
    }

    @Test void anInvalidDatabaseProbeNeverClaimsAValidatedConnection() throws Exception {
        when(connection.isValid(2)).thenReturn(false);
        var result = admin.getSystemStatus(executor.getEmail());
        assertEquals("WARNING", result.getDatabase().getStatus()); assertEquals("DEGRADED", result.getOverallStatus());
        assertTrue(result.getDatabase().getDetails().contains("não passou"));
    }

    @Test void databaseCollectionFailureKeepsResourcesInTheDetailsResponse() throws Exception {
        when(source.getConnection()).thenThrow(new SQLException("private JDBC password"));
        var result = admin.getSystemDetails(executor.getEmail());
        assertEquals("FAILURE", result.getDatabase().getStatus()); assertNotNull(result.getApi());
        assertEquals(12.0, result.getResources().getUsagePercent(), 0.00001); assertEquals("ONLINE", result.getResources().getStatus());
        assertFalse(new ObjectMapper().findAndRegisterModules().writeValueAsString(result).contains("private JDBC"));
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "ORG_OWNER", "ORG_VICE_OWNER"})
    void resourceSamplingOnlyRunsAfterCheckingTheAdministrativeRoleInTheDatabase(String role) throws Exception {
        executor.setRole(role);
        mvc.perform(get("/api/admin/system/status").principal(identity()).param("role", "SYSADMIN"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(memory, operatingSystem, source);
    }

    @Test void missingAuthenticationDoesNotCollectResources() throws Exception {
        mvc.perform(get("/api/admin/system/status")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users, memory, operatingSystem, source);
    }

    @Test void clientParametersCannotOverrideResourceMeasurements() throws Exception {
        var result = mvc.perform(get("/api/admin/system/status").principal(identity())
                .param("usagePercent", "99").param("status", "ONLINE")).andExpect(status().isOk()).andReturn();
        var json = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertEquals(12.0, json.get("memory").get("usagePercent").asDouble(), 0.00001);
        assertEquals("DEGRADED", json.get("overallStatus").asText());
    }

    @Test void unexpectedHeapCollectionErrorsReturnAGenericResponse() throws Exception {
        when(memory.getHeapMemoryUsage()).thenThrow(new IllegalStateException("private heap path"));
        for (String route : List.of("status", "details")) {
            var result = mvc.perform(get("/api/admin/system/" + route).principal(identity()))
                    .andExpect(status().isInternalServerError()).andReturn();
            assertFalse(result.getResponse().getContentAsString().contains("private heap"));
        }
    }
}
