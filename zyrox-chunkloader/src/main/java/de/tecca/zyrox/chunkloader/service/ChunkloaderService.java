package de.tecca.zyrox.chunkloader.service;

import de.tecca.zyrox.chunkloader.billing.ChunkloaderBilling;
import de.tecca.zyrox.chunkloader.item.ChunkloaderItems;
import de.tecca.zyrox.chunkloader.storage.ChunkloaderRecord;
import de.tecca.zyrox.chunkloader.storage.ChunkloaderStorage;
import de.tecca.zyrox.chunkloader.text.ChunkloaderText;
import de.tecca.zyrox.chunkloader.util.DiamondBlocks;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkloaderService {

    private final JavaPlugin plugin;
    private final ChunkloaderStorage storage;
    private final ChunkloaderItems items;
    private final ChunkloaderText text;
    private final Map<Long, ChunkloaderStatus> statuses = new ConcurrentHashMap<>();
    private final Set<ChunkKey> activeTickets = new HashSet<>();

    public ChunkloaderService(JavaPlugin plugin, ChunkloaderStorage storage, ChunkloaderItems items, ChunkloaderText text) {
        this.plugin = plugin;
        this.storage = storage;
        this.items = items;
        this.text = text;
    }

    public Optional<ChunkloaderRecord> findLoader(Location location) {
        try {
            return storage.findByLocation(location);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not find chunkloader: " + exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ChunkloaderRecord> findLoader(long id) {
        try {
            return storage.findById(id);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not find chunkloader: " + exception.getMessage());
            return Optional.empty();
        }
    }

    public List<ChunkloaderRecord> loadersByOwner(UUID ownerId) {
        try {
            return storage.byOwner(ownerId);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not load owner chunkloaders: " + exception.getMessage());
            return List.of();
        }
    }

    public ChunkloaderActionResult create(Block block, Player owner) {
        if (disabledWorld(block.getWorld())) {
            return ChunkloaderActionResult.failure(text.message("place-disabled-world", Map.of()), null);
        }
        try {
            ChunkloaderRecord loader = storage.createLoader(block.getLocation(), owner);
            refreshAll();
            return ChunkloaderActionResult.success(text.message("place-success", Map.of()), loader);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not create chunkloader: " + exception.getMessage());
            return ChunkloaderActionResult.failure(text.message("place-failed", Map.of()), null);
        }
    }

    public ChunkloaderActionResult toggle(ChunkloaderRecord loader) {
        try {
            ChunkloaderRecord updated = storage.setEnabled(loader.id(), !loader.enabled());
            refreshAll();
            return ChunkloaderActionResult.success(text.message(updated.enabled() ? "toggle-enabled" : "toggle-disabled", Map.of()), updated);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not toggle chunkloader: " + exception.getMessage());
            return ChunkloaderActionResult.failure(text.message("storage-failed", Map.of()), loader);
        }
    }

    public ChunkloaderActionResult deposit(ChunkloaderRecord loader, Player player, long amount) {
        long target = amount < 0 ? DiamondBlocks.count(player.getInventory()) : amount;
        if (target <= 0) {
            return ChunkloaderActionResult.failure(text.message("deposit-empty", Map.of()), loader);
        }
        if (DiamondBlocks.count(player.getInventory()) < target) {
            return ChunkloaderActionResult.failure(text.message("deposit-not-enough", Map.of("amount", Long.toString(target))), loader);
        }
        if (!DiamondBlocks.withdraw(player.getInventory(), target)) {
            return ChunkloaderActionResult.failure(text.message("deposit-not-enough", Map.of("amount", Long.toString(target))), loader);
        }
        try {
            ChunkloaderRecord updated = storage.addReserve(loader.id(), target);
            refreshAll();
            return ChunkloaderActionResult.success(text.message("deposit-success", Map.of("amount", Long.toString(target))), updated);
        } catch (SQLException exception) {
            DiamondBlocks.give(player.getInventory(), target);
            plugin.getLogger().warning("Could not deposit chunkloader reserve: " + exception.getMessage());
            return ChunkloaderActionResult.failure(text.message("storage-failed", Map.of()), loader);
        }
    }

    public ChunkloaderActionResult movePriority(ChunkloaderRecord loader, int direction) {
        try {
            ChunkloaderRecord updated = storage.movePriority(loader.id(), direction).orElse(loader);
            refreshAll();
            return ChunkloaderActionResult.success(text.message("priority-updated", Map.of()), updated);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not move chunkloader priority: " + exception.getMessage());
            return ChunkloaderActionResult.failure(text.message("storage-failed", Map.of()), loader);
        }
    }

    public ChunkloaderActionResult delete(ChunkloaderRecord loader) {
        try {
            boolean deleted = storage.deleteLoader(loader.id());
            refreshAll();
            return deleted
                    ? ChunkloaderActionResult.success(text.message("remove-success", placeholders(loader)), null)
                    : ChunkloaderActionResult.failure(text.message("storage-failed", placeholders(loader)), loader);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not delete chunkloader: " + exception.getMessage());
            return ChunkloaderActionResult.failure(text.message("storage-failed", placeholders(loader)), loader);
        }
    }

    public ChunkloaderStatus status(ChunkloaderRecord loader) {
        return statuses.getOrDefault(loader.id(), ChunkloaderStatus.inactive(loader.reserveBlocks()));
    }

    public Map<String, String> placeholders(ChunkloaderRecord loader) {
        return text.placeholders(loader, status(loader));
    }

    public boolean isChunkloaderBlock(Block block) {
        return block != null && block.getType() == items.blockMaterial() && findLoader(block.getLocation()).isPresent();
    }

    public boolean disabledWorld(World world) {
        if (world == null) {
            return true;
        }
        List<String> disabled = plugin.getConfig().getStringList("chunkloader.disabled-worlds");
        return disabled.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(world.getName().toLowerCase(Locale.ROOT)::equals);
    }

    public void refreshAll() {
        List<ChunkloaderRecord> loaders;
        try {
            loaders = storage.allLoaders();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not refresh chunkloaders: " + exception.getMessage());
            return;
        }

        Instant now = Instant.now();
        Duration cycle = Duration.ofMinutes(Math.max(1L, plugin.getConfig().getLong("chunkloader.billing-cycle-minutes", 60L)));
        long baseCost = Math.max(1L, plugin.getConfig().getLong("chunkloader.base-cost-diamond-blocks-per-hour", 1L));
        Map<Long, ChunkloaderRecord> byId = new HashMap<>();
        List<ChunkloaderBilling.Input> inputs = loaders.stream().map(loader -> {
            byId.put(loader.id(), loader);
            Instant paidUntil = parseInstant(loader.paidUntil());
            boolean disabledWorld = disabledWorld(plugin.getServer().getWorld(loader.worldName()));
            return new ChunkloaderBilling.Input(
                    loader.id(),
                    loader.ownerUuid(),
                    loader.priority(),
                    loader.enabled(),
                    disabledWorld,
                    loader.reserveBlocks(),
                    paidUntil
            );
        }).toList();

        Map<Long, ChunkloaderStatus> nextStatuses = new HashMap<>();
        Set<ChunkKey> desiredTickets = new HashSet<>();
        for (ChunkloaderBilling.Decision decision : ChunkloaderBilling.evaluate(inputs, now, baseCost, cycle)) {
            if (decision.chargedBlocks() > 0) {
                try {
                    storage.payCycle(decision.id(), decision.chargedBlocks(), decision.paidUntilAfter());
                } catch (SQLException exception) {
                    plugin.getLogger().warning("Could not charge chunkloader #" + decision.id() + ": " + exception.getMessage());
                    continue;
                }
            }
            ChunkloaderRecord loader = byId.get(decision.id());
            if (loader == null) {
                continue;
            }
            nextStatuses.put(loader.id(), new ChunkloaderStatus(
                    decision.active(),
                    decision.rank(),
                    decision.nextCost(),
                    decision.reserveAfter(),
                    decision.paidUntilAfter()
            ));
            if (decision.active()) {
                desiredTickets.add(new ChunkKey(loader.worldName(), loader.chunkX(), loader.chunkZ()));
            }
        }
        statuses.clear();
        statuses.putAll(nextStatuses);
        reconcileTickets(desiredTickets);
    }

    public void clearTickets() {
        reconcileTickets(Set.of());
    }

    public void spawnVisuals() {
        List<ChunkloaderRecord> loaders;
        try {
            loaders = storage.allLoaders();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not render chunkloader visuals: " + exception.getMessage());
            return;
        }
        for (ChunkloaderRecord loader : loaders) {
            World world = plugin.getServer().getWorld(loader.worldName());
            if (world == null) {
                continue;
            }
            Location center = new Location(world, loader.x() + 0.5D, loader.y() + 1.15D, loader.z() + 0.5D);
            ChunkloaderStatus status = status(loader);
            if (status.active()) {
                spawnRing(world, center, Color.AQUA, 1.0F, 18);
                world.spawnParticle(Particle.END_ROD, center.clone().add(0.0D, 0.5D, 0.0D), 4, 0.12D, 0.45D, 0.12D, 0.01D);
            } else {
                spawnRing(world, center, Color.GRAY, 0.7F, 8);
            }
        }
    }

    public void sendActionBars() {
        int distance = Math.max(1, plugin.getConfig().getInt("chunkloader.actionbar-distance", 6));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Block block = player.getTargetBlockExact(distance);
            if (block == null || block.getType() != items.blockMaterial()) {
                continue;
            }
            Optional<ChunkloaderRecord> loader = findLoader(block.getLocation());
            if (loader.isEmpty()) {
                continue;
            }
            Map<String, String> placeholders = placeholders(loader.get());
            player.sendActionBar(Component.text("Chunkloader " + placeholders.get("status")
                    + " | Chunk " + placeholders.get("chunk_x") + "/" + placeholders.get("chunk_z")
                    + " | Rang " + placeholders.get("rank")
                    + " | Reserve " + placeholders.get("reserve")
                    + " | Bezahlt " + placeholders.get("paid_time_left")));
        }
    }

    private void reconcileTickets(Set<ChunkKey> desiredTickets) {
        for (ChunkKey current : new HashSet<>(activeTickets)) {
            if (!desiredTickets.contains(current)) {
                World world = plugin.getServer().getWorld(current.worldName());
                if (world != null) {
                    world.removePluginChunkTicket(current.chunkX(), current.chunkZ(), plugin);
                }
                activeTickets.remove(current);
            }
        }
        for (ChunkKey desired : desiredTickets) {
            if (activeTickets.contains(desired)) {
                continue;
            }
            World world = plugin.getServer().getWorld(desired.worldName());
            if (world != null) {
                world.addPluginChunkTicket(desired.chunkX(), desired.chunkZ(), plugin);
                activeTickets.add(desired);
            }
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void spawnRing(World world, Location center, Color color, float size, int points) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            Location point = center.clone().add(Math.cos(angle) * 0.75D, 0.0D, Math.sin(angle) * 0.75D);
            world.spawnParticle(Particle.DUST, point, 1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
        }
    }

    public Material blockMaterial() {
        return items.blockMaterial();
    }

    public ItemStack createItem(int amount) {
        return items.createItem(amount);
    }

    private record ChunkKey(String worldName, int chunkX, int chunkZ) {
    }
}
