package de.tecca.zyrox.end.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import de.tecca.zyrox.end.service.DragonEggClaim;
import de.tecca.zyrox.end.service.DragonEggValidation;
import de.tecca.zyrox.end.service.EndDragonEggService;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class EndDragonEggListener implements Listener {

    private static final Set<InventoryType> PROCESSING_INVENTORIES = EnumSet.of(
            InventoryType.ANVIL,
            InventoryType.WORKBENCH,
            InventoryType.CRAFTING,
            InventoryType.ENCHANTING,
            InventoryType.SMITHING,
            InventoryType.GRINDSTONE,
            InventoryType.STONECUTTER,
            InventoryType.LOOM,
            InventoryType.CARTOGRAPHY,
            InventoryType.MERCHANT,
            InventoryType.CRAFTER
    );
    private static final long RECOVERY_WINDOW_TICKS = 3L;
    private static final double RECOVERY_RADIUS_SQUARED = 9.0D;

    private final JavaPlugin plugin;
    private final EndDragonEggService dragonEggService;
    private final NamespacedKey fallingEggKey;
    private final RecoveryRegistry pendingRecoveries = new RecoveryRegistry();
    private long lastConflictWarningAt;

    public EndDragonEggListener(JavaPlugin plugin, EndDragonEggService dragonEggService) {
        this.plugin = plugin;
        this.dragonEggService = dragonEggService;
        this.fallingEggKey = new NamespacedKey(plugin, "first_dragon_egg_falling");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)
                || dragon.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        if (dragonEggService.recordFirstDragonDeath(dragon.getWorld().getName())) {
            plugin.getLogger().info("First Ender Dragon death recorded in world " + dragon.getWorld().getName() + ".");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDragonEggPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack itemStack = event.getItem().getItemStack();
        if (itemStack.getType() != Material.DRAGON_EGG) {
            return;
        }

        if (dragonEggService.taggedFirstDragonEgg(itemStack)) {
            if (!validate(itemStack, player, "pickup")) {
                event.setCancelled(true);
                return;
            }
            event.getItem().setItemStack(itemStack);
            dragonEggService.trackItem(null);
            return;
        }
        if (!canAutoClaimUntaggedEgg(player.getWorld().getEnvironment())) {
            return;
        }

        Optional<DragonEggClaim> claim = dragonEggService.claimAndTagFirstEgg(
                itemStack,
                player.getUniqueId(),
                player.getName()
        );
        if (claim.isEmpty()) {
            return;
        }
        event.getItem().setItemStack(itemStack);
        plugin.getLogger().info("First Dragon Egg claimed by " + player.getName() + " (" + player.getUniqueId() + ").");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        ItemStack stack = item.getItemStack();
        if (!dragonEggService.taggedFirstDragonEgg(stack)) {
            return;
        }
        if (!validate(stack, event.getPlayer(), "drop")) {
            event.setCancelled(true);
            return;
        }
        item.setItemStack(stack);
        dragonEggService.trackItem(item.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlaceValidation(BlockPlaceEvent event) {
        ItemStack stack = event.getItemInHand();
        if (!dragonEggService.taggedFirstDragonEgg(stack)) {
            return;
        }
        boolean creative = event.getPlayer().getGameMode() == GameMode.CREATIVE;
        if (creative || !validate(stack, event.getPlayer(), "place")) {
            event.setCancelled(true);
            if (creative) {
                event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(
                        "Das erste Drachenei kann im Kreativmodus nicht platziert werden."
                ));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlaced(BlockPlaceEvent event) {
        if (dragonEggService.taggedFirstDragonEgg(event.getItemInHand())
                && event.getBlockPlaced().getType() == Material.DRAGON_EGG) {
            dragonEggService.trackPlaced(event.getBlockPlaced().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDragonEggTeleport(BlockFromToEvent event) {
        if (!dragonEggService.isTrackedBlock(event.getBlock().getLocation())) {
            return;
        }
        Location source = event.getBlock().getLocation();
        Location destination = event.getToBlock().getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (destination.getBlock().getType() == Material.DRAGON_EGG) {
                dragonEggService.trackPlaced(destination);
            } else if (source.getBlock().getType() == Material.DRAGON_EGG) {
                dragonEggService.trackPlaced(source);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallingBlockSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling)
                || falling.getBlockData().getMaterial() != Material.DRAGON_EGG) {
            return;
        }
        Location source = falling.getSourceLoc();
        if (source == null || !dragonEggService.isTrackedBlock(source)) {
            return;
        }
        UUID eggId = dragonEggService.state().eggId().orElse(null);
        if (eggId == null) {
            return;
        }
        falling.getPersistentDataContainer().set(fallingEggKey, PersistentDataType.STRING, eggId.toString());
        dragonEggService.trackFalling(source, falling.getUniqueId());
        registerRecovery(source, RecoveryReason.FALLING_SOURCE, falling.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling) || !trackedFalling(falling)) {
            return;
        }
        if (event.getTo() == Material.DRAGON_EGG) {
            dragonEggService.trackPlaced(event.getBlock().getLocation());
        } else {
            registerRecovery(event.getBlock().getLocation(), RecoveryReason.FALLING_BREAK, falling.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallingBlockDrop(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling) || !trackedFalling(falling)) {
            return;
        }
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!dragonEggService.restoreCanonicalEgg(stack)) {
            event.setCancelled(true);
            dragonEggService.trackLost();
            return;
        }
        event.getItemDrop().setItemStack(stack);
        dragonEggService.trackItem(event.getItemDrop().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFallingBlockRemove(EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling) || !trackedFalling(falling)) {
            return;
        }
        registerRecovery(falling.getLocation(), RecoveryReason.FALLING_BREAK, falling.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBlockDrop(BlockDropItemEvent event) {
        if (event.getBlockState().getType() != Material.DRAGON_EGG
                || !dragonEggService.isTrackedBlock(event.getBlockState().getLocation())) {
            return;
        }
        boolean restored = false;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (stack.getType() == Material.DRAGON_EGG && dragonEggService.restoreCanonicalEgg(stack)) {
                item.setItemStack(stack);
                dragonEggService.trackItem(item.getUniqueId());
                restored = true;
                break;
            }
        }
        if (!restored) {
            registerRecovery(event.getBlockState().getLocation(), RecoveryReason.BLOCK_DROP, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBrokenByBlock(BlockBreakBlockEvent event) {
        if (!dragonEggService.isTrackedBlock(event.getBlock().getLocation())) {
            return;
        }
        boolean restored = restoreFirstDrop(event.getDrops());
        if (restored) {
            dragonEggService.trackItem(null);
        } else {
            registerRecovery(event.getBlock().getLocation(), RecoveryReason.BLOCK_BREAK_BY_BLOCK, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        trackPistonMove(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        trackPistonMove(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        prepareExplosionDrop(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        prepareExplosionDrop(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack stack = item.getItemStack();
        if (stack.getType() != Material.DRAGON_EGG) {
            return;
        }
        if (dragonEggService.taggedFirstDragonEgg(stack)) {
            if (!validate(stack, null, "item spawn")) {
                event.setCancelled(true);
                return;
            }
            item.setItemStack(stack);
            dragonEggService.trackItem(item.getUniqueId());
            return;
        }
        PendingRecovery recovery = pendingRecoveries.findMatching(item.getLocation(), Bukkit.getCurrentTick()).orElse(null);
        if (recovery != null && dragonEggService.restoreCanonicalEgg(stack)) {
            pendingRecoveries.remove(recovery);
            item.setItemStack(stack);
            dragonEggService.trackItem(item.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        if (dragonEggService.taggedFirstDragonEgg(event.getEntity().getItemStack())
                || dragonEggService.taggedFirstDragonEgg(event.getTarget().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (validCanonical(event.getEntity().getItemStack())) {
            dragonEggService.trackLost();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)
                || !validCanonical(item.getItemStack())
                || !activeDestructionCause(event.getCause())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!item.isValid() || item.isDead()) {
                dragonEggService.trackLost();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        ItemStack stack = event.getItem();
        if (!dragonEggService.taggedFirstDragonEgg(stack)) {
            return;
        }
        if (!validate(stack, null, "inventory transfer")) {
            event.setCancelled(true);
            return;
        }
        if (PROCESSING_INVENTORIES.contains(event.getDestination().getType())) {
            event.setCancelled(true);
            return;
        }
        event.setItem(stack);
        dragonEggService.trackItem(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        ItemStack stack = event.getItem();
        if (!dragonEggService.taggedFirstDragonEgg(stack)) {
            return;
        }
        if (!validate(stack, null, "dispense")) {
            event.setCancelled(true);
            return;
        }
        event.setItem(stack);
        dragonEggService.trackItem(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreativeClone(InventoryCreativeEvent event) {
        if (dragonEggService.taggedFirstDragonEgg(event.getCursor())
                || dragonEggService.taggedFirstDragonEgg(event.getCurrentItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProcessingClick(InventoryClickEvent event) {
        if (!PROCESSING_INVENTORIES.contains(event.getView().getTopInventory().getType())) {
            return;
        }
        if (dragonEggService.taggedFirstDragonEgg(event.getCurrentItem())
                || dragonEggService.taggedFirstDragonEgg(event.getCursor())
                || hotbarEgg(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProcessingDrag(InventoryDragEvent event) {
        if (PROCESSING_INVENTORIES.contains(event.getView().getTopInventory().getType())
                && dragonEggService.taggedFirstDragonEgg(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (containsTaggedEgg(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (containsTaggedEgg(event.getInventory())) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (containsTaggedEgg(event.getInventory())) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (containsTaggedEgg(event.getInventory())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (dragonEggService.taggedFirstDragonEgg(event.getItem())) {
            event.setCancelled(true);
        }
    }

    private void trackPistonMove(List<Block> blocks, org.bukkit.block.BlockFace direction) {
        for (Block block : blocks) {
            if (!dragonEggService.isTrackedBlock(block.getLocation())) {
                continue;
            }
            Location source = block.getLocation();
            Location forward = block.getRelative(direction).getLocation();
            Location backward = block.getRelative(direction.getOppositeFace()).getLocation();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (forward.getBlock().getType() == Material.DRAGON_EGG) {
                    dragonEggService.trackPlaced(forward);
                } else if (backward.getBlock().getType() == Material.DRAGON_EGG) {
                    dragonEggService.trackPlaced(backward);
                } else if (source.getBlock().getType() != Material.DRAGON_EGG
                        && dragonEggService.isTrackedBlock(source)) {
                    registerRecovery(source, RecoveryReason.PISTON, null);
                }
            });
            return;
        }
    }

    private void registerRecovery(Location location, RecoveryReason reason, UUID fallingUniqueId) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        PendingRecovery recovery = pendingRecoveries.add(
                RecoveryKey.of(location),
                reason,
                fallingUniqueId,
                Bukkit.getCurrentTick(),
                RECOVERY_WINDOW_TICKS
        );
        Bukkit.getScheduler().runTaskLater(plugin, () -> expireRecovery(recovery), RECOVERY_WINDOW_TICKS);
    }

    private void expireRecovery(PendingRecovery recovery) {
        if (!pendingRecoveries.remove(recovery) || !recovery.expiresToLost()) {
            return;
        }
        if (recovery.optionalFallingUniqueId()
                .map(dragonEggService::isTrackedFalling)
                .orElseGet(() -> dragonEggService.isTrackedBlock(recovery.key().location()))) {
            dragonEggService.trackLost();
        }
    }

    private void prepareExplosionDrop(List<Block> blocks) {
        for (Block block : blocks) {
            if (!dragonEggService.isTrackedBlock(block.getLocation())) {
                continue;
            }
            registerRecovery(block.getLocation(), RecoveryReason.EXPLOSION, null);
            return;
        }
    }

    private boolean trackedFalling(FallingBlock falling) {
        String storedId = falling.getPersistentDataContainer().get(fallingEggKey, PersistentDataType.STRING);
        return storedId != null
                && dragonEggService.state().eggId().map(UUID::toString).filter(storedId::equals).isPresent()
                && dragonEggService.isTrackedFalling(falling.getUniqueId());
    }

    private boolean restoreFirstDrop(List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop.getType() == Material.DRAGON_EGG && dragonEggService.restoreCanonicalEgg(drop)) {
                return true;
            }
        }
        return false;
    }

    private boolean validate(ItemStack stack, Player player, String action) {
        DragonEggValidation validation = dragonEggService.validateAndCanonicalize(stack);
        if (validation == DragonEggValidation.VALID) {
            return true;
        }
        warnConflict(player, action, validation);
        return false;
    }

    private boolean validCanonical(ItemStack stack) {
        return dragonEggService.validateAndCanonicalize(stack) == DragonEggValidation.VALID;
    }

    private void warnConflict(Player player, String action, DragonEggValidation validation) {
        long now = System.currentTimeMillis();
        if (now - lastConflictWarningAt >= 5000L) {
            plugin.getLogger().warning("Blocked invalid first Dragon Egg during " + action + ": " + validation + ".");
            lastConflictWarningAt = now;
        }
        if (player != null) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Dieses Drachenei besitzt keine gültige erste Egg-ID."
            ));
        }
    }

    private boolean containsTaggedEgg(Inventory inventory) {
        return containsTaggedEgg(inventory.getContents());
    }

    private boolean containsTaggedEgg(ItemStack[] contents) {
        for (ItemStack stack : contents) {
            if (dragonEggService.taggedFirstDragonEgg(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean hotbarEgg(InventoryClickEvent event) {
        int button = event.getHotbarButton();
        if (button < 0 || !(event.getWhoClicked() instanceof Player player)) {
            return false;
        }
        return dragonEggService.taggedFirstDragonEgg(player.getInventory().getItem(button));
    }

    static boolean canAutoClaimUntaggedEgg(World.Environment environment) {
        return environment == World.Environment.THE_END;
    }

    static boolean activeDestructionCause(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
                || cause == EntityDamageEvent.DamageCause.CONTACT
                || cause == EntityDamageEvent.DamageCause.VOID;
    }

    enum RecoveryReason {
        FALLING_SOURCE(false),
        FALLING_BREAK(true),
        EXPLOSION(true),
        BLOCK_DROP(true),
        BLOCK_BREAK_BY_BLOCK(true),
        PISTON(true);

        private final boolean expiresToLost;

        RecoveryReason(boolean expiresToLost) {
            this.expiresToLost = expiresToLost;
        }
    }

    static final class RecoveryRegistry {

        private final Set<PendingRecovery> recoveries = new HashSet<>();
        private long nextRecoveryId;

        PendingRecovery add(RecoveryKey key, RecoveryReason reason, UUID fallingUniqueId, long currentTick, long ttlTicks) {
            PendingRecovery recovery = new PendingRecovery(
                    ++nextRecoveryId,
                    key,
                    reason,
                    fallingUniqueId,
                    currentTick + ttlTicks
            );
            recoveries.add(recovery);
            return recovery;
        }

        boolean remove(PendingRecovery recovery) {
            return recoveries.remove(recovery);
        }

        Optional<PendingRecovery> findMatching(Location location, long currentTick) {
            if (location == null || location.getWorld() == null) {
                return Optional.empty();
            }
            UUID worldId = location.getWorld().getUID();
            return findMatching(worldId, location.getX(), location.getY(), location.getZ(), currentTick);
        }

        Optional<PendingRecovery> findMatching(UUID worldId, double x, double y, double z, long currentTick) {
            return recoveries.stream()
                    .filter(recovery -> !recovery.expired(currentTick))
                    .filter(recovery -> recovery.key().matches(worldId, x, y, z))
                    .min((left, right) -> Double.compare(
                            left.key().distanceSquared(x, y, z),
                            right.key().distanceSquared(x, y, z)
                    ));
        }
    }

    record PendingRecovery(
            long id,
            RecoveryKey key,
            RecoveryReason reason,
            UUID fallingUniqueId,
            long expiresAtTick
    ) {
        Optional<UUID> optionalFallingUniqueId() {
            return Optional.ofNullable(fallingUniqueId);
        }

        boolean expired(long currentTick) {
            return currentTick > expiresAtTick;
        }

        boolean expiresToLost() {
            return reason.expiresToLost;
        }
    }

    record RecoveryKey(UUID worldUniqueId, String worldName, int x, int y, int z) {
        static RecoveryKey of(Location location) {
            return new RecoveryKey(
                    location.getWorld().getUID(),
                    location.getWorld().getName(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
        }

        boolean matches(UUID worldId, double itemX, double itemY, double itemZ) {
            if (!worldUniqueId.equals(worldId)) {
                return false;
            }
            return distanceSquared(itemX, itemY, itemZ) <= RECOVERY_RADIUS_SQUARED;
        }

        double distanceSquared(double itemX, double itemY, double itemZ) {
            double dx = x + 0.5D - itemX;
            double dy = y + 0.5D - itemY;
            double dz = z + 0.5D - itemZ;
            return dx * dx + dy * dy + dz * dz;
        }

        Location location() {
            World world = Bukkit.getWorld(worldUniqueId);
            if (world == null) {
                world = Bukkit.getWorld(worldName);
            }
            return world == null ? null : new Location(world, x, y, z);
        }
    }
}
