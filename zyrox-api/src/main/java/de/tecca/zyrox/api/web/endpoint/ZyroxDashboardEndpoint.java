package de.tecca.zyrox.api.web.endpoint;

import java.util.Objects;

/**
 * A plugin-owned dashboard HTTP endpoint protected by ZyroxAPI sessions and permissions.
 */
public record ZyroxDashboardEndpoint(
        String id,
        String permission,
        ZyroxDashboardEndpointHandler handler
) {

    public ZyroxDashboardEndpoint {
        id = requireIdentifier(id, "id");
        permission = permission == null ? "" : permission;
        handler = Objects.requireNonNull(handler, "handler");
    }

    public boolean hasPermission() {
        return !permission.isBlank();
    }

    private static String requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        if (!text.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException(name + " must contain only lowercase letters, numbers, dots, underscores, or dashes");
        }
        return text;
    }
}
