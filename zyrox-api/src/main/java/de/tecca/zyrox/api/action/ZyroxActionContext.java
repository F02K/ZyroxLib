package de.tecca.zyrox.api.action;

import de.tecca.zyrox.api.ZyroxApi;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runtime context passed to an action handler.
 */
public interface ZyroxActionContext {

    ZyroxApi api();

    ZyroxActionSource source();

    Optional<UUID> actorUniqueId();

    String actorName();

    Map<String, String> parameters();
}

