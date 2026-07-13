package de.tecca.zyrox.shop.storage;

import de.tecca.zyrox.shop.util.ItemStacks;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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

public final class ShopStorage implements AutoCloseable {

    private final JavaPlugin plugin;
    private final File databaseFile;
    private final ShopItemCodec itemCodec;
    private final Clock clock;
    private Duration emptyStockAutoCloseDuration = Duration.ofDays(5);
    private Connection connection;

    public ShopStorage(JavaPlugin plugin, File databaseFile) {
        this(plugin, databaseFile, new ShopItemCodec() {
            @Override
            public String serialize(ItemStack item) {
                return ItemStacks.serialize(item);
            }

            @Override
            public ItemStack deserialize(String value) {
                return ItemStacks.deserialize(value);
            }
        });
    }

    public ShopStorage(JavaPlugin plugin, File databaseFile, ShopItemCodec itemCodec) {
        this(plugin, databaseFile, itemCodec, Clock.systemUTC());
    }

    public ShopStorage(JavaPlugin plugin, File databaseFile, ShopItemCodec itemCodec, Clock clock) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
        this.itemCodec = itemCodec;
        this.clock = clock;
    }

    public synchronized void setEmptyStockAutoCloseDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            emptyStockAutoCloseDuration = Duration.ofDays(5);
            return;
        }
        emptyStockAutoCloseDuration = duration;
    }

    public synchronized void open() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create storage directory: " + parent);
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        migrate();
    }

    public synchronized ShopRecord createShop(
            Location location,
            Player owner,
            ItemStack template,
            int bundleAmount,
            long priceDiamonds
    ) throws SQLException {
        LocationKey key = LocationKey.from(location);
        String now = now();
        String emptyStockExpiresAt = Instant.parse(now).plus(emptyStockAutoCloseDuration).toString();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO shops(world_name, x, y, z, owner_uuid, owner_name, item_data,
                                  bundle_amount, price_diamonds, stock_amount, revenue_diamonds,
                                  empty_stock_expires_at, last_empty_stock_warning_at,
                                  created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, NULL, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, key.worldName());
            statement.setInt(2, key.x());
            statement.setInt(3, key.y());
            statement.setInt(4, key.z());
            statement.setString(5, owner.getUniqueId().toString());
            statement.setString(6, owner.getName());
            statement.setString(7, itemCodec.serialize(template));
            statement.setInt(8, Math.max(1, bundleAmount));
            statement.setLong(9, Math.max(1, priceDiamonds));
            statement.setString(10, emptyStockExpiresAt);
            statement.setString(11, now);
            statement.setString(12, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Shop insert did not return an id.");
                }
                return findById(keys.getLong(1)).orElseThrow();
            }
        }
    }

    public synchronized Optional<ShopRecord> findByLocation(Location location) throws SQLException {
        LocationKey key = LocationKey.from(location);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM shops
                WHERE world_name = ? AND x = ? AND y = ? AND z = ?
                """)) {
            statement.setString(1, key.worldName());
            statement.setInt(2, key.x());
            statement.setInt(3, key.y());
            statement.setInt(4, key.z());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readShop(resultSet)) : Optional.empty();
            }
        }
    }

    public synchronized Optional<ShopRecord> findById(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM shops WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readShop(resultSet)) : Optional.empty();
            }
        }
    }

    public synchronized List<ShopRecord> allShops() throws SQLException {
        List<ShopRecord> shops = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM shops ORDER BY id");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                shops.add(readShop(resultSet));
            }
        }
        return shops;
    }

    public synchronized ShopRecord updateBundle(long shopId, int bundleAmount, long priceDiamonds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE shops
                SET bundle_amount = ?, price_diamonds = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setInt(1, Math.max(1, bundleAmount));
            statement.setLong(2, Math.max(1, priceDiamonds));
            statement.setString(3, now());
            statement.setLong(4, shopId);
            statement.executeUpdate();
        }
        return findById(shopId).orElseThrow();
    }

    public synchronized ShopRecord updateTemplate(long shopId, ItemStack template) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE shops
                SET item_data = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setString(1, itemCodec.serialize(template));
            statement.setString(2, now());
            statement.setLong(3, shopId);
            statement.executeUpdate();
        }
        return findById(shopId).orElseThrow();
    }

    public synchronized ShopRecord addStock(long shopId, long amount, Player actor) throws SQLException {
        if (amount <= 0) {
            return findById(shopId).orElseThrow();
        }
        try {
            connection.setAutoCommit(false);
            addStockUnchecked(shopId, amount);
            insertTransaction(shopId, actor, ShopTransactionType.STOCK_DEPOSIT, amount, 0);
            connection.commit();
            return findById(shopId).orElseThrow();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized ShopRecord withdrawStock(long shopId, long amount, Player actor) throws SQLException {
        if (amount <= 0) {
            return findById(shopId).orElseThrow();
        }
        try {
            connection.setAutoCommit(false);
            ShopRecord shop = findById(shopId).orElseThrow();
            if (shop.stockAmount() < amount) {
                throw new SQLException("Shop does not have enough stock.");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE shops
                    SET stock_amount = stock_amount - ?,
                        empty_stock_expires_at = CASE
                            WHEN stock_amount - ? <= 0 THEN ?
                            ELSE empty_stock_expires_at
                        END,
                        last_empty_stock_warning_at = CASE
                            WHEN stock_amount - ? <= 0 THEN NULL
                            ELSE last_empty_stock_warning_at
                        END,
                        updated_at = ?
                    WHERE id = ? AND stock_amount >= ?
                    """)) {
                statement.setLong(1, amount);
                statement.setLong(2, amount);
                statement.setString(3, deadlineFromNow());
                statement.setLong(4, amount);
                statement.setString(5, now());
                statement.setLong(6, shopId);
                statement.setLong(7, amount);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Shop stock changed before withdrawal could complete.");
                }
            }
            insertTransaction(shopId, actor, ShopTransactionType.STOCK_WITHDRAW, amount, 0);
            connection.commit();
            return findById(shopId).orElseThrow();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized ShopRecord recordPurchase(
            long shopId,
            Player buyer,
            long bundles,
            int expectedBundleAmount,
            long expectedPriceDiamonds
    ) throws SQLException {
        if (bundles <= 0) {
            throw new SQLException("Purchase bundle amount must be positive.");
        }
        try {
            connection.setAutoCommit(false);
            ShopRecord shop = findById(shopId).orElseThrow();
            if (shop.bundleAmount() != expectedBundleAmount || shop.priceDiamonds() != expectedPriceDiamonds) {
                throw new SQLException("Shop offer changed.");
            }
            long items = Math.multiplyExact(shop.bundleAmount(), bundles);
            long price = Math.multiplyExact(shop.priceDiamonds(), bundles);
            if (shop.stockAmount() < items) {
                throw new SQLException("Shop does not have enough stock.");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE shops
                    SET stock_amount = stock_amount - ?,
                        revenue_diamonds = revenue_diamonds + ?,
                        empty_stock_expires_at = CASE
                            WHEN stock_amount - ? <= 0 THEN ?
                            ELSE empty_stock_expires_at
                        END,
                        last_empty_stock_warning_at = CASE
                            WHEN stock_amount - ? <= 0 THEN NULL
                            ELSE last_empty_stock_warning_at
                        END,
                        updated_at = ?
                    WHERE id = ? AND bundle_amount = ? AND price_diamonds = ? AND stock_amount >= ?
                    """)) {
                statement.setLong(1, items);
                statement.setLong(2, price);
                statement.setLong(3, items);
                statement.setString(4, deadlineFromNow());
                statement.setLong(5, items);
                statement.setString(6, now());
                statement.setLong(7, shopId);
                statement.setInt(8, expectedBundleAmount);
                statement.setLong(9, expectedPriceDiamonds);
                statement.setLong(10, items);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Shop offer or stock changed before purchase could complete.");
                }
            }
            insertTransaction(shopId, buyer, ShopTransactionType.PURCHASE, items, price);
            connection.commit();
            return findById(shopId).orElseThrow();
        } catch (SQLException | ArithmeticException exception) {
            connection.rollback();
            throw exception instanceof SQLException sqlException
                    ? sqlException
                    : new SQLException("Purchase amount is too large.", exception);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized ShopRecord withdrawRevenue(long shopId, long amount, Player actor) throws SQLException {
        if (amount <= 0) {
            return findById(shopId).orElseThrow();
        }
        try {
            connection.setAutoCommit(false);
            ShopRecord shop = findById(shopId).orElseThrow();
            if (shop.revenueDiamonds() < amount) {
                throw new SQLException("Shop does not have enough stored diamonds.");
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE shops
                    SET revenue_diamonds = revenue_diamonds - ?, updated_at = ?
                    WHERE id = ? AND revenue_diamonds >= ?
                    """)) {
                statement.setLong(1, amount);
                statement.setString(2, now());
                statement.setLong(3, shopId);
                statement.setLong(4, amount);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Shop revenue changed before withdrawal could complete.");
                }
            }
            insertTransaction(shopId, actor, ShopTransactionType.REVENUE_WITHDRAW, 0, amount);
            connection.commit();
            return findById(shopId).orElseThrow();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized ShopRecord refundRevenue(long shopId, long amount) throws SQLException {
        if (amount <= 0) {
            return findById(shopId).orElseThrow();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE shops
                SET revenue_diamonds = revenue_diamonds + ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setLong(1, amount);
            statement.setString(2, now());
            statement.setLong(3, shopId);
            statement.executeUpdate();
        }
        return findById(shopId).orElseThrow();
    }

    public synchronized int initializeMissingEmptyStockDeadlines() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE shops
                SET empty_stock_expires_at = ?,
                    last_empty_stock_warning_at = NULL,
                    updated_at = ?
                WHERE stock_amount <= 0 AND empty_stock_expires_at IS NULL
                """)) {
            String now = now();
            statement.setString(1, Instant.parse(now).plus(emptyStockAutoCloseDuration).toString());
            statement.setString(2, now);
            return statement.executeUpdate();
        }
    }

    public synchronized List<ShopRecord> expiredEmptyStockShops() throws SQLException {
        List<ShopRecord> shops = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM shops
                WHERE stock_amount <= 0
                  AND empty_stock_expires_at IS NOT NULL
                  AND empty_stock_expires_at <= ?
                ORDER BY id
                """)) {
            statement.setString(1, now());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    shops.add(readShop(resultSet));
                }
            }
        }
        return shops;
    }

    public synchronized void markEmptyStockWarningSent(long shopId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE shops
                SET last_empty_stock_warning_at = ?,
                    updated_at = ?
                WHERE id = ?
                """)) {
            String now = now();
            statement.setString(1, now);
            statement.setString(2, now);
            statement.setLong(3, shopId);
            statement.executeUpdate();
        }
    }

    public synchronized boolean deleteShop(long shopId, Player actor, boolean force) throws SQLException {
        try {
            connection.setAutoCommit(false);
            ShopRecord shop = findById(shopId).orElse(null);
            if (shop == null) {
                connection.rollback();
                return false;
            }
            if (!force && (shop.stockAmount() > 0 || shop.revenueDiamonds() > 0)) {
                connection.rollback();
                return false;
            }
            insertTransaction(shopId, actor, ShopTransactionType.DELETE, shop.stockAmount(), shop.revenueDiamonds());
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM shops WHERE id = ?")) {
                statement.setLong(1, shopId);
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
                    CREATE TABLE IF NOT EXISTS shops(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        world_name TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        owner_uuid TEXT NOT NULL,
                        owner_name TEXT NOT NULL,
                        item_data TEXT NOT NULL,
                        bundle_amount INTEGER NOT NULL,
                        price_diamonds INTEGER NOT NULL,
                        stock_amount INTEGER NOT NULL,
                        revenue_diamonds INTEGER NOT NULL,
                        empty_stock_expires_at TEXT,
                        last_empty_stock_warning_at TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        UNIQUE(world_name, x, y, z)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS shop_transactions(
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        shop_id INTEGER NOT NULL,
                        actor_uuid TEXT,
                        actor_name TEXT,
                        type TEXT NOT NULL,
                        item_amount INTEGER NOT NULL,
                        diamond_amount INTEGER NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            addColumnIfMissing(statement, "shops", "empty_stock_expires_at", "TEXT");
            addColumnIfMissing(statement, "shops", "last_empty_stock_warning_at", "TEXT");
        }
    }

    private void addStockUnchecked(long shopId, long amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE shops
                SET stock_amount = stock_amount + ?,
                    empty_stock_expires_at = NULL,
                    last_empty_stock_warning_at = NULL,
                    updated_at = ?
                WHERE id = ?
                """)) {
            statement.setLong(1, amount);
            statement.setString(2, now());
            statement.setLong(3, shopId);
            statement.executeUpdate();
        }
    }

    private void insertTransaction(
            long shopId,
            Player actor,
            ShopTransactionType type,
            long itemAmount,
            long diamondAmount
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO shop_transactions(shop_id, actor_uuid, actor_name, type, item_amount, diamond_amount, created_at)
                VALUES(?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, shopId);
            statement.setString(2, actor == null ? null : actor.getUniqueId().toString());
            statement.setString(3, actor == null ? null : actor.getName());
            statement.setString(4, type.name());
            statement.setLong(5, itemAmount);
            statement.setLong(6, diamondAmount);
            statement.setString(7, now());
            statement.executeUpdate();
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

    private String deadlineFromNow() {
        return Instant.now(clock).plus(emptyStockAutoCloseDuration).toString();
    }

    private String now() {
        return Instant.now(clock).toString();
    }

    private ShopRecord readShop(ResultSet resultSet) throws SQLException {
        return new ShopRecord(
                resultSet.getLong("id"),
                resultSet.getString("world_name"),
                resultSet.getInt("x"),
                resultSet.getInt("y"),
                resultSet.getInt("z"),
                UUID.fromString(resultSet.getString("owner_uuid")),
                resultSet.getString("owner_name"),
                itemCodec.deserialize(resultSet.getString("item_data")),
                resultSet.getInt("bundle_amount"),
                resultSet.getLong("price_diamonds"),
                resultSet.getLong("stock_amount"),
                resultSet.getLong("revenue_diamonds"),
                resultSet.getString("empty_stock_expires_at"),
                resultSet.getString("last_empty_stock_warning_at"),
                resultSet.getString("created_at"),
                resultSet.getString("updated_at")
        );
    }
}
