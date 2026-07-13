package de.tecca.zyrox.dc.chat;

import java.util.Map;

public final class ChatSyncFormatter {

    private static final int DEFAULT_MAX_LENGTH = 256;

    private ChatSyncFormatter() {
    }

    public static String format(String pattern, Map<String, String> placeholders, int maxLength) {
        String result = pattern == null ? "" : pattern;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            result = result.replace("{" + placeholder.getKey() + "}", sanitize(placeholder.getValue()));
        }
        return trimLength(result, maxLength);
    }

    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
    }

    public static String trimLength(String value, int maxLength) {
        String text = value == null ? "" : value;
        int safeMaxLength = maxLength <= 0 ? DEFAULT_MAX_LENGTH : maxLength;
        if (text.length() <= safeMaxLength) {
            return text;
        }
        if (safeMaxLength < 4) {
            return text.substring(0, safeMaxLength);
        }
        return text.substring(0, safeMaxLength - 3) + "...";
    }
}
