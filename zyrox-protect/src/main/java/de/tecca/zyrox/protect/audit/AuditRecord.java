package de.tecca.zyrox.protect.audit;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AuditRecord(
        String category,
        String severity,
        String actorSource,
        Optional<UUID> actorUniqueId,
        String actorName,
        String targetType,
        String targetUniqueId,
        String targetName,
        String action,
        String summary,
        Map<String, String> details,
        String requestMeta
) {

    public AuditRecord {
        category = clean(category, "GENERAL");
        severity = clean(severity, "INFO");
        actorSource = clean(actorSource, "SYSTEM");
        actorUniqueId = Objects.requireNonNull(actorUniqueId, "actorUniqueId");
        actorName = clean(actorName, "Unknown");
        targetType = clean(targetType, "");
        targetUniqueId = clean(targetUniqueId, "");
        targetName = clean(targetName, "");
        action = clean(action, "EVENT");
        summary = clean(summary, "");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
        requestMeta = clean(requestMeta, "");
    }

    public static AuditRecord system(String category, String severity, String action, String summary, Map<String, String> details) {
        return new AuditRecord(category, severity, "SYSTEM", Optional.empty(), "ZyroxProtect", "", "", "", action, summary, details, "");
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
