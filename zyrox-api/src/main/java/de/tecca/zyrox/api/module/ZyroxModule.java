package de.tecca.zyrox.api.module;

import java.util.Objects;

/**
 * Describes a ZyroxMC plugin/module visible to the central dashboard.
 */
public record ZyroxModule(
        String id,
        String name,
        String version,
        String description,
        String permission
) {

    public ZyroxModule {
        id = requireIdentifier(id, "id");
        name = requireText(name, "name");
        version = requireText(version, "version");
        description = description == null ? "" : description;
        permission = permission == null ? "" : permission;
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

