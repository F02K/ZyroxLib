package de.tecca.zyrox.api.setting;

import java.util.Objects;

public record ZyroxSettingResult(
        boolean success,
        String message
) {

    public ZyroxSettingResult {
        message = message == null ? "" : message;
    }

    public static ZyroxSettingResult success(String message) {
        return new ZyroxSettingResult(true, Objects.requireNonNullElse(message, "Setting saved."));
    }

    public static ZyroxSettingResult failure(String message) {
        return new ZyroxSettingResult(false, Objects.requireNonNullElse(message, "Setting could not be saved."));
    }
}
