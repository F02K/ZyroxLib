package de.tecca.zyrox.api.action;

import java.util.List;
import java.util.Objects;

/**
 * Describes an admin action exposed through commands and the web dashboard.
 */
public record ZyroxAction(
        String id,
        String label,
        String description,
        String permission,
        List<ZyroxActionParameter> parameters,
        ZyroxActionHandler handler
) {

    public ZyroxAction {
        id = requireIdentifier(id, "id");
        label = requireText(label, "label");
        description = description == null ? "" : description;
        permission = permission == null ? "" : permission;
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        handler = Objects.requireNonNull(handler, "handler");
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

