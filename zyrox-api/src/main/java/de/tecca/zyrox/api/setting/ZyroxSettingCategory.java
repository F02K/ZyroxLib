package de.tecca.zyrox.api.setting;

import java.util.Objects;

public record ZyroxSettingCategory(
        String id,
        String label,
        int order
) {

    public ZyroxSettingCategory {
        id = requireIdentifier(id, "id");
        label = requireText(label, "label");
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
