package de.tecca.zyrox.dc.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ZyroxDcStorage implements AutoCloseable {

    private static final int INITIAL_SCHEMA_VERSION = 1;

    private final JavaPlugin plugin;
    private final File databaseFile;
    private Connection connection;

    public ZyroxDcStorage(JavaPlugin plugin, File databaseFile) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
    }

    public synchronized void open() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create storage directory: " + parent);
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        migrate();
    }

    public synchronized void upsertLink(String discordId, String discordName, String minecraftUsername, String minecraftUuid) throws SQLException {
        Instant now = Instant.now();
        String normalizedMinecraftUuid = minecraftUuid == null ? "" : minecraftUuid;
        Optional<Long> existingLinkId = findLinkId(discordId, minecraftUsername, normalizedMinecraftUuid);
        if (existingLinkId.isPresent()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE linked_accounts
                    SET discord_name = ?, minecraft_username = ?, minecraft_uuid = ?, updated_at = ?
                    WHERE id = ?
                    """)) {
                statement.setString(1, discordName);
                statement.setString(2, minecraftUsername);
                statement.setString(3, normalizedMinecraftUuid);
                statement.setString(4, now.toString());
                statement.setLong(5, existingLinkId.get());
                statement.executeUpdate();
            }
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO linked_accounts(discord_id, discord_name, minecraft_username, minecraft_uuid, linked_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, discordId);
            statement.setString(2, discordName);
            statement.setString(3, minecraftUsername);
            statement.setString(4, normalizedMinecraftUuid);
            statement.setString(5, now.toString());
            statement.setString(6, now.toString());
            statement.executeUpdate();
        }
    }

    public synchronized boolean deleteLink(String discordId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM linked_accounts WHERE discord_id = ?")) {
            statement.setString(1, discordId);
            return statement.executeUpdate() > 0;
        }
    }

    public synchronized List<LinkedAccount> findAllByDiscordId(String discordId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM linked_accounts WHERE discord_id = ? ORDER BY minecraft_username COLLATE NOCASE")) {
            statement.setString(1, discordId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<LinkedAccount> accounts = new ArrayList<>();
                while (resultSet.next()) {
                    accounts.add(readLinkedAccount(resultSet));
                }
                return accounts;
            }
        }
    }

    public synchronized Optional<LinkedAccount> findByMinecraftIdentity(UUID minecraftUuid, String minecraftUsername) throws SQLException {
        if (minecraftUuid != null) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM linked_accounts WHERE minecraft_uuid = ?")) {
                statement.setString(1, minecraftUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(readLinkedAccount(resultSet));
                    }
                }
            }
        }

        if (minecraftUsername == null || minecraftUsername.isBlank()) {
            return Optional.empty();
        }

        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM linked_accounts WHERE LOWER(minecraft_username) = LOWER(?)")) {
            statement.setString(1, minecraftUsername.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readLinkedAccount(resultSet));
            }
        }
    }

    public synchronized void upsertDiscordBan(String discordId, String reason) throws SQLException {
        Instant now = Instant.now();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO discord_bans(discord_id, reason, banned_at, updated_at)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(discord_id) DO UPDATE SET
                    reason = excluded.reason,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, discordId);
            statement.setString(2, reason == null ? "" : reason);
            statement.setString(3, now.toString());
            statement.setString(4, now.toString());
            statement.executeUpdate();
        }
    }

    public synchronized boolean removeDiscordBan(String discordId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM discord_bans WHERE discord_id = ?")) {
            statement.setString(1, discordId);
            return statement.executeUpdate() > 0;
        }
    }

    public synchronized Optional<DiscordBan> findDiscordBan(String discordId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM discord_bans WHERE discord_id = ?")) {
            statement.setString(1, discordId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readDiscordBan(resultSet));
            }
        }
    }

    public synchronized List<DiscordBan> findAllDiscordBans() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM discord_bans ORDER BY updated_at DESC")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DiscordBan> bans = new ArrayList<>();
                while (resultSet.next()) {
                    bans.add(readDiscordBan(resultSet));
                }
                return bans;
            }
        }
    }

    public synchronized void replaceDiscordBans(Collection<DiscordBan> bans) throws SQLException {
        List<DiscordBan> currentBans = bans == null ? List.of() : List.copyOf(bans);
        try {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM discord_bans");
            }
            for (DiscordBan ban : currentBans) {
                upsertDiscordBan(ban.discordId(), ban.reason());
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized List<LinkedAccount> findAllLinks() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM linked_accounts ORDER BY discord_name COLLATE NOCASE")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<LinkedAccount> accounts = new ArrayList<>();
                while (resultSet.next()) {
                    accounts.add(readLinkedAccount(resultSet));
                }
                return accounts;
            }
        }
    }

    public synchronized List<String> duplicateMinecraftKeys() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(NULLIF(minecraft_uuid, ''), LOWER(minecraft_username)) AS key
                FROM linked_accounts
                GROUP BY key
                HAVING COUNT(*) > 1
                """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> duplicates = new ArrayList<>();
                while (resultSet.next()) {
                    duplicates.add(resultSet.getString("key"));
                }
                return duplicates;
            }
        }
    }

    public synchronized void recordEvent(VerificationEventType type, String discordId, String minecraftUsername, String message) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO verification_events(type, discord_id, minecraft_username, message, created_at)
                VALUES(?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, type.name());
            statement.setString(2, discordId);
            statement.setString(3, minecraftUsername);
            statement.setString(4, message);
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not record verification event: " + exception.getMessage());
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    private void migrate() throws SQLException {
        createMigrationTable();
        if (isMigrationApplied(INITIAL_SCHEMA_VERSION)) {
            return;
        }

        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            applyInitialSchemaMigration();
            recordMigration(INITIAL_SCHEMA_VERSION);
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void createMigrationTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_migrations(
                        version INTEGER PRIMARY KEY,
                        applied_at TEXT NOT NULL
                    )
                    """);
        }
    }

    private boolean isMigrationApplied(int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT version FROM schema_migrations WHERE version = ?")) {
            statement.setInt(1, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void recordMigration(int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO schema_migrations(version, applied_at)
                VALUES(?, ?)
                """)) {
            statement.setInt(1, version);
            statement.setString(2, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private void applyInitialSchemaMigration() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!tableExists("linked_accounts")) {
                createLinkedAccountsTable(statement);
            } else if (!columnExists("linked_accounts", "id")) {
                statement.executeUpdate("ALTER TABLE linked_accounts RENAME TO linked_accounts_old");
                createLinkedAccountsTable(statement);
                statement.executeUpdate("""
                        INSERT INTO linked_accounts(discord_id, discord_name, minecraft_username, minecraft_uuid, linked_at, updated_at)
                        SELECT discord_id, discord_name, minecraft_username, minecraft_uuid, linked_at, updated_at
                        FROM linked_accounts_old
                        """);
                statement.executeUpdate("DROP TABLE linked_accounts_old");
            }
            createLinkedAccountsIndexes(statement);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS verification_events(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        type TEXT NOT NULL,
                        discord_id TEXT,
                        minecraft_username TEXT,
                        message TEXT,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS discord_bans(
                        discord_id TEXT PRIMARY KEY,
                        reason TEXT,
                        banned_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
        }
    }

    private Optional<Long> findLinkId(String discordId, String minecraftUsername, String minecraftUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id
                FROM linked_accounts
                WHERE discord_id = ?
                  AND (
                    (? <> '' AND minecraft_uuid = ?)
                    OR ((minecraft_uuid IS NULL OR minecraft_uuid = '') AND LOWER(minecraft_username) = LOWER(?))
                  )
                LIMIT 1
                """)) {
            statement.setString(1, discordId);
            statement.setString(2, minecraftUuid);
            statement.setString(3, minecraftUuid);
            statement.setString(4, minecraftUsername);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSet.getLong("id"));
            }
        }
    }

    private void createLinkedAccountsTable(Statement statement) throws SQLException {
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
        createLinkedAccountsIndexes(statement);
    }

    private void createLinkedAccountsIndexes(Statement statement) throws SQLException {
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_linked_accounts_discord_id ON linked_accounts(discord_id)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_linked_accounts_minecraft_uuid ON linked_accounts(minecraft_uuid)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_linked_accounts_minecraft_username ON linked_accounts(minecraft_username COLLATE NOCASE)");
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + tableName + ")")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    private LinkedAccount readLinkedAccount(ResultSet resultSet) throws SQLException {
        return new LinkedAccount(
                resultSet.getString("discord_id"),
                resultSet.getString("discord_name"),
                resultSet.getString("minecraft_username"),
                resultSet.getString("minecraft_uuid"),
                Instant.parse(resultSet.getString("linked_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private DiscordBan readDiscordBan(ResultSet resultSet) throws SQLException {
        return new DiscordBan(
                resultSet.getString("discord_id"),
                resultSet.getString("reason"),
                Instant.parse(resultSet.getString("banned_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }
}
