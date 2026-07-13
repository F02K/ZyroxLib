package de.tecca.zyrox.dc.chat;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ChatSyncFormatterTest {

    @Test
    void replacesPlaceholdersAndSanitizesNewlines() {
        String formatted = ChatSyncFormatter.format(
                "**{player}**: {message}",
                Map.of(
                        "player", "Alex",
                        "message", "hello\nserver"
                ),
                256
        );

        assertEquals("**Alex**: hello server", formatted);
    }

    @Test
    void unknownPlaceholdersAreLeftInPlace() {
        String formatted = ChatSyncFormatter.format(
                "{display-name}: {message} {unknown}",
                Map.of(
                        "display-name", "DiscordName",
                        "message", "hi"
                ),
                256
        );

        assertEquals("DiscordName: hi {unknown}", formatted);
    }

    @Test
    void formattedMessageIsLimitedToConfiguredLength() {
        String formatted = ChatSyncFormatter.format(
                "{message}",
                Map.of("message", "abcdefghijklmnopqrstuvwxyz"),
                10
        );

        assertEquals(10, formatted.length());
        assertFalse(formatted.contains("k"));
    }
}
