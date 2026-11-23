package de.tecca.zyrox.protect.audit;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.web.endpoint.ZyroxDashboardEndpointRequest;
import de.tecca.zyrox.api.web.endpoint.ZyroxDashboardEndpointResponse;
import de.tecca.zyrox.protect.auth.ProtectAuthService;
import de.tecca.zyrox.protect.web.ProtectEndpointController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectEndpointControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void eventsReportsHasMoreWhenAnotherPageExists() throws Exception {
        UUID actor = UUID.randomUUID();
        try (Harness harness = openHarness(actor)) {
            harness.storage.append(AuditRecord.system("COMMAND", "INFO", "ONE", "One.", Map.of()));
            harness.storage.append(AuditRecord.system("COMMAND", "INFO", "TWO", "Two.", Map.of()));
            harness.storage.append(AuditRecord.system("COMMAND", "INFO", "THREE", "Three.", Map.of()));

            ZyroxDashboardEndpointResponse response = harness.controller.events(request(actor, Map.of("limit", "2")));

            assertEquals(200, response.status());
            assertTrue(response.body().contains("\"hasMore\":true"));
            assertTrue(response.body().contains("\"limit\":2"));
        }
    }

    @Test
    void exportRecordsAuditEvent() throws Exception {
        UUID actor = UUID.randomUUID();
        try (Harness harness = openHarness(actor)) {
            harness.storage.append(AuditRecord.system("COMMAND", "INFO", "ONE", "One.", Map.of()));

            ZyroxDashboardEndpointResponse response = harness.controller.export(request(actor, Map.of("format", "json")));

            assertEquals(200, response.status());
            assertTrue(response.body().contains("\"events\""));
            assertEquals(1, harness.storage.query(new AuditQuery("AUDIT", "", "", "", "AUDIT_EXPORT", 10, 0)).size());
        }
    }

    private Harness openHarness(UUID actor) throws Exception {
        File database = new File(tempDir.toFile(), "protect.db");
        AuditStorage storage = new AuditStorage(null, database);
        storage.open();
        ProtectAuthService authService = new ProtectAuthService(null, database);
        authService.open();
        authService.setPassword("super-secret-password".toCharArray());
        assertTrue(authService.unlock(actor, "super-secret-password".toCharArray()));
        return new Harness(storage, authService, new ProtectEndpointController(storage, authService));
    }

    private ZyroxDashboardEndpointRequest request(UUID actor, Map<String, String> query) {
        return new ZyroxDashboardEndpointRequest(
                api(),
                actor,
                "AuditAdmin",
                "GET",
                query,
                Map.of(),
                ""
        );
    }

    private ZyroxApi api() {
        return (ZyroxApi) Proxy.newProxyInstance(
                ZyroxApi.class.getClassLoader(),
                new Class<?>[]{ZyroxApi.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "ZyroxApiTestProxy";
                    default -> null;
                }
        );
    }

    private record Harness(AuditStorage storage, ProtectAuthService authService, ProtectEndpointController controller) implements AutoCloseable {

        @Override
        public void close() throws Exception {
            Exception failure = null;
            try {
                authService.close();
            } catch (Exception exception) {
                failure = exception;
            }
            try {
                storage.close();
            } catch (Exception exception) {
                if (failure == null) {
                    failure = exception;
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
