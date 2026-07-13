package de.tecca.zyrox.api.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigUpdaterTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-13T01:02:03Z"), ZoneOffset.UTC);
    private static final Logger LOGGER = Logger.getLogger("ConfigUpdaterTest");

    @TempDir
    private Path tempDir;

    @Test
    void missingConfigCreatesBundledDefaultWithoutBackup() throws Exception {
        ConfigUpdateResult result = update("""
                root:
                  enabled: true
                """);

        assertEquals(ConfigUpdateStatus.CREATED, result.status());
        assertTrue(Files.exists(tempDir.resolve("config.yml")));
        assertFalse(Files.exists(tempDir.resolve("config-backups")));
        assertTrue(Files.readString(tempDir.resolve("config.yml")).contains("enabled: true"));
    }

    @Test
    void existingUserValuesSurviveAndNewDefaultsAreAdded() throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), """
                root:
                  enabled: false
                  names:
                  - user
                """);

        ConfigUpdateResult result = update("""
                root:
                  enabled: true
                  names:
                  - default
                  new-key: 42
                """);

        String updated = Files.readString(tempDir.resolve("config.yml"));
        assertEquals(ConfigUpdateStatus.UPDATED, result.status());
        assertTrue(updated.contains("enabled: false"));
        assertTrue(updated.contains("- user"));
        assertTrue(updated.contains("new-key: 42"));
        assertTrue(Files.exists(tempDir.resolve("config-backups").resolve("config-20260713-010203.yml")));
    }

    @Test
    void unknownUserKeysArePreserved() throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), """
                root:
                  enabled: false
                custom:
                  note: keep-me
                """);

        update("""
                root:
                  enabled: true
                  added: 7
                """);

        String updated = Files.readString(tempDir.resolve("config.yml"));
        assertTrue(updated.contains("added: 7"));
        assertTrue(updated.contains("custom:"));
        assertTrue(updated.contains("note: keep-me"));
    }

    @Test
    void upToDateGeneratedConfigDoesNotCreateBackup() throws Exception {
        String defaults = """
                root:
                  enabled: true
                """;
        update(defaults);
        ConfigUpdateResult result = update(defaults);

        assertEquals(ConfigUpdateStatus.UNCHANGED, result.status());
        assertFalse(Files.exists(tempDir.resolve("config-backups")));
    }

    @Test
    void failedBackupDoesNotDestroyOriginal() throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), """
                root:
                  enabled: false
                """);
        Files.writeString(tempDir.resolve("config-backups"), "not-a-directory");

        ConfigUpdateResult result = update("""
                root:
                  enabled: true
                  added: 1
                """);

        assertEquals(ConfigUpdateStatus.FAILED, result.status());
        String original = Files.readString(tempDir.resolve("config.yml"));
        assertTrue(original.contains("enabled: false"));
        assertFalse(original.contains("added: 1"));
    }

    private ConfigUpdateResult update(String bundledDefault) {
        return ConfigUpdater.update(
                tempDir,
                "config.yml",
                () -> stream(bundledDefault),
                LOGGER,
                FIXED_CLOCK
        );
    }

    private InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
