package de.tecca.zyrox.protect.audit;

public record AuditEvent(
        long id,
        String createdAt,
        String category,
        String severity,
        String actorSource,
        String actorUuid,
        String actorName,
        String targetType,
        String targetUuid,
        String targetName,
        String action,
        String summary,
        String detailsJson,
        String requestMeta,
        String previousHash,
        String eventHash
) {
}
