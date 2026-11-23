package de.tecca.zyrox.protect.auth;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectAuthService implements AutoCloseable {

    private static final String PASSWORD_KEY = "dashboard_password";

    private final JavaPlugin plugin;
    private final File databaseFile;
    private final PasswordHasher hasher = new PasswordHasher();
    private final Map<UUID, Instant> unlockedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, FailedAttemptWindow> failedAttempts = new ConcurrentHashMap<>();
    private Duration unlockTtl = Duration.ofMinutes(15);
    private Duration failedAttemptWindow = Duration.ofSeconds(60);
    private int maxFailedAttempts = 5;
    private Connection connection;

    public ProtectAuthService(JavaPlugin plugin, File databaseFile) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
    }

    public synchronized void open() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS protect_secrets(
                        id TEXT PRIMARY KEY,
                        algorithm TEXT NOT NULL,
                        iterations INTEGER NOT NULL,
                        salt TEXT NOT NULL,
                        hash TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
        }
    }

    public void reload(Duration unlockTtl, Duration failedAttemptWindow, int maxFailedAttempts) {
        this.unlockTtl = unlockTtl == null || unlockTtl.isNegative() || unlockTtl.isZero() ? Duration.ofMinutes(15) : unlockTtl;
        this.failedAttemptWindow = failedAttemptWindow == null || failedAttemptWindow.isNegative() || failedAttemptWindow.isZero()
                ? Duration.ofSeconds(60)
                : failedAttemptWindow;
        this.maxFailedAttempts = Math.max(1, maxFailedAttempts);
    }

    public synchronized boolean isPasswordConfigured() {
        try {
            return loadPassword().algorithm() != null;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not read ZyroxProtect password state: " + exception.getMessage());
            return false;
        }
    }

    public synchronized void setPassword(char[] password) throws SQLException {
        if (password == null || password.length < 12) {
            throw new IllegalArgumentException("Password must contain at least 12 characters.");
        }
        PasswordHash hash = hasher.hash(password);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO protect_secrets(id, algorithm, iterations, salt, hash, updated_at)
                VALUES(?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    algorithm = excluded.algorithm,
                    iterations = excluded.iterations,
                    salt = excluded.salt,
                    hash = excluded.hash,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, PASSWORD_KEY);
            statement.setString(2, hash.algorithm());
            statement.setInt(3, hash.iterations());
            statement.setString(4, hash.saltBase64());
            statement.setString(5, hash.hashBase64());
            statement.setString(6, Instant.now().toString());
            statement.executeUpdate();
        } finally {
            Arrays.fill(password, '\0');
        }
        unlockedUntil.clear();
        failedAttempts.clear();
    }

    public synchronized boolean unlock(UUID actor, char[] password) {
        if (actor == null) {
            return false;
        }
        if (isThrottled(actor)) {
            return false;
        }
        try {
            PasswordHash stored = loadPassword();
            if (stored.algorithm() == null) {
                return false;
            }
            boolean valid = hasher.verify(password, stored);
            if (valid) {
                unlockedUntil.put(actor, Instant.now().plus(unlockTtl));
                failedAttempts.remove(actor);
                return true;
            }
            recordFailedAttempt(actor);
            return false;
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not verify ZyroxProtect password: " + exception.getMessage());
            return false;
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    public boolean isUnlocked(UUID actor) {
        if (actor == null) {
            return false;
        }
        Instant expiresAt = unlockedUntil.get(actor);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            unlockedUntil.remove(actor);
            return false;
        }
        return true;
    }

    public void lock(UUID actor) {
        if (actor != null) {
            unlockedUntil.remove(actor);
        }
    }

    public void lockAll() {
        unlockedUntil.clear();
    }

    public boolean isThrottled(UUID actor) {
        FailedAttemptWindow window = failedAttempts.get(actor);
        if (window == null) {
            return false;
        }
        if (window.expiresAt().isBefore(Instant.now())) {
            failedAttempts.remove(actor);
            return false;
        }
        return window.count() >= maxFailedAttempts;
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    private PasswordHash loadPassword() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM protect_secrets WHERE id = ?")) {
            statement.setString(1, PASSWORD_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new PasswordHash(null, 0, "", "");
                }
                return new PasswordHash(
                        resultSet.getString("algorithm"),
                        resultSet.getInt("iterations"),
                        resultSet.getString("salt"),
                        resultSet.getString("hash")
                );
            }
        }
    }

    private void recordFailedAttempt(UUID actor) {
        Instant now = Instant.now();
        FailedAttemptWindow current = failedAttempts.get(actor);
        if (current == null || current.expiresAt().isBefore(now)) {
            failedAttempts.put(actor, new FailedAttemptWindow(1, now.plus(failedAttemptWindow)));
            return;
        }
        failedAttempts.put(actor, new FailedAttemptWindow(current.count() + 1, current.expiresAt()));
    }

    private record FailedAttemptWindow(int count, Instant expiresAt) {
    }
}
