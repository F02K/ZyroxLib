package de.tecca.zyrox.chunkloader.storage;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ChunkloaderStorage implements AutoCloseable {

    private final JavaPlugin plugin;
    private final File databaseFile;
    private final Clock clock;
    private Connection connection;

    public ChunkloaderStorage(JavaPlugin plugin, File databaseFile) {
        this(plugin, databaseFile, Clock.systemUTC());
    }

    public ChunkloaderStorage(JavaPlugin plugin, File databaseFile, Clock clock) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
        this.clock = clock;
    }

    public synchronized void open() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create storage directory: " + parent);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        migrate();
    }

    public synchronized ChunkloaderRecord createLoader(Location location, Player owner) throws SQLException {
        LocationKey key = LocationKey.from(location);
        int priority = maxPriority(owner.getUniqueId()) + 1;
        String now = now();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO chunkloaders(world_name, x, y, z, chunk_x, chunk_z, owner_uuid, owner_name,
                                         priority, enabled, reserve_blocks, paid_until, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, NULL, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, key.worldName());
            statement.setInt(2, key.x());
            statement.setInt(3, key.y());
            statement.setInt(4, key.z());
            statement.setInt(5, key.chunkX());
            statement.setInt(6, key.chunkZ());
            statement.setString(7, owner.getUniqueId().toString());
            statement.setString(8, owner.getName());
            statement.setInt(9, priority);
            statement.setString(10, now);
            statement.setString(11, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Chunkloader insert did not return an id.");
                }
                return findById(keys.getLong(1)).orElseThrow();
            }
        }
    }

    public synchronized Optional<ChunkloaderRecord> findByLocation(Location location) throws SQLException {
        LocationKey key = LocationKey.from(location);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM chunkloaders
                WHERE world_name = ? AND x = ? AND y = ? AND z = ?
                """)) {
            statement.setString(1, key.worldName());
            statement.setInt(2, key.x());
            statement.setInt(3, key.y());
            statement.setInt(4, key.z());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(read(resultSet)) : Optional.empty();
            }
        }
    }

    public synchronized Optional<ChunkloaderRecord> findById(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM chunkloaders WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(read(resultSet)) : Optional.empty();
            }
        }
    }

    public synchronized List<ChunkloaderRecord> allLoaders() throws SQLException {
        List<ChunkloaderRecord> loaders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM chunkloaders ORDER BY owner_uuid, priority, id");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                loaders.add(read(resultSet));
            }
        }
        return loaders;
    }

    public synchronized List<ChunkloaderRecord> byOwner(UUID ownerId) throws SQLException {
        List<ChunkloaderRecord> loaders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM chunkloaders
                WHERE owner_uuid = ?
                ORDER BY priority, id
                """)) {
            statement.setString(1, ownerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    loaders.add(read(resultSet));
                }
            }
        }
        return loaders;
    }

    public synchronized ChunkloaderRecord setEnabled(long id, boolean enabled) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE chunkloaders
                SET enabled = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setInt(1, enabled ? 1 : 0);
            statement.setString(2, now());
            statement.setLong(3, id);
            statement.executeUpdate();
        }
        return findById(id).orElseThrow();
    }

    public synchronized ChunkloaderRecord addReserve(long id, long delta) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE chunkloaders
                SET reserve_blocks = MAX(0, reserve_blocks + ?), updated_at = ?
                WHERE id = ?
                """)) {
            statement.setLong(1, delta);
            statement.setString(2, now());
            statement.setLong(3, id);
            statement.executeUpdate();
        }
        return findById(id).orElseThrow();
    }

    public synchronized boolean payCycle(long id, long blocks, Instant paidUntil) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE chunkloaders
                SET reserve_blocks = reserve_blocks - ?, paid_until = ?, updated_at = ?
                WHERE id = ? AND reserve_blocks >= ?
                """)) {
            statement.setLong(1, blocks);
            statement.setString(2, paidUntil.toString());
            statement.setString(3, now());
            statement.setLong(4, id);
            statement.setLong(5, blocks);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized Optional<ChunkloaderRecord> movePriority(long id, int direction) throws SQLException {
        ChunkloaderRecord loader = findById(id).orElse(null);
        if (loader == null || direction == 0) {
            return Optional.ofNullable(loader);
        }
        List<ChunkloaderRecord> ownerLoaders = byOwner(loader.ownerUuid());
        int index = -1;
        for (int i = 0; i < ownerLoaders.size(); i++) {
            if (ownerLoaders.get(i).id() == id) {
                index = i;
                break;
            }
        }
        int swapIndex = index + (direction < 0 ? -1 : 1);
        if (index < 0 || swapIndex < 0 || swapIndex >= ownerLoaders.size()) {
            return Optional.of(loader);
        }
        ChunkloaderRecord other = ownerLoaders.get(swapIndex);
        try {
            connection.setAutoCommit(false);
            updatePriority(loader.id(), other.priority());
            updatePriority(other.id(), loader.priority());
            connection.commit();
            return findById(id);
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized boolean deleteLoader(long id) throws SQLException {
        ChunkloaderRecord loader = findById(id).orElse(null);
        if (loader == null) {
            return false;
        }
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM chunkloaders WHERE id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
            compactPriorities(loader.ownerUuid());
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized void markShutdownTime() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO chunkloader_meta(key, value)
                VALUES('last_shutdown', ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """)) {
            statement.setString(1, now());
            statement.executeUpdate();
        }
    }

    public synchronized int pausePaidTimeSinceLastShutdown() throws SQLException {
        Instant lastShutdown = lastShutdown().orElse(null);
        if (lastShutdown == null) {
            return 0;
        }
        Instant current = Instant.now(clock);
        Duration downtime = Duration.between(lastShutdown, current);
        if (downtime.isNegative() || downtime.isZero()) {
            clearLastShutdown();
            return 0;
        }
        int updated = 0;
        try {
            connection.setAutoCommit(false);
            for (ChunkloaderRecord loader : allLoaders()) {
                if (loader.paidUntil() == null || loader.paidUntil().isBlank()) {
                    continue;
                }
                Instant paidUntil;
                try {
                    paidUntil = Instant.parse(loader.paidUntil());
                } catch (RuntimeException exception) {
                    continue;
                }
                if (!paidUntil.isAfter(lastShutdown)) {
                    continue;
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE chunkloaders
                        SET paid_until = ?, updated_at = ?
                        WHERE id = ?
                        """)) {
                    statement.setString(1, paidUntil.plus(downtime).toString());
                    statement.setString(2, now());
                    statement.setLong(3, loader.id());
                    updated += statement.executeUpdate();
                }
            }
            clearLastShutdown();
            connection.commit();
            return updated;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    private int maxPriority(UUID ownerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT MAX(priority) FROM chunkloaders WHERE owner_uuid = ?")) {
            statement.setString(1, ownerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private void compactPriorities(UUID ownerId) throws SQLException {
        List<ChunkloaderRecord> loaders = byOwner(ownerId);
        for (int i = 0; i < loaders.size(); i++) {
            updatePriority(loaders.get(i).id(), i + 1);
        }
    }

    private void updatePriority(long id, int priority) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE chunkloaders
                SET priority = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setInt(1, priority);
            statement.setString(2, now());
            statement.setLong(3, id);
            statement.executeUpdate();
        }
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS chunkloaders(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        world_name TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        chunk_x INTEGER NOT NULL,
                        chunk_z INTEGER NOT NULL,
                        owner_uuid TEXT NOT NULL,
                        owner_name TEXT NOT NULL,
                        priority INTEGER NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        reserve_blocks INTEGER NOT NULL DEFAULT 0,
                        paid_until TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        UNIQUE(world_name, x, y, z)
                    )
                    """);
            addColumnIfMissing(statement, "chunkloaders", "chunk_x", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(statement, "chunkloaders", "chunk_z", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(statement, "chunkloaders", "enabled", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(statement, "chunkloaders", "reserve_blocks", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(statement, "chunkloaders", "paid_until", "TEXT");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS chunkloader_meta(
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
        }
    }

    private void addColumnIfMissing(Statement statement, String tableName, String columnName, String type) throws SQLException {
        if (columnNames(tableName).contains(columnName)) {
            return;
        }
        statement.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + type);
    }

    private Set<String> columnNames(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        return columns;
    }

    private ChunkloaderRecord read(ResultSet resultSet) throws SQLException {
        int x = resultSet.getInt("x");
        int z = resultSet.getInt("z");
        int chunkX = resultSet.getInt("chunk_x");
        int chunkZ = resultSet.getInt("chunk_z");
        if (chunkX == 0 && x != 0) {
            chunkX = x >> 4;
        }
        if (chunkZ == 0 && z != 0) {
            chunkZ = z >> 4;
        }
        return new ChunkloaderRecord(
                resultSet.getLong("id"),
                resultSet.getString("world_name"),
                x,
                resultSet.getInt("y"),
                z,
                chunkX,
                chunkZ,
                UUID.fromString(resultSet.getString("owner_uuid")),
                resultSet.getString("owner_name"),
                resultSet.getInt("priority"),
                resultSet.getInt("enabled") != 0,
                resultSet.getLong("reserve_blocks"),
                resultSet.getString("paid_until"),
                resultSet.getString("created_at"),
                resultSet.getString("updated_at")
        );
    }

    private String now() {
        return Instant.now(clock).toString();
    }

    private Optional<Instant> lastShutdown() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT value FROM chunkloader_meta WHERE key = 'last_shutdown'");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            try {
                return Optional.of(Instant.parse(resultSet.getString("value")));
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
        }
    }

    private void clearLastShutdown() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM chunkloader_meta WHERE key = 'last_shutdown'")) {
            statement.executeUpdate();
        }
    }
}
