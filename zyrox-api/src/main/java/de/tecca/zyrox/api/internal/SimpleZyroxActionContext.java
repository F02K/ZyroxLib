package de.tecca.zyrox.api.internal;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.action.ZyroxActionContext;
import de.tecca.zyrox.api.action.ZyroxActionSource;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SimpleZyroxActionContext implements ZyroxActionContext {

    private final ZyroxApi api;
    private final ZyroxActionSource source;
    private final UUID actorUniqueId;
    private final String actorName;
    private final Map<String, String> parameters;

    public SimpleZyroxActionContext(
            ZyroxApi api,
            ZyroxActionSource source,
            UUID actorUniqueId,
            String actorName,
            Map<String, String> parameters
    ) {
        this.api = Objects.requireNonNull(api, "api");
        this.source = Objects.requireNonNull(source, "source");
        this.actorUniqueId = actorUniqueId;
        this.actorName = Objects.requireNonNull(actorName, "actorName");
        this.parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }

    @Override
    public ZyroxApi api() {
        return api;
    }

    @Override
    public ZyroxActionSource source() {
        return source;
    }

    @Override
    public Optional<UUID> actorUniqueId() {
        return Optional.ofNullable(actorUniqueId);
    }

    @Override
    public String actorName() {
        return actorName;
    }

    @Override
    public Map<String, String> parameters() {
        return parameters;
    }
}

