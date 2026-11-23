package de.tecca.zyrox.protect.web;

import de.tecca.zyrox.api.web.endpoint.ZyroxDashboardEndpointRequest;
import de.tecca.zyrox.api.web.endpoint.ZyroxDashboardEndpointResponse;
import de.tecca.zyrox.protect.audit.AuditEvent;
import de.tecca.zyrox.protect.audit.AuditExporter;
import de.tecca.zyrox.protect.audit.AuditJson;
import de.tecca.zyrox.protect.audit.AuditQuery;
import de.tecca.zyrox.protect.audit.AuditRecord;
import de.tecca.zyrox.protect.audit.AuditStorage;
import de.tecca.zyrox.protect.audit.HashChainStatus;
import de.tecca.zyrox.protect.auth.ProtectAuthService;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProtectEndpointController {

    private final AuditStorage storage;
    private final ProtectAuthService authService;
    private final AuditExporter exporter = new AuditExporter();

    public ProtectEndpointController(AuditStorage storage, ProtectAuthService authService) {
        this.storage = storage;
        this.authService = authService;
    }

    public ZyroxDashboardEndpointResponse status(ZyroxDashboardEndpointRequest request) throws SQLException {
        HashChainStatus hash = storage.verifyHashChain();
        String json = "{"
                + "\"success\":true,"
                + "\"configured\":" + authService.isPasswordConfigured() + ','
                + "\"unlocked\":" + authService.isUnlocked(request.actorUniqueId()) + ','
                + "\"eventCount\":" + storage.countEvents() + ','
                + "\"hashValid\":" + hash.valid() + ','
                + "\"hashMessage\":\"" + AuditJson.escape(hash.message()) + "\""
                + "}";
        return ZyroxDashboardEndpointResponse.json(json);
    }

    public ZyroxDashboardEndpointResponse unlock(ZyroxDashboardEndpointRequest request) {
        if (!authService.isPasswordConfigured()) {
            return ZyroxDashboardEndpointResponse.json(403, "{\"success\":false,\"message\":\"Password is not configured.\"}");
        }
        boolean unlocked = authService.unlock(request.actorUniqueId(), request.parameter("password").toCharArray());
        storage.record(new AuditRecord(
                "AUTH",
                unlocked ? "INFO" : "WARNING",
                "WEB",
                Optional.of(request.actorUniqueId()),
                request.actorName(),
                "DASHBOARD",
                "",
                "ZyroxProtect",
                unlocked ? "UNLOCKED" : "UNLOCK_FAILED",
                unlocked ? "ZyroxProtect dashboard unlocked." : "Failed ZyroxProtect dashboard unlock attempt.",
                Map.of("throttled", Boolean.toString(authService.isThrottled(request.actorUniqueId()))),
                ""
        ));
        return ZyroxDashboardEndpointResponse.json(unlocked
                ? "{\"success\":true,\"message\":\"Unlocked.\"}"
                : "{\"success\":false,\"message\":\"Unlock failed.\"}");
    }

    public ZyroxDashboardEndpointResponse lock(ZyroxDashboardEndpointRequest request) {
        authService.lock(request.actorUniqueId());
        storage.record(new AuditRecord(
                "AUTH",
                "INFO",
                "WEB",
                Optional.of(request.actorUniqueId()),
                request.actorName(),
                "DASHBOARD",
                "",
                "ZyroxProtect",
                "LOCKED",
                "ZyroxProtect dashboard locked.",
                Map.of(),
                ""
        ));
        return ZyroxDashboardEndpointResponse.json("{\"success\":true,\"message\":\"Locked.\"}");
    }

    public ZyroxDashboardEndpointResponse events(ZyroxDashboardEndpointRequest request) throws SQLException {
        if (!authService.isUnlocked(request.actorUniqueId())) {
            return ZyroxDashboardEndpointResponse.json(403, "{\"success\":false,\"message\":\"Locked\"}");
        }
        AuditQuery query = queryFrom(request);
        List<AuditEvent> page = storage.queryPage(query);
        boolean hasMore = page.size() > query.limit();
        List<AuditEvent> events = hasMore ? page.subList(0, query.limit()) : page;
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,\"limit\":").append(query.limit())
                .append(",\"offset\":").append(query.offset())
                .append(",\"hasMore\":").append(hasMore)
                .append(",\"events\":[");
        boolean first = true;
        for (AuditEvent event : events) {
            if (!first) {
                json.append(',');
            }
            first = false;
            AuditExporter.appendEventJson(json, event);
        }
        json.append("]}");
        return ZyroxDashboardEndpointResponse.json(json.toString());
    }

    public ZyroxDashboardEndpointResponse export(ZyroxDashboardEndpointRequest request) throws SQLException {
        if (!authService.isUnlocked(request.actorUniqueId())) {
            return ZyroxDashboardEndpointResponse.json(403, "{\"success\":false,\"message\":\"Locked\"}");
        }
        AuditQuery query = queryFrom(request);
        List<AuditEvent> events = storage.query(new AuditQuery(
                query.category(),
                query.severity(),
                query.actor(),
                query.target(),
                query.action(),
                500,
                0
        ));
        String format = "csv".equalsIgnoreCase(request.parameter("format")) ? "csv" : "json";
        ZyroxDashboardEndpointResponse response;
        if ("csv".equals(format)) {
            response = new ZyroxDashboardEndpointResponse(200, "text/csv; charset=utf-8", exporter.csv(events));
        } else {
            response = ZyroxDashboardEndpointResponse.json(exporter.json(events, storage.verifyHashChain()));
        }
        storage.record(new AuditRecord(
                "AUDIT",
                "INFO",
                "WEB",
                Optional.of(request.actorUniqueId()),
                request.actorName(),
                "AUDIT_EXPORT",
                "",
                format,
                "AUDIT_EXPORT",
                "Audit events exported from dashboard.",
                Map.of(
                        "format", format,
                        "category", query.category(),
                        "severity", query.severity(),
                        "actor", query.actor(),
                        "target", query.target(),
                        "action", query.action(),
                        "exportedEvents", Integer.toString(events.size())
                ),
                ""
        ));
        return response;
    }

    private AuditQuery queryFrom(ZyroxDashboardEndpointRequest request) {
        return new AuditQuery(
                request.parameter("category"),
                request.parameter("severity"),
                request.parameter("actor"),
                request.parameter("target"),
                request.parameter("action"),
                parseInt(request.parameter("limit"), 50),
                parseInt(request.parameter("offset"), 0)
        );
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
