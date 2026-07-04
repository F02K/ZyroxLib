package de.tecca.zyrox.api.internal.web;

import de.tecca.zyrox.api.web.ZyroxWebSession;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SimpleZyroxWebSession implements ZyroxWebSession {

    private final String id;
    private final UUID actorUniqueId;
    private final String actorName;
    private final Instant createdAt;
    private final Instant expiresAt;

    public SimpleZyroxWebSession(String id, UUID actorUniqueId, String actorName, Instant createdAt, Instant expiresAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.actorUniqueId = Objects.requireNonNull(actorUniqueId, "actorUniqueId");
        this.actorName = Objects.requireNonNull(actorName, "actorName");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public UUID actorUniqueId() {
        return actorUniqueId;
    }

    @Override
    public String actorName() {
        return actorName;
    }

    @Override
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public Instant expiresAt() {
        return expiresAt;
    }
}

