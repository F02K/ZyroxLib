package de.tecca.zyrox.api.action;

import java.util.Objects;

/**
 * Describes one typed action parameter.
 */
public record ZyroxActionParameter(
        String id,
        String label,
        ZyroxActionParameterType type,
        boolean required
) {

    public ZyroxActionParameter {
        id = requireIdentifier(id, "id");
        label = requireText(label, "label");
        type = Objects.requireNonNull(type, "type");
    }

    public static ZyroxActionParameter string(String id, String label, boolean required) {
        return new ZyroxActionParameter(id, label, ZyroxActionParameterType.STRING, required);
    }

    public static ZyroxActionParameter integer(String id, String label, boolean required) {
        return new ZyroxActionParameter(id, label, ZyroxActionParameterType.INTEGER, required);
    }

    public static ZyroxActionParameter decimal(String id, String label, boolean required) {
        return new ZyroxActionParameter(id, label, ZyroxActionParameterType.DECIMAL, required);
    }

    public static ZyroxActionParameter bool(String id, String label, boolean required) {
        return new ZyroxActionParameter(id, label, ZyroxActionParameterType.BOOLEAN, required);
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

