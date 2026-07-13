package de.tecca.zyrox.dc.storage;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ZyroxDcStorageTest {

    @TempDir
    private Path tempDir;

    @Test
    void freshDatabaseCreatesSchemaAndRecordsMigration() throws Exception {
        Path database = tempDir.resolve("fresh.db");

        try (ZyroxDcStorage storage = openStorage(database)) {
            storage.upsertLink("123", "DiscordUser", "MinecraftUser", "00000000-0000-0000-0000-000000000001");
            Optional<LinkedAccount> account = storage.findByMinecraftIdentity(null, "minecraftuser");

            assertTrue(account.isPresent());
            assertEquals("123", account.get().discordId());
        }

        assertTrue(tableExists(database, "schema_migrations"));
        assertTrue(tableExists(database, "linked_accounts"));
        assertTrue(tableExists(database, "verification_events"));
        assertTrue(tableExists(database, "discord_bans"));
        assertEquals(1, migrationCount(database));
    }

    @Test
    void existingCurrentDatabaseWithoutMigrationTableIsMarkedAsMigrated() throws Exception {
        Path database = tempDir.resolve("current.db");
        try (Connection connection = connect(database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE linked_accounts(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        discord_id TEXT NOT NULL,
                        discord_name TEXT NOT NULL,
                        minecraft_username TEXT NOT NULL,
                        minecraft_uuid TEXT,
                        linked_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
        }

        try (ZyroxDcStorage ignored = openStorage(database)) {
            assertTrue(tableExists(database, "verification_events"));
            assertTrue(tableExists(database, "discord_bans"));
        }

        assertEquals(1, migrationCount(database));
    }

    @Test
    void legacyLinkedAccountsWithoutIdAreCopiedIntoCurrentSchema() throws Exception {
        Path database = tempDir.resolve("legacy.db");
        Instant timestamp = Instant.parse("2026-07-13T01:02:03Z");
        try (Connection connection = connect(database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE linked_accounts(
                        discord_id TEXT NOT NULL,
                        discord_name TEXT NOT NULL,
                        minecraft_username TEXT NOT NULL,
                        minecraft_uuid TEXT,
                        linked_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO linked_accounts(discord_id, discord_name, minecraft_username, minecraft_uuid, linked_at, updated_at)
                    VALUES('42', 'LegacyDiscord', 'LegacyMinecraft', '', '%s', '%s')
                    """.formatted(timestamp, timestamp));
        }

        try (ZyroxDcStorage storage = openStorage(database)) {
            List<LinkedAccount> accounts = storage.findAllLinks();

            assertEquals(1, accounts.size());
            assertEquals("42", accounts.getFirst().discordId());
            assertTrue(columnExists(database, "linked_accounts", "id"));
        }

        assertEquals(1, migrationCount(database));
    }

    @Test
    void openingMigratedDatabaseAgainDoesNotRecordDuplicateMigration() throws Exception {
        Path database = tempDir.resolve("idempotent.db");

        try (ZyroxDcStorage ignored = openStorage(database)) {
            assertEquals(1, migrationCount(database));
        }
        try (ZyroxDcStorage ignored = openStorage(database)) {
            assertEquals(1, migrationCount(database));
        }
    }

    private ZyroxDcStorage openStorage(Path database) throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ZyroxDcStorageTest"));
        ZyroxDcStorage storage = new ZyroxDcStorage(plugin, database.toFile());
        storage.open();
        return storage;
    }

    private Connection connect(Path database) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
    }

    private boolean tableExists(Path database, String tableName) throws Exception {
        try (Connection connection = connect(database);
             var statement = connection.prepareStatement("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean columnExists(Path database, String tableName, String columnName) throws Exception {
        try (Connection connection = connect(database);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private int migrationCount(Path database) throws Exception {
        try (Connection connection = connect(database);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) AS count FROM schema_migrations WHERE version = 1")) {
            return resultSet.next() ? resultSet.getInt("count") : 0;
        }
    }
}
