package de.tecca.zyrox.api.web;

import java.time.Instant;
import java.util.UUID;

public interface ZyroxWebSession {

    String id();

    UUID actorUniqueId();

    String actorName();

    Instant createdAt();

    Instant expiresAt();
}
