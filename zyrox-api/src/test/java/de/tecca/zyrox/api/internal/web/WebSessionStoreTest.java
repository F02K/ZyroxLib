package de.tecca.zyrox.api.internal.web;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSessionStoreTest {

    @Test
    void consumedSessionContainsCsrfToken() {
        WebSessionStore store = new WebSessionStore();
        String token = store.createPendingToken(player(), Duration.ofMinutes(5));

        Optional<SimpleZyroxWebSession> session = store.consumeToken(token);

        assertTrue(session.isPresent());
        assertFalse(session.get().csrfToken().isBlank());
    }

    private Player player() {
        UUID uniqueId = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uniqueId;
                    case "getName" -> "AuditAdmin";
                    case "toString" -> "AuditAdmin";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
