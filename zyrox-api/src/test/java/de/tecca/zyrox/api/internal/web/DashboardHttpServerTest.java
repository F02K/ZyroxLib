package de.tecca.zyrox.api.internal.web;

import com.sun.net.httpserver.Headers;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardHttpServerTest {

    @Test
    void csrfMatchesOnlyExpectedToken() {
        SimpleZyroxWebSession session = session("expected-token");

        assertTrue(DashboardHttpServer.csrfMatches(session, "expected-token"));
        assertFalse(DashboardHttpServer.csrfMatches(session, "other-token"));
        assertFalse(DashboardHttpServer.csrfMatches(session, null));
    }

    @Test
    void appliesSecurityHeaders() {
        Headers headers = new Headers();

        DashboardHttpServer.applySecurityHeaders(headers);

        assertEquals("no-store", headers.getFirst("Cache-Control"));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("no-referrer", headers.getFirst("Referrer-Policy"));
        assertTrue(headers.getFirst("Content-Security-Policy").contains("frame-ancestors 'none'"));
    }

    @Test
    void sessionCookieIsSecureOnlyWhenRequested() {
        assertEquals(
                "zyrox_session=session-id; Path=/; HttpOnly; SameSite=Strict",
                DashboardHttpServer.sessionCookie("session-id", false)
        );
        assertEquals(
                "zyrox_session=session-id; Path=/; HttpOnly; SameSite=Strict; Secure",
                DashboardHttpServer.sessionCookie("session-id", true)
        );
    }

    private SimpleZyroxWebSession session(String csrfToken) {
        Instant now = Instant.now();
        return new SimpleZyroxWebSession(
                "session-id",
                csrfToken,
                UUID.randomUUID(),
                "AuditAdmin",
                now,
                now.plusSeconds(600)
        );
    }
}
