package de.tecca.zyrox.api.internal;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.web.ZyroxDashboardViewContext;

import java.util.Objects;
import java.util.UUID;

public final class SimpleZyroxDashboardViewContext implements ZyroxDashboardViewContext {

    private final ZyroxApi api;
    private final UUID actorUniqueId;
    private final String actorName;

    public SimpleZyroxDashboardViewContext(ZyroxApi api, UUID actorUniqueId, String actorName) {
        this.api = Objects.requireNonNull(api, "api");
        this.actorUniqueId = Objects.requireNonNull(actorUniqueId, "actorUniqueId");
        this.actorName = Objects.requireNonNull(actorName, "actorName");
    }

    @Override
    public ZyroxApi api() {
        return api;
    }

    @Override
    public UUID actorUniqueId() {
        return actorUniqueId;
    }

    @Override
    public String actorName() {
        return actorName;
    }
}

