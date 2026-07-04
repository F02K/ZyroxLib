package de.tecca.zyrox.api.internal.web;

import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WebSessionStore {

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PendingToken> pendingTokens = new ConcurrentHashMap<>();
    private final Map<String, SimpleZyroxWebSession> sessions = new ConcurrentHashMap<>();

    public String createPendingToken(Player player, Duration lifetime) {
        cleanupExpired();

        String token = randomToken();
        Instant now = Instant.now();
        pendingTokens.put(token, new PendingToken(player.getUniqueId(), player.getName(), now, now.plus(lifetime)));
        return token;
    }

    public Optional<SimpleZyroxWebSession> consumeToken(String token) {
        cleanupExpired();

        PendingToken pendingToken = pendingTokens.remove(token);
        if (pendingToken == null || pendingToken.isExpired()) {
            return Optional.empty();
        }

        String sessionId = randomToken();
        SimpleZyroxWebSession session = new SimpleZyroxWebSession(
                sessionId,
                pendingToken.actorUniqueId(),
                pendingToken.actorName(),
                Instant.now(),
                pendingToken.expiresAt()
        );
        sessions.put(sessionId, session);
        return Optional.of(session);
    }

    public Optional<SimpleZyroxWebSession> findSession(String sessionId) {
        cleanupExpired();

        SimpleZyroxWebSession session = sessions.get(sessionId);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        pendingTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record PendingToken(UUID actorUniqueId, String actorName, Instant createdAt, Instant expiresAt) {

        private boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }
    }
}

