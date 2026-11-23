package de.tecca.zyrox.end.listener;

import de.tecca.zyrox.end.service.DragonEggMiningRules;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class EndDragonEggMiningService implements Listener {

    private static final double MAX_MINING_DISTANCE_SQUARED = 36.0D;

    private final JavaPlugin plugin;
    private final Map<UUID, MiningSession> sessions = new HashMap<>();
    private final Set<BreakAllowance> breakAllowances = new HashSet<>();
    private BukkitTask miningTask;

    public EndDragonEggMiningService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (miningTask != null) {
            return;
        }
        miningTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (miningTask != null) {
            miningTask.cancel();
            miningTask = null;
        }
        sessions.values().forEach(MiningSession::clearBlockDamage);
        sessions.clear();
        breakAllowances.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDragonEggTeleport(BlockFromToEvent event) {
        if (DragonEggMiningRules.isDragonEgg(event.getBlock().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!DragonEggMiningRules.isDragonEgg(block.getType())) {
            return;
        }
        MiningMode miningMode = miningMode(event.getPlayer().getGameMode());
        if (miningMode == MiningMode.INSTANT) {
            return;
        }
        event.setCancelled(true);
        if (miningMode == MiningMode.SESSION) {
            startOrBlock(event.getPlayer(), block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!DragonEggMiningRules.isDragonEgg(event.getBlock().getType())) {
            return;
        }

        MiningMode miningMode = miningMode(event.getPlayer().getGameMode());
        if (miningMode == MiningMode.INSTANT) {
            cancelSession(event.getPlayer().getUniqueId());
            event.setInstaBreak(true);
            return;
        }
        if (miningMode == MiningMode.IGNORED) {
            event.setCancelled(true);
            return;
        }

        event.setInstaBreak(false);
        event.setCancelled(true);
        startOrBlock(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        if (DragonEggMiningRules.isDragonEgg(event.getBlock().getType())) {
            cancelSession(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!DragonEggMiningRules.isDragonEgg(event.getBlock().getType())) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            cancelSession(event.getPlayer().getUniqueId());
            return;
        }
        if (!breakAllowances.contains(BreakAllowance.of(event.getPlayer(), event.getBlock()))) {
            event.setCancelled(true);
        }
    }

    private void startOrBlock(Player player, Block block) {
        if (miningMode(player.getGameMode()) != MiningMode.SESSION) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!DragonEggMiningRules.isObsidianCapableTool(tool.getType())) {
            cancelSession(player.getUniqueId());
            player.sendBlockDamage(block.getLocation(), 0.0F);
            return;
        }

        UUID playerId = player.getUniqueId();
        MiningSession existing = sessions.get(playerId);
        ToolSignature toolSignature = ToolSignature.of(tool);
        BlockKey blockKey = BlockKey.of(block);
        if (existing != null && existing.sameTarget(blockKey) && existing.sameTool(toolSignature)) {
            return;
        }
        cancelSession(playerId);
        sessions.put(playerId, new MiningSession(player, blockKey, block.getLocation(), toolSignature));
    }

    private void tick() {
        for (MiningSession session : Set.copyOf(sessions.values())) {
            if (!session.valid()) {
                cancelSession(session.playerId());
                continue;
            }

            Optional<Block> block = session.block();
            if (block.isEmpty()) {
                cancelSession(session.playerId());
                continue;
            }

            ItemStack tool = session.player().getInventory().getItemInMainHand();
            if (!session.sameTool(ToolSignature.of(tool))) {
                cancelSession(session.playerId());
                continue;
            }

            session.addProgress(damagePerTick(session.player(), tool));
            if (session.complete()) {
                completeSession(session, block.get());
            } else {
                session.sendProgress();
            }
        }
    }

    private void completeSession(MiningSession session, Block block) {
        sessions.remove(session.playerId());
        session.clearBlockDamage();

        BreakAllowance allowance = BreakAllowance.of(session.player(), block);
        breakAllowances.add(allowance);
        try {
            session.player().breakBlock(block);
        } finally {
            breakAllowances.remove(allowance);
        }
    }

    private double damagePerTick(Player player, ItemStack tool) {
        return DragonEggMiningRules.damagePerTick(
                tool.getType(),
                efficiencyLevel(tool),
                potionAmplifier(player, PotionEffectType.HASTE),
                potionAmplifier(player, PotionEffectType.MINING_FATIGUE),
                player.isInWater() && !hasAquaAffinity(player),
                !player.isOnGround()
        );
    }

    private int efficiencyLevel(ItemStack tool) {
        return tool == null ? 0 : tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
    }

    private int potionAmplifier(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        return effect == null ? -1 : effect.getAmplifier();
    }

    private boolean hasAquaAffinity(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return helmet != null && helmet.containsEnchantment(Enchantment.AQUA_AFFINITY);
    }

    private void cancelSession(UUID playerId) {
        MiningSession removed = sessions.remove(playerId);
        if (removed != null) {
            removed.clearBlockDamage();
        }
    }

    static MiningMode miningMode(GameMode gameMode) {
        if (gameMode == GameMode.CREATIVE) {
            return MiningMode.INSTANT;
        }
        if (gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE) {
            return MiningMode.SESSION;
        }
        return MiningMode.IGNORED;
    }

    enum MiningMode {
        INSTANT,
        SESSION,
        IGNORED
    }

    private static final class MiningSession {

        private final Player player;
        private final BlockKey blockKey;
        private final Location blockLocation;
        private final ToolSignature toolSignature;
        private double progress;

        private MiningSession(Player player, BlockKey blockKey, Location blockLocation, ToolSignature toolSignature) {
            this.player = player;
            this.blockKey = blockKey;
            this.blockLocation = blockLocation;
            this.toolSignature = toolSignature;
        }

        UUID playerId() {
            return player.getUniqueId();
        }

        Player player() {
            return player;
        }

        boolean sameTarget(BlockKey other) {
            return blockKey.equals(other);
        }

        boolean sameTool(ToolSignature other) {
            return toolSignature.equals(other);
        }

        boolean valid() {
            return player.isOnline()
                    && player.isValid()
                    && miningMode(player.getGameMode()) == MiningMode.SESSION
                    && player.getWorld().getUID().equals(blockKey.worldId())
                    && player.getLocation().distanceSquared(blockLocation) <= MAX_MINING_DISTANCE_SQUARED;
        }

        Optional<Block> block() {
            World world = player.getServer().getWorld(blockKey.worldId());
            if (world == null) {
                return Optional.empty();
            }
            Block block = world.getBlockAt(blockKey.x(), blockKey.y(), blockKey.z());
            return DragonEggMiningRules.isDragonEgg(block.getType()) ? Optional.of(block) : Optional.empty();
        }

        void addProgress(double amount) {
            progress += amount;
        }

        boolean complete() {
            return progress >= 1.0D;
        }

        void sendProgress() {
            player.sendBlockDamage(blockLocation, (float) Math.min(0.99D, Math.max(0.0D, progress)));
        }

        void clearBlockDamage() {
            player.sendBlockDamage(blockLocation, 0.0F);
        }
    }

    private record ToolSignature(Material type, int efficiencyLevel) {

        static ToolSignature of(ItemStack stack) {
            if (stack == null) {
                return new ToolSignature(Material.AIR, 0);
            }
            return new ToolSignature(stack.getType(), stack.getEnchantmentLevel(Enchantment.EFFICIENCY));
        }
    }

    private record BreakAllowance(UUID playerId, BlockKey blockKey) {

        static BreakAllowance of(Player player, Block block) {
            return new BreakAllowance(player.getUniqueId(), BlockKey.of(block));
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {

        static BlockKey of(Block block) {
            Location location = block.getLocation();
            return new BlockKey(
                    location.getWorld().getUID(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ()
            );
        }
    }
}
