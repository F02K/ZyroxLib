package de.tecca.zyrox.api.audit;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Optional service implemented by audit plugins that want to receive ZyroxAPI admin events.
 */
public interface ZyroxAuditSink {

    void recordAdminEvent(
            String category,
            String severity,
            String action,
            String actorSource,
            Optional<UUID> actorUniqueId,
            String actorName,
            String targetType,
            String targetName,
            String summary,
            Map<String, String> details
    );
}
