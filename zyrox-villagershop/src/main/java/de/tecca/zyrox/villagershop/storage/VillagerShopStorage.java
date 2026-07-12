package de.tecca.zyrox.villagershop.storage;

import de.tecca.zyrox.villagershop.util.ItemStacks;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class VillagerShopStorage implements AutoCloseable {

    private final File databaseFile;
    private final VillagerShopItemCodec itemCodec;
    private Connection connection;

    public VillagerShopStorage(File databaseFile) {
        this(databaseFile, new VillagerShopItemCodec() {
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

    public VillagerShopStorage(File databaseFile, VillagerShopItemCodec itemCodec) {
        this.databaseFile = databaseFile;
        this.itemCodec = itemCodec;
    }

    public synchronized void open() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create storage directory: " + parent);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("PRAGMA foreign_keys = ON");
        }
        migrate();
    }

    public synchronized VillagerShopRecord createShop(
            UUID entityUuid,
            Location location,
            String displayName,
            String profession,
            ItemStack inputOne,
            ItemStack inputTwo,
            ItemStack output,
            int maxUses,
            boolean unlimited
    ) throws SQLException {
        String now = Instant.now().toString();
        try {
            connection.setAutoCommit(false);
            long shopId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO villager_shops(entity_uuid, world_name, x, y, z, yaw, pitch, display_name,
                                               display_text, profession, created_at, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, entityUuid.toString());
                statement.setString(2, worldName(location));
                statement.setDouble(3, location.getX());
                statement.setDouble(4, location.getY());
                statement.setDouble(5, location.getZ());
                statement.setFloat(6, location.getYaw());
                statement.setFloat(7, location.getPitch());
                statement.setString(8, displayName);
                statement.setString(9, displayName);
                statement.setString(10, profession);
                statement.setString(11, now);
                statement.setString(12, now);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Villager shop insert did not return an id.");
                    }
                    shopId = keys.getLong(1);
                }
            }
            insertTrade(shopId, 0, inputOne, inputTwo, output, maxUses, 0, unlimited, now);
            connection.commit();
            return findById(shopId).orElseThrow();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public synchronized Optional<VillagerShopRecord> findById(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM villager_shops WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readShop(resultSet)) : Optional.empty();
            }
        }
    }

    public synchronized Optional<VillagerShopRecord> findByEntityUuid(UUID entityUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM villager_shops WHERE entity_uuid = ?")) {
            statement.setString(1, entityUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readShop(resultSet)) : Optional.empty();
            }
        }
    }

    public synchronized List<VillagerShopRecord> allShops() throws SQLException {
        List<VillagerShopRecord> shops = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM villager_shops ORDER BY id");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                shops.add(readShop(resultSet));
            }
        }
        return shops;
    }

    public synchronized VillagerShopRecord updateProfession(long shopId, String profession) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE villager_shops
                SET profession = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setString(1, profession);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, shopId);
            statement.executeUpdate();
        }
        return findById(shopId).orElseThrow();
    }

    public synchronized VillagerShopRecord addTrade(
            long shopId,
            ItemStack inputOne,
            ItemStack inputTwo,
            ItemStack output,
            int maxUses,
            boolean unlimited
    ) throws SQLException {
        String now = Instant.now().toString();
        int slot = nextTradeSlot(shopId);
        insertTrade(shopId, slot, inputOne, inputTwo, output, maxUses, 0, unlimited, now);
        touchShop(shopId);
        return findById(shopId).orElseThrow();
    }

    public synchronized VillagerShopRecord updateTrade(
            long tradeId,
            ItemStack inputOne,
            ItemStack inputTwo,
            ItemStack output,
            int maxUses,
            int currentUses,
            boolean unlimited
    ) throws SQLException {
        long shopId = shopIdForTrade(tradeId);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE villager_shop_trades
                SET input_one_data = ?, input_two_data = ?, output_data = ?,
                    max_uses = ?, current_uses = ?, unlimited = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setString(1, itemCodec.serialize(inputOne));
            statement.setString(2, inputTwo == null ? null : itemCodec.serialize(inputTwo));
            statement.setString(3, itemCodec.serialize(output));
            statement.setInt(4, Math.max(1, maxUses));
            statement.setInt(5, Math.max(0, currentUses));
            statement.setBoolean(6, unlimited);
            statement.setString(7, Instant.now().toString());
            statement.setLong(8, tradeId);
            statement.executeUpdate();
        }
        touchShop(shopId);
        return findById(shopId).orElseThrow();
    }

    public synchronized VillagerShopRecord updateTradeUses(long tradeId, int currentUses) throws SQLException {
        long shopId = shopIdForTrade(tradeId);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE villager_shop_trades
                SET current_uses = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setInt(1, Math.max(0, currentUses));
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, tradeId);
            statement.executeUpdate();
        }
        touchShop(shopId);
        return findById(shopId).orElseThrow();
    }

    public synchronized VillagerShopRecord deleteTrade(long tradeId) throws SQLException {
        long shopId = shopIdForTrade(tradeId);
        if (tradeCount(shopId) <= 1) {
            return findById(shopId).orElseThrow();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM villager_shop_trades WHERE id = ?")) {
            statement.setLong(1, tradeId);
            statement.executeUpdate();
        }
        resequenceTrades(shopId);
        touchShop(shopId);
        return findById(shopId).orElseThrow();
    }

    public synchronized VillagerShopRecord updateName(long id, String displayName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE villager_shops
                SET display_name = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setString(1, displayName);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, id);
            statement.executeUpdate();
        }
        return findById(id).orElseThrow();
    }

    public synchronized VillagerShopRecord updateDisplayText(long id, String displayText) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE villager_shops
                SET display_text = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setString(1, displayText);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, id);
            statement.executeUpdate();
        }
        return findById(id).orElseThrow();
    }

    public synchronized VillagerShopRecord updateLocation(long id, Location location) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE villager_shops
                SET world_name = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setString(1, worldName(location));
            statement.setDouble(2, location.getX());
            statement.setDouble(3, location.getY());
            statement.setDouble(4, location.getZ());
            statement.setFloat(5, location.getYaw());
            statement.setFloat(6, location.getPitch());
            statement.setString(7, Instant.now().toString());
            statement.setLong(8, id);
            statement.executeUpdate();
        }
        return findById(id).orElseThrow();
    }

    public synchronized VillagerShopRecord updateEntityUuid(long id, UUID entityUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE villager_shops
                SET entity_uuid = ?, updated_at = ?
                WHERE id = ?
                """)) {
            statement.setString(1, entityUuid.toString());
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, id);
            statement.executeUpdate();
        }
        return findById(id).orElseThrow();
    }

    public synchronized boolean deleteShop(long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM villager_shops WHERE id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
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
        if (tableExists("villager_shops") && tableHasColumn("villager_shops", "input_one_data")) {
            migrateSingleTradeTable();
        }
        try (Statement statement = connection.createStatement()) {
            createTables(statement);
        }
        ensureDisplayTextColumn();
    }

    private void migrateSingleTradeTable() throws SQLException {
        try {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE villager_shops RENAME TO villager_shops_legacy");
                createTables(statement);
                statement.executeUpdate("""
                        INSERT INTO villager_shops(id, entity_uuid, world_name, x, y, z, yaw, pitch,
                                                   display_name, display_text, profession, created_at, updated_at)
                        SELECT id, entity_uuid, world_name, x, y, z, yaw, pitch,
                               display_name, display_name, profession, created_at, updated_at
                        FROM villager_shops_legacy
                        """);
                statement.executeUpdate("""
                        INSERT INTO villager_shop_trades(shop_id, slot, input_one_data, input_two_data,
                                                         output_data, max_uses, current_uses, unlimited,
                                                         created_at, updated_at)
                        SELECT id, 0, input_one_data, input_two_data, output_data,
                               max_uses, current_uses, unlimited, created_at, updated_at
                        FROM villager_shops_legacy
                        """);
                statement.executeUpdate("DROP TABLE villager_shops_legacy");
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void createTables(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS villager_shops(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_uuid TEXT NOT NULL UNIQUE,
                    world_name TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL NOT NULL,
                    pitch REAL NOT NULL,
                    display_name TEXT NOT NULL,
                    display_text TEXT NOT NULL,
                    profession TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS villager_shop_trades(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    shop_id INTEGER NOT NULL,
                    slot INTEGER NOT NULL,
                    input_one_data TEXT NOT NULL,
                    input_two_data TEXT,
                    output_data TEXT NOT NULL,
                    max_uses INTEGER NOT NULL,
                    current_uses INTEGER NOT NULL,
                    unlimited INTEGER NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    UNIQUE(shop_id, slot),
                    FOREIGN KEY(shop_id) REFERENCES villager_shops(id) ON DELETE CASCADE
                )
                """);
    }

    private void insertTrade(
            long shopId,
            int slot,
            ItemStack inputOne,
            ItemStack inputTwo,
            ItemStack output,
            int maxUses,
            int currentUses,
            boolean unlimited,
            String now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO villager_shop_trades(shop_id, slot, input_one_data, input_two_data, output_data,
                                                 max_uses, current_uses, unlimited, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, shopId);
            statement.setInt(2, slot);
            statement.setString(3, itemCodec.serialize(inputOne));
            statement.setString(4, inputTwo == null ? null : itemCodec.serialize(inputTwo));
            statement.setString(5, itemCodec.serialize(output));
            statement.setInt(6, Math.max(1, maxUses));
            statement.setInt(7, Math.max(0, currentUses));
            statement.setBoolean(8, unlimited);
            statement.setString(9, now);
            statement.setString(10, now);
            statement.executeUpdate();
        }
    }

    private VillagerShopRecord readShop(ResultSet resultSet) throws SQLException {
        long shopId = resultSet.getLong("id");
        return new VillagerShopRecord(
                shopId,
                UUID.fromString(resultSet.getString("entity_uuid")),
                resultSet.getString("world_name"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getFloat("yaw"),
                resultSet.getFloat("pitch"),
                resultSet.getString("display_name"),
                resultSet.getString("display_text"),
                resultSet.getString("profession"),
                tradesFor(shopId),
                resultSet.getString("created_at"),
                resultSet.getString("updated_at")
        );
    }

    private List<VillagerTradeRecord> tradesFor(long shopId) throws SQLException {
        List<VillagerTradeRecord> trades = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM villager_shop_trades
                WHERE shop_id = ?
                ORDER BY slot
                """)) {
            statement.setLong(1, shopId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String inputTwoData = resultSet.getString("input_two_data");
                    trades.add(new VillagerTradeRecord(
                            resultSet.getLong("id"),
                            resultSet.getLong("shop_id"),
                            resultSet.getInt("slot"),
                            itemCodec.deserialize(resultSet.getString("input_one_data")),
                            inputTwoData == null ? null : itemCodec.deserialize(inputTwoData),
                            itemCodec.deserialize(resultSet.getString("output_data")),
                            resultSet.getInt("max_uses"),
                            resultSet.getInt("current_uses"),
                            resultSet.getBoolean("unlimited"),
                            resultSet.getString("created_at"),
                            resultSet.getString("updated_at")
                    ));
                }
            }
        }
        return trades;
    }

    private int nextTradeSlot(long shopId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(slot), -1) + 1 FROM villager_shop_trades WHERE shop_id = ?")) {
            statement.setLong(1, shopId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private int tradeCount(long shopId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM villager_shop_trades WHERE shop_id = ?")) {
            statement.setLong(1, shopId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private long shopIdForTrade(long tradeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT shop_id FROM villager_shop_trades WHERE id = ?")) {
            statement.setLong(1, tradeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }
        throw new SQLException("Unknown villager trade: " + tradeId);
    }

    private void resequenceTrades(long shopId) throws SQLException {
        List<Long> tradeIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM villager_shop_trades WHERE shop_id = ? ORDER BY slot")) {
            statement.setLong(1, shopId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tradeIds.add(resultSet.getLong("id"));
                }
            }
        }
        for (int index = 0; index < tradeIds.size(); index++) {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE villager_shop_trades SET slot = ? WHERE id = ?")) {
                statement.setInt(1, index);
                statement.setLong(2, tradeIds.get(index));
                statement.executeUpdate();
            }
        }
    }

    private void touchShop(long shopId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE villager_shops SET updated_at = ? WHERE id = ?")) {
            statement.setString(1, Instant.now().toString());
            statement.setLong(2, shopId);
            statement.executeUpdate();
        }
    }

    private void ensureDisplayTextColumn() throws SQLException {
        if (tableHasColumn("villager_shops", "display_text")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE villager_shops ADD COLUMN display_text TEXT NOT NULL DEFAULT ''");
            statement.executeUpdate("UPDATE villager_shops SET display_text = display_name WHERE display_text = ''");
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, tableName, null)) {
            return resultSet.next();
        }
    }

    private boolean tableHasColumn(String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(null, null, tableName, columnName)) {
            return resultSet.next();
        }
    }

    private String worldName(Location location) throws SQLException {
        if (location.getWorld() == null) {
            throw new SQLException("Location has no world.");
        }
        return location.getWorld().getName();
    }
}
