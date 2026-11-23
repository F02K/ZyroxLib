package de.tecca.zyrox.protect.audit;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class AuditStorage implements AutoCloseable {

    private final JavaPlugin plugin;
    private final File databaseFile;
    private Connection connection;

    public AuditStorage(JavaPlugin plugin, File databaseFile) {
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

    public synchronized void record(AuditRecord record) {
        try {
            append(record);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not record audit event: " + exception.getMessage());
        }
    }

    public synchronized long append(AuditRecord record) throws SQLException {
        String createdAt = Instant.now().toString();
        String previousHash = latestHash();
        String detailsJson = AuditJson.object(record.details());
        String eventHash = hash(canonical(
                createdAt,
                record.category(),
                record.severity(),
                record.actorSource(),
                record.actorUniqueId().map(Object::toString).orElse(""),
                record.actorName(),
                record.targetType(),
                record.targetUniqueId(),
                record.targetName(),
                record.action(),
                record.summary(),
                detailsJson,
                record.requestMeta(),
                previousHash
        ));

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_events(created_at, category, severity, actor_source, actor_uuid, actor_name,
                                         target_type, target_uuid, target_name, action, summary, details_json,
                                         request_meta, previous_hash, event_hash)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, createdAt);
            statement.setString(2, record.category());
            statement.setString(3, record.severity());
            statement.setString(4, record.actorSource());
            statement.setString(5, record.actorUniqueId().map(Object::toString).orElse(""));
            statement.setString(6, record.actorName());
            statement.setString(7, record.targetType());
            statement.setString(8, record.targetUniqueId());
            statement.setString(9, record.targetName());
            statement.setString(10, record.action());
            statement.setString(11, record.summary());
            statement.setString(12, detailsJson);
            statement.setString(13, record.requestMeta());
            statement.setString(14, previousHash);
            statement.setString(15, eventHash);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        }
    }

    public synchronized List<AuditEvent> query(AuditQuery query) throws SQLException {
        return query(query, query.limit());
    }

    public synchronized List<AuditEvent> queryPage(AuditQuery query) throws SQLException {
        return query(query, Math.min(501, query.limit() + 1));
    }

    private List<AuditEvent> query(AuditQuery query, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_events WHERE 1 = 1");
        List<String> parameters = new ArrayList<>();
        if (!query.category().isBlank()) {
            sql.append(" AND category = ?");
            parameters.add(query.category());
        }
        if (!query.severity().isBlank()) {
            sql.append(" AND severity = ?");
            parameters.add(query.severity());
        }
        if (!query.action().isBlank()) {
            sql.append(" AND action LIKE ?");
            parameters.add("%" + query.action() + "%");
        }
        if (!query.actor().isBlank()) {
            sql.append(" AND (actor_name LIKE ? OR actor_uuid LIKE ?)");
            parameters.add("%" + query.actor() + "%");
            parameters.add("%" + query.actor() + "%");
        }
        if (!query.target().isBlank()) {
            sql.append(" AND (target_name LIKE ? OR target_uuid LIKE ?)");
            parameters.add("%" + query.target() + "%");
            parameters.add("%" + query.target() + "%");
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (String parameter : parameters) {
                statement.setString(index++, parameter);
            }
            statement.setInt(index++, Math.max(1, limit));
            statement.setInt(index, query.offset());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuditEvent> events = new ArrayList<>();
                while (resultSet.next()) {
                    events.add(readEvent(resultSet));
                }
                return events;
            }
        }
    }

    public synchronized HashChainStatus verifyHashChain() throws SQLException {
        String previous = "";
        long checked = 0;
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM audit_events ORDER BY id");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                AuditEvent event = readEvent(resultSet);
                String expected = hash(canonical(
                        event.createdAt(),
                        event.category(),
                        event.severity(),
                        event.actorSource(),
                        event.actorUuid(),
                        event.actorName(),
                        event.targetType(),
                        event.targetUuid(),
                        event.targetName(),
                        event.action(),
                        event.summary(),
                        event.detailsJson(),
                        event.requestMeta(),
                        previous
                ));
                if (!previous.equals(event.previousHash()) || !expected.equals(event.eventHash())) {
                    return new HashChainStatus(false, checked, "Audit hash mismatch at event #" + event.id() + ".");
                }
                previous = event.eventHash();
                checked++;
            }
        }
        return new HashChainStatus(true, checked, "Audit hash chain is valid.");
    }

    public synchronized int purgeOlderThanDays(int days) throws SQLException {
        if (days <= 0) {
            return 0;
        }
        String cutoff = Instant.now().minusSeconds(days * 86400L).toString();
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM audit_events WHERE created_at < ?")) {
            statement.setString(1, cutoff);
            return statement.executeUpdate();
        }
    }

    public synchronized long countEvents() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM audit_events");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
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
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS audit_events(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        created_at TEXT NOT NULL,
                        category TEXT NOT NULL,
                        severity TEXT NOT NULL,
                        actor_source TEXT NOT NULL,
                        actor_uuid TEXT,
                        actor_name TEXT,
                        target_type TEXT,
                        target_uuid TEXT,
                        target_name TEXT,
                        action TEXT NOT NULL,
                        summary TEXT,
                        details_json TEXT,
                        request_meta TEXT,
                        previous_hash TEXT NOT NULL,
                        event_hash TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_events_created ON audit_events(created_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_events_category ON audit_events(category)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_events_actor ON audit_events(actor_name)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_events_target ON audit_events(target_name)");
        }
    }

    private String latestHash() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT event_hash FROM audit_events ORDER BY id DESC LIMIT 1");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getString("event_hash") : "";
        }
    }

    private AuditEvent readEvent(ResultSet resultSet) throws SQLException {
        return new AuditEvent(
                resultSet.getLong("id"),
                resultSet.getString("created_at"),
                resultSet.getString("category"),
                resultSet.getString("severity"),
                resultSet.getString("actor_source"),
                resultSet.getString("actor_uuid"),
                resultSet.getString("actor_name"),
                resultSet.getString("target_type"),
                resultSet.getString("target_uuid"),
                resultSet.getString("target_name"),
                resultSet.getString("action"),
                resultSet.getString("summary"),
                resultSet.getString("details_json"),
                resultSet.getString("request_meta"),
                resultSet.getString("previous_hash"),
                resultSet.getString("event_hash")
        );
    }

    private static String canonical(String... values) {
        return String.join("\u001f", values);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
