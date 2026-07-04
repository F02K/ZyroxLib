package de.tecca.zyrox.api.setting;

import java.util.Objects;

public record ZyroxSetting(
        String id,
        String label,
        String description,
        String categoryId,
        ZyroxSettingType type,
        boolean required,
        String permission,
        int order,
        boolean restartRequired,
        ZyroxSettingReader reader,
        ZyroxSettingWriter writer
) {

    public ZyroxSetting {
        id = requireIdentifier(id, "id");
        label = requireText(label, "label");
        description = description == null ? "" : description;
        categoryId = requireIdentifier(categoryId, "categoryId");
        type = Objects.requireNonNull(type, "type");
        permission = permission == null ? "" : permission;
        reader = Objects.requireNonNull(reader, "reader");
        writer = Objects.requireNonNull(writer, "writer");
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
