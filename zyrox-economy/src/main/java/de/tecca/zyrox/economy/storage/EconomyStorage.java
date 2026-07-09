package de.tecca.zyrox.economy.storage;

import org.bukkit.OfflinePlayer;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class EconomyStorage implements AutoCloseable {

    private static final String SHOP_RESERVE_KEY = "shop_reserve_cents";

    private final JavaPlugin plugin;
    private final File databaseFile;
    private Connection connection;

    public EconomyStorage(JavaPlugin plugin, File databaseFile) {
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

    public synchronized void recordWithdrawal(OfflinePlayer player, long reserveCents, long changeCents) throws SQLException {
        if (reserveCents < 0 || changeCents < 0) {
            throw new IllegalArgumentException("Withdrawal ledger amounts cannot be negative.");
        }

        try {
            connection.setAutoCommit(false);
            addStateValue(SHOP_RESERVE_KEY, reserveCents);
            if (changeCents > 0) {
                EconomyIdentity identity = identity(player);
                ensureAccount(identity);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE player_balances
                        SET change_balance_cents = change_balance_cents + ?,
                            player_name = ?,
                            player_uuid = ?,
                            updated_at = ?
                        WHERE player_key = ?
                        """)) {
                    statement.setLong(1, changeCents);
                    statement.setString(2, identity.playerName());
                    statement.setString(3, identity.playerUuid());
                    statement.setString(4, Instant.now().toString());
                    statement.setString(5, identity.playerKey());
                    statement.executeUpdate();
                }
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized boolean recordDeposit(OfflinePlayer player, long amountCents) throws SQLException {
        if (amountCents < 0) {
            throw new IllegalArgumentException("Deposit amount cannot be negative.");
        }
        if (amountCents == 0) {
            return true;
        }

        try {
            connection.setAutoCommit(false);
            long reserve = stateValue(SHOP_RESERVE_KEY);
            if (reserve < amountCents) {
                connection.rollback();
                return false;
            }

            addStateValue(SHOP_RESERVE_KEY, -amountCents);
            EconomyIdentity identity = identity(player);
            ensureAccount(identity);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE player_balances
                    SET payout_balance_cents = payout_balance_cents + ?,
                        player_name = ?,
                        player_uuid = ?,
                        updated_at = ?
                    WHERE player_key = ?
                    """)) {
                statement.setLong(1, amountCents);
                statement.setString(2, identity.playerName());
                statement.setString(3, identity.playerUuid());
                statement.setString(4, Instant.now().toString());
                statement.setString(5, identity.playerKey());
                statement.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized Optional<EconomyAccount> findAccount(OfflinePlayer player) throws SQLException {
        EconomyIdentity identity = identity(player);
        migrateNameAccountToUuid(identity);
        return findAccountByKey(identity.playerKey());
    }

    public synchronized long withdrawCashout(OfflinePlayer player, long requestedCents, long rateCents) throws SQLException {
        if (requestedCents < 0 || rateCents <= 0) {
            throw new IllegalArgumentException("Cashout amount and rate must be positive.");
        }

        EconomyIdentity identity = identity(player);
        try {
            connection.setAutoCommit(false);
            migrateNameAccountToUuid(identity);
            EconomyAccount account = findAccountByKey(identity.playerKey()).orElse(null);
            if (account == null) {
                connection.rollback();
                return 0;
            }

            long total = account.changeBalanceCents() + account.payoutBalanceCents();
            long maxWholeCents = (total / rateCents) * rateCents;
            long requestedWholeCents = (requestedCents / rateCents) * rateCents;
            long debitCents = Math.min(maxWholeCents, requestedWholeCents);
            if (debitCents <= 0) {
                connection.rollback();
                return 0;
            }

            long payoutDebit = Math.min(account.payoutBalanceCents(), debitCents);
            long changeDebit = debitCents - payoutDebit;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE player_balances
                    SET payout_balance_cents = payout_balance_cents - ?,
                        change_balance_cents = change_balance_cents - ?,
                        updated_at = ?
                    WHERE player_key = ?
                    """)) {
                statement.setLong(1, payoutDebit);
                statement.setLong(2, changeDebit);
                statement.setString(3, Instant.now().toString());
                statement.setString(4, identity.playerKey());
                statement.executeUpdate();
            }
            connection.commit();
            return debitCents;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized EconomyLedgerSummary summary() throws SQLException {
        long change = 0;
        long payout = 0;
        long accounts = 0;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) AS account_count,
                       COALESCE(SUM(change_balance_cents), 0) AS change_total,
                       COALESCE(SUM(payout_balance_cents), 0) AS payout_total
                FROM player_balances
                """);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                accounts = resultSet.getLong("account_count");
                change = resultSet.getLong("change_total");
                payout = resultSet.getLong("payout_total");
            }
        }
        return new EconomyLedgerSummary(stateValue(SHOP_RESERVE_KEY), change, payout, accounts);
    }

    public synchronized long shopReserveBalance() throws SQLException {
        return stateValue(SHOP_RESERVE_KEY);
    }

    public synchronized boolean hasFractionalBalances(long unit) throws SQLException {
        if (unit <= 0) {
            throw new IllegalArgumentException("Unit must be positive.");
        }
        if (stateValue(SHOP_RESERVE_KEY) % unit != 0) {
            return true;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM player_balances
                WHERE change_balance_cents % ? != 0
                   OR payout_balance_cents % ? != 0
                LIMIT 1
                """)) {
            statement.setLong(1, unit);
            statement.setLong(2, unit);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public synchronized boolean hasAnyLedgerBalances() throws SQLException {
        if (stateValue(SHOP_RESERVE_KEY) != 0) {
            return true;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM player_balances
                WHERE change_balance_cents != 0
                   OR payout_balance_cents != 0
                LIMIT 1
                """);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
        }
    }

    public synchronized List<EconomyAccount> richestAccounts(int limit) throws SQLException {
        List<EconomyAccount> accounts = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM player_balances
                WHERE change_balance_cents > 0 OR payout_balance_cents > 0
                ORDER BY (change_balance_cents + payout_balance_cents) DESC
                LIMIT ?
                """)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    accounts.add(readAccount(resultSet));
                }
            }
        }
        return accounts;
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
                    CREATE TABLE IF NOT EXISTS economy_state(
                        state_key TEXT PRIMARY KEY,
                        value_cents INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_balances(
                        player_key TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL,
                        player_uuid TEXT,
                        change_balance_cents INTEGER NOT NULL DEFAULT 0,
                        payout_balance_cents INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
        }
        ensureState(SHOP_RESERVE_KEY);
    }

    private void ensureState(String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO economy_state(state_key, value_cents)
                VALUES(?, 0)
                ON CONFLICT(state_key) DO NOTHING
                """)) {
            statement.setString(1, key);
            statement.executeUpdate();
        }
    }

    private long stateValue(String key) throws SQLException {
        ensureState(key);
        try (PreparedStatement statement = connection.prepareStatement("SELECT value_cents FROM economy_state WHERE state_key = ?")) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("value_cents") : 0;
            }
        }
    }

    private void addStateValue(String key, long deltaCents) throws SQLException {
        ensureState(key);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE economy_state
                SET value_cents = value_cents + ?
                WHERE state_key = ?
                """)) {
            statement.setLong(1, deltaCents);
            statement.setString(2, key);
            statement.executeUpdate();
        }
    }

    private void ensureAccount(EconomyIdentity identity) throws SQLException {
        migrateNameAccountToUuid(identity);
        Instant now = Instant.now();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_balances(player_key, player_name, player_uuid, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?)
                ON CONFLICT(player_key) DO UPDATE SET
                    player_name = excluded.player_name,
                    player_uuid = excluded.player_uuid,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, identity.playerKey());
            statement.setString(2, identity.playerName());
            statement.setString(3, identity.playerUuid());
            statement.setString(4, now.toString());
            statement.setString(5, now.toString());
            statement.executeUpdate();
        }
    }

    private void migrateNameAccountToUuid(EconomyIdentity identity) throws SQLException {
        if (identity.playerUuid() == null || identity.playerName().isBlank()) {
            return;
        }

        String nameKey = nameKey(identity.playerName());
        if (nameKey.equals(identity.playerKey())) {
            return;
        }

        EconomyAccount nameAccount = findAccountByKey(nameKey).orElse(null);
        if (nameAccount == null) {
            return;
        }

        EconomyAccount uuidAccount = findAccountByKey(identity.playerKey()).orElse(null);
        if (uuidAccount == null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE player_balances
                    SET player_key = ?, player_uuid = ?, player_name = ?, updated_at = ?
                    WHERE player_key = ?
                    """)) {
                statement.setString(1, identity.playerKey());
                statement.setString(2, identity.playerUuid());
                statement.setString(3, identity.playerName());
                statement.setString(4, Instant.now().toString());
                statement.setString(5, nameKey);
                statement.executeUpdate();
            }
            return;
        }

        try (PreparedStatement merge = connection.prepareStatement("""
                UPDATE player_balances
                SET change_balance_cents = change_balance_cents + ?,
                    payout_balance_cents = payout_balance_cents + ?,
                    player_name = ?,
                    player_uuid = ?,
                    updated_at = ?
                WHERE player_key = ?
                """)) {
            merge.setLong(1, nameAccount.changeBalanceCents());
            merge.setLong(2, nameAccount.payoutBalanceCents());
            merge.setString(3, identity.playerName());
            merge.setString(4, identity.playerUuid());
            merge.setString(5, Instant.now().toString());
            merge.setString(6, identity.playerKey());
            merge.executeUpdate();
        }

        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM player_balances WHERE player_key = ?")) {
            delete.setString(1, nameKey);
            delete.executeUpdate();
        }
    }

    private Optional<EconomyAccount> findAccountByKey(String playerKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM player_balances WHERE player_key = ?")) {
            statement.setString(1, playerKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(readAccount(resultSet));
            }
        }
    }

    private EconomyAccount readAccount(ResultSet resultSet) throws SQLException {
        return new EconomyAccount(
                resultSet.getString("player_key"),
                resultSet.getString("player_name"),
                resultSet.getString("player_uuid"),
                resultSet.getLong("change_balance_cents"),
                resultSet.getLong("payout_balance_cents")
        );
    }

    private EconomyIdentity identity(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName() == null ? uuid.toString() : player.getName();
        return new EconomyIdentity("uuid:" + uuid, name, uuid.toString());
    }

    private String nameKey(String playerName) {
        return "name:" + playerName.toLowerCase(Locale.ROOT);
    }

    public OfflinePlayer offlineByName(String playerName) {
        return plugin.getServer().getOfflinePlayer(playerName);
    }

    private record EconomyIdentity(String playerKey, String playerName, String playerUuid) {
    }
}
