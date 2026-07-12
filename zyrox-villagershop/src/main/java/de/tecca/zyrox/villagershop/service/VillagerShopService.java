package de.tecca.zyrox.villagershop.service;

import de.tecca.zyrox.villagershop.storage.VillagerShopRecord;
import de.tecca.zyrox.villagershop.storage.VillagerShopStorage;
import de.tecca.zyrox.villagershop.storage.VillagerTradeRecord;
import de.tecca.zyrox.villagershop.text.VillagerShopText;
import de.tecca.zyrox.villagershop.util.ItemStacks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class VillagerShopService {

    private final JavaPlugin plugin;
    private final VillagerShopStorage storage;
    private final VillagerShopText text;
    private final NamespacedKey shopIdKey;
    private final NamespacedKey displayIdKey;
    private BukkitTask lockTask;

    public VillagerShopService(JavaPlugin plugin, VillagerShopStorage storage, VillagerShopText text) {
        this.plugin = plugin;
        this.storage = storage;
        this.text = text;
        this.shopIdKey = new NamespacedKey(plugin, "villager_shop_id");
        this.displayIdKey = new NamespacedKey(plugin, "villager_shop_display_id");
    }

    public NamespacedKey shopIdKey() {
        return shopIdKey;
    }

    public Optional<Long> managedShopId(Entity entity) {
        if (!(entity instanceof Villager)) {
            return Optional.empty();
        }
        Long id = entity.getPersistentDataContainer().get(shopIdKey, PersistentDataType.LONG);
        return id == null ? Optional.empty() : Optional.of(id);
    }

    public Optional<Long> managedDisplayShopId(Entity entity) {
        if (!(entity instanceof TextDisplay)) {
            return Optional.empty();
        }
        Long id = entity.getPersistentDataContainer().get(displayIdKey, PersistentDataType.LONG);
        return id == null ? Optional.empty() : Optional.of(id);
    }

    public Optional<VillagerShopRecord> findById(long id) {
        try {
            return storage.findById(id);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load villager shop: " + exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<VillagerShopRecord> findByEntity(Entity entity) {
        Optional<Long> id = managedShopId(entity);
        return id.isEmpty() ? Optional.empty() : findById(id.get());
    }

    public List<VillagerShopRecord> allShops() throws SQLException {
        return storage.allShops();
    }

    public Optional<VillagerShopRecord> targetedShop(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                6.0D,
                entity -> entity instanceof Villager
        );
        if (result == null || result.getHitEntity() == null) {
            return Optional.empty();
        }
        return findByEntity(result.getHitEntity());
    }

    public VillagerShopRecord createShop(Player player, String displayName) throws SQLException {
        Location location = player.getLocation();
        String name = displayName == null || displayName.isBlank()
                ? plugin.getConfig().getString("villager-shop.default-name", "Villager Shop")
                : displayName.trim();
        String profession = normalizeProfession(plugin.getConfig().getString("villager-shop.default-profession", "NONE"));
        ItemStack input = defaultItem("villager-shop.default-input-material", Material.EMERALD);
        ItemStack output = defaultItem("villager-shop.default-output-material", Material.BREAD);
        int maxUses = Math.max(1, plugin.getConfig().getInt("villager-shop.default-max-uses", 16));
        boolean unlimited = plugin.getConfig().getBoolean("villager-shop.default-unlimited", true);

        Villager villager = spawnVillager(location);
        try {
            VillagerShopRecord shop = storage.createShop(villager.getUniqueId(), location, name, profession, input, null, output, maxUses, unlimited);
            applyToVillager(villager, shop);
            return shop;
        } catch (SQLException exception) {
            villager.remove();
            throw exception;
        }
    }

    public VillagerShopRecord updateTrade(
            long shopId,
            long tradeId,
            String profession,
            ItemStack inputOne,
            ItemStack inputTwo,
            ItemStack output,
            int maxUses,
            int currentUses,
            boolean unlimited
    ) throws SQLException {
        storage.updateProfession(shopId, normalizeProfession(profession));
        VillagerShopRecord updated = storage.updateTrade(
                tradeId,
                ItemStacks.tradeItem(inputOne),
                inputTwo == null || inputTwo.getType().isAir() ? null : ItemStacks.tradeItem(inputTwo),
                ItemStacks.tradeItem(output),
                maxUses,
                unlimited ? 0 : Math.min(currentUses, Math.max(1, maxUses)),
                unlimited
        );
        applyToStoredEntity(updated);
        return updated;
    }

    public VillagerShopRecord addTrade(VillagerShopRecord shop) throws SQLException {
        VillagerTradeRecord template = shop.tradeAt(Math.max(0, shop.trades().size() - 1));
        VillagerShopRecord updated = storage.addTrade(
                shop.id(),
                template.inputOne(),
                template.inputTwo(),
                template.output(),
                template.maxUses(),
                template.unlimited()
        );
        applyToStoredEntity(updated);
        return updated;
    }

    public VillagerShopRecord removeTrade(long tradeId) throws SQLException {
        VillagerShopRecord updated = storage.deleteTrade(tradeId);
        applyToStoredEntity(updated);
        return updated;
    }

    public VillagerShopRecord resetUses(long tradeId) throws SQLException {
        VillagerShopRecord updated = storage.updateTradeUses(tradeId, 0);
        applyToStoredEntity(updated);
        return updated;
    }

    public VillagerShopRecord rename(long id, String displayName) throws SQLException {
        VillagerShopRecord updated = storage.updateName(id, displayName == null || displayName.isBlank() ? "Villager Shop" : displayName.trim());
        applyToStoredEntity(updated);
        return updated;
    }

    public VillagerShopRecord updateDisplayText(long id, String displayText) throws SQLException {
        VillagerShopRecord updated = storage.updateDisplayText(id, displayText == null ? "" : displayText.trim());
        applyToStoredEntity(updated);
        return updated;
    }

    public VillagerShopRecord teleport(long id, Location location) throws SQLException {
        VillagerShopRecord shop = storage.findById(id).orElseThrow();
        Villager villager = findVillager(shop).orElseGet(() -> spawnVillager(location));
        villager.teleport(location);
        VillagerShopRecord moved = storage.updateLocation(id, location);
        if (!villager.getUniqueId().equals(moved.entityUuid())) {
            moved = storage.updateEntityUuid(id, villager.getUniqueId());
        }
        applyToVillager(villager, moved);
        return moved;
    }

    public boolean remove(long id) throws SQLException {
        Optional<VillagerShopRecord> shop = storage.findById(id);
        if (shop.isPresent()) {
            findVillager(shop.get()).ifPresent(Entity::remove);
            removeTextDisplays(shop.get().id());
        }
        return storage.deleteShop(id);
    }

    public void recordCompletedTrade(VillagerShopRecord shop, VillagerTradeRecord trade) {
        if (trade.unlimited()) {
            applyToStoredEntity(shop);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                VillagerShopRecord current = storage.findById(shop.id()).orElse(null);
                if (current == null) {
                    return;
                }
                VillagerTradeRecord currentTrade = current.trades().stream()
                        .filter(candidate -> candidate.id() == trade.id())
                        .findFirst()
                        .orElse(null);
                if (currentTrade == null || currentTrade.unlimited()) {
                    return;
                }
                int updatedUses = Math.min(currentTrade.maxUses(), currentTrade.currentUses() + 1);
                VillagerShopRecord updated = storage.updateTradeUses(currentTrade.id(), updatedUses);
                applyToStoredEntity(updated);
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not persist villager trade use: " + exception.getMessage());
            }
        });
    }

    public void restoreAll() {
        try {
            List<VillagerShopRecord> shops = storage.allShops();
            for (VillagerShopRecord shop : shops) {
                Optional<Villager> existing = findVillager(shop);
                if (existing.isPresent()) {
                    applyToVillager(existing.get(), shop);
                    continue;
                }
                if (Bukkit.getWorld(shop.worldName()) == null) {
                    plugin.getLogger().warning("Could not restore villager shop " + shop.id() + ": world is not loaded.");
                    continue;
                }
                Villager spawned = spawnVillager(shop.location());
                VillagerShopRecord updated = storage.updateEntityUuid(shop.id(), spawned.getUniqueId());
                applyToVillager(spawned, updated);
            }
            cleanupManagedEntities(storage.allShops());
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not restore villager shops: " + exception.getMessage());
        }
    }

    public void startLockTask() {
        stopLockTask();
        long period = Math.max(1L, plugin.getConfig().getLong("villager-shop.lock-period-ticks", 20L));
        lockTask = Bukkit.getScheduler().runTaskTimer(plugin, this::enforceLocks, period, period);
    }

    public void stopLockTask() {
        if (lockTask != null) {
            lockTask.cancel();
            lockTask = null;
        }
    }

    public String formatInfoLine(String key, VillagerShopRecord shop) {
        return text.message(key, text.placeholders(shop));
    }

    public Optional<VillagerTradeRecord> matchingTrade(VillagerShopRecord shop, MerchantRecipe recipe) {
        for (VillagerTradeRecord trade : shop.trades()) {
            if (matchesRecipe(trade, recipe)) {
                return Optional.of(trade);
            }
        }
        return Optional.empty();
    }

    public String normalizeProfession(String raw) {
        String value = raw == null || raw.isBlank() ? "NONE" : raw.trim().toUpperCase(Locale.ROOT);
        try {
            Villager.Profession.valueOf(value);
            return value;
        } catch (IllegalArgumentException exception) {
            return "NONE";
        }
    }

    public String nextProfession(String current) {
        Villager.Profession[] professions = Villager.Profession.values();
        String normalized = normalizeProfession(current);
        for (int index = 0; index < professions.length; index++) {
            if (professions[index].name().equals(normalized)) {
                return professions[(index + 1) % professions.length].name();
            }
        }
        return professions.length == 0 ? "NONE" : professions[0].name();
    }

    private Villager spawnVillager(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Cannot spawn villager without a world.");
        }
        return world.spawn(location, Villager.class);
    }

    private Optional<Villager> findVillager(VillagerShopRecord shop) {
        Entity entity = Bukkit.getEntity(shop.entityUuid());
        return entity instanceof Villager villager ? Optional.of(villager) : Optional.empty();
    }

    private void applyToStoredEntity(VillagerShopRecord shop) {
        findVillager(shop).ifPresent(villager -> applyToVillager(villager, shop));
    }

    private void applyToVillager(Villager villager, VillagerShopRecord shop) {
        villager.getPersistentDataContainer().set(shopIdKey, PersistentDataType.LONG, shop.id());
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setInvulnerable(true);
        villager.setAware(false);
        villager.setCollidable(false);
        villager.setSilent(true);
        villager.setCustomName(shop.displayName());
        villager.setCustomNameVisible(false);
        villager.setAdult();
        villager.setVillagerLevel(5);
        villager.setProfession(Villager.Profession.valueOf(normalizeProfession(shop.profession())));
        villager.setRecipes(shop.trades().stream()
                .map(VillagerTradeFactory::createRecipe)
                .toList());
        villager.setVelocity(new Vector(0, 0, 0));
        upsertTextDisplay(shop);
    }

    private boolean matchesRecipe(VillagerTradeRecord trade, MerchantRecipe recipe) {
        List<ItemStack> ingredients = recipe.getIngredients();
        if (ingredients.size() < 1 || ingredients.size() > 2) {
            return false;
        }
        if (!sameItem(trade.inputOne(), ingredients.get(0))) {
            return false;
        }
        if (trade.hasSecondInput()) {
            return ingredients.size() == 2 && sameItem(trade.inputTwo(), ingredients.get(1));
        }
        return ingredients.size() == 1;
    }

    private boolean sameItem(ItemStack expected, ItemStack actual) {
        if (expected == null || actual == null || expected.getType() != actual.getType()) {
            return false;
        }
        ItemStack expectedCopy = expected.clone();
        ItemStack actualCopy = actual.clone();
        expectedCopy.setAmount(1);
        actualCopy.setAmount(1);
        return expected.getAmount() == actual.getAmount() && expectedCopy.isSimilar(actualCopy);
    }

    private void enforceLocks() {
        double maxDistanceSquared = Math.max(0.0D, plugin.getConfig().getDouble("villager-shop.lock-distance-squared", 0.04D));
        try {
            for (VillagerShopRecord shop : storage.allShops()) {
                Optional<Villager> optional = findVillager(shop);
                if (optional.isEmpty()) {
                    continue;
                }
                Villager villager = optional.get();
                Location target = shop.location();
                if (target.getWorld() == null || !villager.getWorld().equals(target.getWorld())) {
                    continue;
                }
                villager.setVelocity(new Vector(0, 0, 0));
                villager.setAware(false);
                upsertTextDisplay(shop);
                if (villager.getLocation().distanceSquared(target) > maxDistanceSquared) {
                    villager.teleport(target);
                } else {
                    villager.setRotation(target.getYaw(), target.getPitch());
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not enforce villager shop locks: " + exception.getMessage());
        }
    }

    private void cleanupManagedEntities(List<VillagerShopRecord> shops) {
        Set<Long> knownIds = new HashSet<>();
        Map<Long, java.util.UUID> expectedEntities = shops.stream()
                .collect(java.util.stream.Collectors.toMap(VillagerShopRecord::id, VillagerShopRecord::entityUuid));
        knownIds.addAll(expectedEntities.keySet());

        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                Long id = villager.getPersistentDataContainer().get(shopIdKey, PersistentDataType.LONG);
                if (id == null) {
                    continue;
                }
                if (!knownIds.contains(id) || !villager.getUniqueId().equals(expectedEntities.get(id))) {
                    villager.remove();
                }
            }
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                Long id = display.getPersistentDataContainer().get(displayIdKey, PersistentDataType.LONG);
                if (id == null) {
                    continue;
                }
                if (!knownIds.contains(id)) {
                    display.remove();
                }
            }
        }
    }

    private void upsertTextDisplay(VillagerShopRecord shop) {
        Location location = displayLocation(shop);
        if (location.getWorld() == null) {
            return;
        }
        TextDisplay display = findTextDisplay(shop.id()).orElseGet(() -> spawnTextDisplay(location, shop.id()));
        if (!display.getWorld().equals(location.getWorld()) || display.getLocation().distanceSquared(location) > 0.01D) {
            display.teleport(location);
        }
        display.setText(shop.displayText());
        display.setBillboard(Display.Billboard.CENTER);
        display.setPersistent(true);
        display.setCustomName("VillagerShop Display #" + shop.id());
        display.setCustomNameVisible(false);
    }

    private TextDisplay spawnTextDisplay(Location location, long shopId) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
        display.getPersistentDataContainer().set(displayIdKey, PersistentDataType.LONG, shopId);
        return display;
    }

    private Optional<TextDisplay> findTextDisplay(long shopId) {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                Long id = display.getPersistentDataContainer().get(displayIdKey, PersistentDataType.LONG);
                if (id != null && id == shopId) {
                    return Optional.of(display);
                }
            }
        }
        return Optional.empty();
    }

    private void removeTextDisplays(long shopId) {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                Long id = display.getPersistentDataContainer().get(displayIdKey, PersistentDataType.LONG);
                if (id != null && id == shopId) {
                    display.remove();
                }
            }
        }
    }

    private Location displayLocation(VillagerShopRecord shop) {
        Location location = shop.location();
        double offset = plugin.getConfig().getDouble("villager-shop.display-offset-y", 2.35D);
        return location.add(0.0D, offset, 0.0D);
    }

    private ItemStack defaultItem(String path, Material fallback) {
        String configured = plugin.getConfig().getString(path, fallback.name());
        Material material = configured == null ? fallback : Material.matchMaterial(configured);
        if (material == null || material.isAir()) {
            material = fallback;
        }
        return new ItemStack(material);
    }
}
