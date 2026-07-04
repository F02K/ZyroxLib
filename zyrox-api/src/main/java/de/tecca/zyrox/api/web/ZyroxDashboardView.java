package de.tecca.zyrox.api.web;

import java.util.Objects;

/**
 * A plugin-owned HTML view rendered inside the central Zyrox dashboard.
 */
public record ZyroxDashboardView(
        String id,
        String label,
        String permission,
        ZyroxDashboardViewRenderer renderer
) {

    public ZyroxDashboardView {
        id = requireIdentifier(id, "id");
        label = requireText(label, "label");
        permission = permission == null ? "" : permission;
        renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public boolean hasPermission() {
        return !permission.isBlank();
    }

    private static String requireIdentifier(String value, String name) {
        String text = requireText(value, name);
        if (!text.matches("[a-z0-9._-]+")) {
            throw new IllegalArgumentException(name + " must contain only lowercase letters, numbers, dots, underscores, or dashes");
        }
        return text;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String text = value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return text;
    }
}

