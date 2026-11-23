package de.tecca.zyrox.protect.audit;

public record AuditQuery(
        String category,
        String severity,
        String actor,
        String target,
        String action,
        int limit,
        int offset
) {

    public AuditQuery {
        category = clean(category);
        severity = clean(severity);
        actor = clean(actor);
        target = clean(target);
        action = clean(action);
        limit = Math.max(1, Math.min(500, limit <= 0 ? 100 : limit));
        offset = Math.max(0, offset);
    }

    public static AuditQuery recent(int limit) {
        return new AuditQuery("", "", "", "", "", limit, 0);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
