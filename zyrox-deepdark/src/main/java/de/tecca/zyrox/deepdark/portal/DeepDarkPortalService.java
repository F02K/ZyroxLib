package de.tecca.zyrox.deepdark.portal;

import de.tecca.zyrox.deepdark.world.DeepDarkWorldService;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeepDarkPortalService {

    private final JavaPlugin plugin;
    private final DeepDarkWorldService worldService;
    private final File portalFile;
    private final Map<String, DeepDarkPortal> portals = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private int effectTaskId = -1;

    public DeepDarkPortalService(JavaPlugin plugin, DeepDarkWorldService worldService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.worldService = Objects.requireNonNull(worldService, "worldService");
        this.portalFile = new File(plugin.getDataFolder(), "portals.yml");
    }

    public void load() {
        portals.clear();
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(portalFile);
        ConfigurationSection portalSection = configuration.getConfigurationSection("portals");
        if (portalSection == null) {
            return;
        }

        for (String id : portalSection.getKeys(false)) {
            DeepDarkPortal.read(id, portalSection.getConfigurationSection(id)).ifPresent(portal -> portals.put(portal.id(), portal));
        }
        portals.values().forEach(this::restorePortalInterior);
    }

    public void save() {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection portalSection = configuration.createSection("portals");
        for (DeepDarkPortal portal : portals.values()) {
            portal.write(portalSection.createSection(portal.id()));
        }

        try {
            configuration.save(portalFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save DeepDark portals: " + exception.getMessage());
        }
    }

    public void startEffects() {
        if (effectTaskId != -1) {
            Bukkit.getScheduler().cancelTask(effectTaskId);
        }
        effectTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::spawnPortalParticles, 20L, 20L);
    }

    public void stopEffects() {
        if (effectTaskId != -1) {
            Bukkit.getScheduler().cancelTask(effectTaskId);
            effectTaskId = -1;
        }
    }

    public Collection<DeepDarkPortal> allPortals() {
        return portals.values().stream()
                .sorted(Comparator.comparing(DeepDarkPortal::id))
                .toList();
    }

    public Optional<DeepDarkPortal> get(String id) {
        return Optional.ofNullable(portals.get(id));
    }

    public DeepDarkPortal createPortalAt(Location center, PortalAxis axis, String requestedId, boolean createReturnPortal) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(axis, "axis");
        World world = Objects.requireNonNull(center.getWorld(), "center world");

        int originX = axis == PortalAxis.X ? center.getBlockX() - (DeepDarkPortal.FRAME_WIDTH / 2) : center.getBlockX();
        int originY = center.getBlockY();
        int originZ = axis == PortalAxis.X ? center.getBlockZ() : center.getBlockZ() - (DeepDarkPortal.FRAME_WIDTH / 2);
        if (worldService.isDeepDarkWorld(world)) {
            originY = worldService.portalPlatformY(world, center.getBlockX(), center.getBlockZ());
            preparePortalPlatform(world, originX, originY, originZ, axis);
        }
        buildFrame(world, originX, originY, originZ, axis);
        fillPortalInterior(world, originX, originY, originZ, axis);

        String id = requestedId == null || requestedId.isBlank() ? portalId(world.getName(), originX, originY, originZ, axis) : sanitizeId(requestedId);
        DeepDarkPortal portal = new DeepDarkPortal(id, world.getName(), originX, originY, originZ, axis, "", 0L);
        portals.put(portal.id(), portal);

        if (createReturnPortal && plugin.getConfig().getBoolean("portal.auto-create-return-portal", true) && !worldService.isDeepDarkWorld(world)) {
            DeepDarkPortal returnPortal = ensureReturnPortal(portal);
            portal = portal.withLinkedPortalId(returnPortal.id());
            returnPortal = returnPortal.withLinkedPortalId(portal.id());
            portals.put(portal.id(), portal);
            portals.put(returnPortal.id(), returnPortal);
        }

        save();
        return portal;
    }

    public Optional<DeepDarkPortal> activateFrame(Block clickedBlock) {
        Optional<FrameMatch> frame = findFrame(clickedBlock);
        if (frame.isEmpty()) {
            return Optional.empty();
        }

        FrameMatch match = frame.get();
        Optional<DeepDarkPortal> existing = portals.values().stream()
                .filter(portal -> portal.worldName().equals(match.world().getName()))
                .filter(portal -> portal.originX() == match.originX() && portal.originY() == match.originY() && portal.originZ() == match.originZ())
                .filter(portal -> portal.axis() == match.axis())
                .findFirst();
        if (existing.isPresent()) {
            fillPortalInterior(match.world(), match.originX(), match.originY(), match.originZ(), match.axis());
            return existing;
        }

        String id = portalId(match.world().getName(), match.originX(), match.originY(), match.originZ(), match.axis());
        DeepDarkPortal portal = new DeepDarkPortal(id, match.world().getName(), match.originX(), match.originY(), match.originZ(), match.axis(), "", 0L);
        fillPortalInterior(match.world(), match.originX(), match.originY(), match.originZ(), match.axis());
        portals.put(portal.id(), portal);
        if (plugin.getConfig().getBoolean("portal.auto-create-return-portal", true) && !worldService.isDeepDarkWorld(match.world())) {
            DeepDarkPortal returnPortal = ensureReturnPortal(portal);
            portal = portal.withLinkedPortalId(returnPortal.id());
            returnPortal = returnPortal.withLinkedPortalId(portal.id());
            portals.put(portal.id(), portal);
            portals.put(returnPortal.id(), returnPortal);
        }
        save();
        return Optional.of(portal);
    }

    public boolean deletePortal(String id) {
        DeepDarkPortal removed = portals.remove(id);
        if (removed == null) {
            return false;
        }
        clearPortalInterior(removed);
        if (!removed.linkedPortalId().isBlank()) {
            DeepDarkPortal linked = portals.get(removed.linkedPortalId());
            if (linked != null) {
                portals.put(linked.id(), linked.withLinkedPortalId(""));
            }
        }
        save();
        return true;
    }

    public Optional<DeepDarkPortal> nearestPortal(Location location, double maxDistance) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }

        return portals.values().stream()
                .filter(portal -> portal.worldName().equals(location.getWorld().getName()))
                .filter(portal -> portal.centerLocation() != null)
                .filter(portal -> portal.centerLocation().distanceSquared(location) <= maxDistance * maxDistance)
                .min(Comparator.comparingDouble(portal -> portal.centerLocation().distanceSquared(location)));
    }

    public void handlePortalMove(Player player) {
        findPortalAt(player.getLocation()).ifPresent(portal -> teleportPlayer(player, portal));
    }

    public boolean handleVanillaNetherPortal(Player player) {
        Optional<DeepDarkPortal> portal = findPortalAt(player.getLocation());
        if (portal.isEmpty()) {
            return false;
        }
        teleportPlayer(player, portal.get());
        return true;
    }

    public boolean shouldBlockVanillaNetherPortal(Location location) {
        if (!plugin.getConfig().getBoolean("portal.prevent-vanilla-nether-travel", true)) {
            return false;
        }
        if (findPortalAt(location).isPresent()) {
            return true;
        }
        return plugin.getConfig().getBoolean("portal.deepdark-block-all-nether-portals", true)
                && location != null
                && worldService.isDeepDarkWorld(location.getWorld());
    }

    public Material activationItem() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("portal.activation-item", "ECHO_SHARD"));
        return material == null ? Material.ECHO_SHARD : material;
    }

    public Material frameMaterial() {
        return Material.REINFORCED_DEEPSLATE;
    }

    public Material fillMaterial() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("portal.fill-material", "NETHER_PORTAL"));
        return material == null || !material.isBlock() ? Material.NETHER_PORTAL : material;
    }

    private Optional<FrameMatch> findFrame(Block clickedBlock) {
        if (clickedBlock == null || clickedBlock.getType() != frameMaterial()) {
            return Optional.empty();
        }

        World world = clickedBlock.getWorld();
        for (PortalAxis axis : PortalAxis.values()) {
            for (int yOffset = 0; yOffset < DeepDarkPortal.FRAME_HEIGHT; yOffset++) {
                int originY = clickedBlock.getY() - yOffset;
                for (int widthOffset = 0; widthOffset < DeepDarkPortal.FRAME_WIDTH; widthOffset++) {
                    int originX = axis == PortalAxis.X ? clickedBlock.getX() - widthOffset : clickedBlock.getX();
                    int originZ = axis == PortalAxis.X ? clickedBlock.getZ() : clickedBlock.getZ() - widthOffset;
                    if (isValidFrame(world, originX, originY, originZ, axis)) {
                        return Optional.of(new FrameMatch(world, originX, originY, originZ, axis));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean isValidFrame(World world, int originX, int originY, int originZ, PortalAxis axis) {
        for (int width = 0; width < DeepDarkPortal.FRAME_WIDTH; width++) {
            for (int height = 0; height < DeepDarkPortal.FRAME_HEIGHT; height++) {
                int x = axis == PortalAxis.X ? originX + width : originX;
                int z = axis == PortalAxis.X ? originZ : originZ + width;
                Material type = world.getBlockAt(x, originY + height, z).getType();
                boolean boundary = isFrameBoundary(width, height);
                if (boundary && type != frameMaterial()) {
                    return false;
                }
                if (!boundary && !isValidPortalInterior(type)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void buildFrame(World world, int originX, int originY, int originZ, PortalAxis axis) {
        for (int width = 0; width < DeepDarkPortal.FRAME_WIDTH; width++) {
            for (int height = 0; height < DeepDarkPortal.FRAME_HEIGHT; height++) {
                int x = axis == PortalAxis.X ? originX + width : originX;
                int z = axis == PortalAxis.X ? originZ : originZ + width;
                if (isFrameBoundary(width, height)) {
                    world.getBlockAt(x, originY + height, z).setType(frameMaterial(), false);
                } else {
                    world.getBlockAt(x, originY + height, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private void fillPortalInterior(World world, int originX, int originY, int originZ, PortalAxis axis) {
        BlockData portalData = portalBlockData(axis);
        for (int width = DeepDarkPortal.INTERIOR_MIN_WIDTH; width <= DeepDarkPortal.INTERIOR_MAX_WIDTH; width++) {
            for (int height = DeepDarkPortal.INTERIOR_MIN_HEIGHT; height <= DeepDarkPortal.INTERIOR_MAX_HEIGHT; height++) {
                int x = axis == PortalAxis.X ? originX + width : originX;
                int z = axis == PortalAxis.X ? originZ : originZ + width;
                world.getBlockAt(x, originY + height, z).setBlockData(portalData, false);
            }
        }
    }

    private void restorePortalInterior(DeepDarkPortal portal) {
        World world = Bukkit.getWorld(portal.worldName());
        if (world == null || !isValidFrame(world, portal.originX(), portal.originY(), portal.originZ(), portal.axis())) {
            return;
        }
        fillPortalInterior(world, portal.originX(), portal.originY(), portal.originZ(), portal.axis());
    }

    private void clearPortalInterior(DeepDarkPortal portal) {
        World world = Bukkit.getWorld(portal.worldName());
        if (world == null) {
            return;
        }

        for (int width = DeepDarkPortal.INTERIOR_MIN_WIDTH; width <= DeepDarkPortal.INTERIOR_MAX_WIDTH; width++) {
            for (int height = DeepDarkPortal.INTERIOR_MIN_HEIGHT; height <= DeepDarkPortal.INTERIOR_MAX_HEIGHT; height++) {
                int x = portal.axis() == PortalAxis.X ? portal.originX() + width : portal.originX();
                int z = portal.axis() == PortalAxis.X ? portal.originZ() : portal.originZ() + width;
                Block block = world.getBlockAt(x, portal.originY() + height, z);
                if (block.getType() == fillMaterial()) {
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private BlockData portalBlockData(PortalAxis axis) {
        BlockData data = Bukkit.createBlockData(fillMaterial());
        if (data instanceof Orientable orientable) {
            orientable.setAxis(axis == PortalAxis.X ? Axis.X : Axis.Z);
            return orientable;
        }
        return data;
    }

    private boolean isValidPortalInterior(Material material) {
        return material.isAir() || material == fillMaterial();
    }

    private boolean isFrameBoundary(int width, int height) {
        return width == 0 || width == DeepDarkPortal.FRAME_WIDTH - 1 || height == 0 || height == DeepDarkPortal.FRAME_HEIGHT - 1;
    }

    private DeepDarkPortal ensureReturnPortal(DeepDarkPortal sourcePortal) {
        World deepDarkWorld = worldService.world();
        Location sourceCenter = sourcePortal.centerLocation();
        int centerX = sourceCenter == null ? deepDarkWorld.getSpawnLocation().getBlockX() : sourceCenter.getBlockX();
        int centerZ = sourceCenter == null ? deepDarkWorld.getSpawnLocation().getBlockZ() : sourceCenter.getBlockZ();
        int originY = worldService.portalPlatformY(deepDarkWorld, centerX, centerZ);
        PortalAxis axis = sourcePortal.axis();
        int originX = axis == PortalAxis.X ? centerX - (DeepDarkPortal.FRAME_WIDTH / 2) : centerX;
        int originZ = axis == PortalAxis.X ? centerZ : centerZ - (DeepDarkPortal.FRAME_WIDTH / 2);
        preparePortalPlatform(deepDarkWorld, originX, originY, originZ, axis);
        buildFrame(deepDarkWorld, originX, originY, originZ, axis);
        fillPortalInterior(deepDarkWorld, originX, originY, originZ, axis);
        String id = "return_" + sourcePortal.id();
        return new DeepDarkPortal(id, deepDarkWorld.getName(), originX, originY, originZ, axis, sourcePortal.id(), 0L);
    }

    private Location destinationFor(DeepDarkPortal portal) {
        if (!portal.linkedPortalId().isBlank()) {
            DeepDarkPortal linkedPortal = portals.get(portal.linkedPortalId());
            if (linkedPortal != null && linkedPortal.centerLocation() != null) {
                return worldService.ensureSafeTeleportLocation(linkedPortal.centerLocation());
            }
        }

        if (worldService.isDeepDarkWorld(Bukkit.getWorld(portal.worldName()))) {
            World overworld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
            return overworld == null ? null : worldService.ensureSafeTeleportLocation(overworld.getSpawnLocation());
        }
        return worldService.ensureSafeTeleportLocation(worldService.safeSpawnLocation(worldService.world()));
    }

    private Optional<DeepDarkPortal> findPortalAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return portals.values().stream()
                .filter(candidate -> candidate.contains(location))
                .findFirst();
    }

    private void teleportPlayer(Player player, DeepDarkPortal portal) {
        if (isOnCooldown(player)) {
            return;
        }

        if (plugin.getConfig().getBoolean("portal.require-permission", true) && !player.hasPermission(permission("enter", "zyrox.deepdark.enter"))) {
            player.sendMessage("You do not have permission to enter the DeepDark.");
            setCooldown(player);
            return;
        }

        Location destination = destinationFor(portal);
        if (destination == null) {
            player.sendMessage("This DeepDark portal has no valid destination.");
            setCooldown(player);
            return;
        }

        player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        portals.put(portal.id(), portal.withLastUsedAt(Instant.now().toEpochMilli()));
        save();
        setCooldown(player);
    }

    private void preparePortalPlatform(World world, int originX, int originY, int originZ, PortalAxis axis) {
        int margin = Math.max(4, plugin.getConfig().getInt("portal.platform-radius", 14));
        int minX = axis == PortalAxis.X ? originX - margin : originX - margin;
        int maxX = axis == PortalAxis.X ? originX + DeepDarkPortal.FRAME_WIDTH - 1 + margin : originX + margin;
        int minZ = axis == PortalAxis.X ? originZ - margin : originZ - margin;
        int maxZ = axis == PortalAxis.X ? originZ + margin : originZ + DeepDarkPortal.FRAME_WIDTH - 1 + margin;
        int clearTop = originY + DeepDarkPortal.FRAME_HEIGHT + 3;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                world.getBlockAt(x, originY - 3, z).setType(Material.DEEPSLATE, false);
                world.getBlockAt(x, originY - 2, z).setType(Material.COBBLED_DEEPSLATE, false);
                world.getBlockAt(x, originY - 1, z).setType(edge ? Material.DEEPSLATE_BRICKS : platformFloor(x, z), false);
                for (int y = originY; y <= clearTop; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private Material platformFloor(int x, int z) {
        int value = Math.floorMod(x * 31 + z * 57, 100);
        if (value > 84) {
            return Material.SCULK;
        }
        if (value > 70) {
            return Material.POLISHED_DEEPSLATE;
        }
        return Material.DEEPSLATE_TILES;
    }

    private void spawnPortalParticles() {
        for (DeepDarkPortal portal : portals.values()) {
            Location center = portal.centerLocation();
            if (center == null || center.getWorld() == null) {
                continue;
            }
            center.getWorld().spawnParticle(Particle.PORTAL, center.clone().add(0, 1.5, 0), 24, 1.2, 1.2, 0.15, 0.02);
        }
    }

    private boolean isOnCooldown(Player player) {
        long lastUse = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cooldownMillis = Math.max(1, plugin.getConfig().getInt("portal.cooldown-seconds", 5)) * 1000L;
        return System.currentTimeMillis() - lastUse < cooldownMillis;
    }

    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private String portalId(String worldName, int x, int y, int z, PortalAxis axis) {
        return sanitizeId(worldName + "_" + x + "_" + y + "_" + z + "_" + axis.name().toLowerCase());
    }

    private String sanitizeId(String value) {
        return value.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_");
    }

    private String permission(String key, String fallback) {
        return plugin.getConfig().getString("permissions." + key, fallback);
    }

    private record FrameMatch(World world, int originX, int originY, int originZ, PortalAxis axis) {
    }
}
