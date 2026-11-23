package de.tecca.zyrox.protect.audit;

import java.util.List;

public final class AuditExporter {

    public String json(List<AuditEvent> events, HashChainStatus status) {
        StringBuilder json = new StringBuilder();
        json.append("{\"hashChain\":{\"valid\":").append(status.valid())
                .append(",\"checkedEvents\":").append(status.checkedEvents())
                .append(",\"message\":\"").append(AuditJson.escape(status.message())).append("\"},\"events\":[");
        boolean first = true;
        for (AuditEvent event : events) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendEventJson(json, event);
        }
        json.append("]}");
        return json.toString();
    }

    public String csv(List<AuditEvent> events) {
        StringBuilder csv = new StringBuilder();
        csv.append("id,created_at,category,severity,actor_source,actor_uuid,actor_name,target_type,target_uuid,target_name,action,summary,event_hash\n");
        for (AuditEvent event : events) {
            csv.append(event.id()).append(',')
                    .append(cell(event.createdAt())).append(',')
                    .append(cell(event.category())).append(',')
                    .append(cell(event.severity())).append(',')
                    .append(cell(event.actorSource())).append(',')
                    .append(cell(event.actorUuid())).append(',')
                    .append(cell(event.actorName())).append(',')
                    .append(cell(event.targetType())).append(',')
                    .append(cell(event.targetUuid())).append(',')
                    .append(cell(event.targetName())).append(',')
                    .append(cell(event.action())).append(',')
                    .append(cell(event.summary())).append(',')
                    .append(cell(event.eventHash())).append('\n');
        }
        return csv.toString();
    }

    public static void appendEventJson(StringBuilder json, AuditEvent event) {
        json.append("{\"id\":").append(event.id()).append(',')
                .append("\"createdAt\":\"").append(AuditJson.escape(event.createdAt())).append("\",")
                .append("\"category\":\"").append(AuditJson.escape(event.category())).append("\",")
                .append("\"severity\":\"").append(AuditJson.escape(event.severity())).append("\",")
                .append("\"actorSource\":\"").append(AuditJson.escape(event.actorSource())).append("\",")
                .append("\"actorUuid\":\"").append(AuditJson.escape(event.actorUuid())).append("\",")
                .append("\"actorName\":\"").append(AuditJson.escape(event.actorName())).append("\",")
                .append("\"targetType\":\"").append(AuditJson.escape(event.targetType())).append("\",")
                .append("\"targetUuid\":\"").append(AuditJson.escape(event.targetUuid())).append("\",")
                .append("\"targetName\":\"").append(AuditJson.escape(event.targetName())).append("\",")
                .append("\"action\":\"").append(AuditJson.escape(event.action())).append("\",")
                .append("\"summary\":\"").append(AuditJson.escape(event.summary())).append("\",")
                .append("\"details\":").append(event.detailsJson() == null || event.detailsJson().isBlank() ? "{}" : event.detailsJson()).append(',')
                .append("\"requestMeta\":\"").append(AuditJson.escape(event.requestMeta())).append("\",")
                .append("\"previousHash\":\"").append(AuditJson.escape(event.previousHash())).append("\",")
                .append("\"eventHash\":\"").append(AuditJson.escape(event.eventHash())).append("\"}");
    }

    private String cell(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
