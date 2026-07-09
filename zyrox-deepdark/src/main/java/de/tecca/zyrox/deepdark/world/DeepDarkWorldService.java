package de.tecca.zyrox.deepdark.world;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DeepDarkWorldService {

    private final JavaPlugin plugin;
    private World world;

    public DeepDarkWorldService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public World ensureWorld() {
        String worldName = worldName();
        World loadedWorld = Bukkit.getWorld(worldName);
        if (loadedWorld == null) {
            WorldCreator creator = new WorldCreator(worldName)
                    .environment(World.Environment.NORMAL)
                    .generateStructures(false)
                    .seed(plugin.getConfig().getLong("world.seed", 918273645L))
                    .generator(new DeepDarkChunkGenerator(
                            plugin.getConfig().getInt("world.cavern-height", 72),
                            plugin.getConfig().getInt("world.hall-density", 44),
                            plugin.getConfig().getInt("world.tunnel-density", 38),
                            plugin.getConfig().getInt("world.floor-roughness", plugin.getConfig().getInt("world.cavern-roughness", 8)),
                            plugin.getConfig().getInt("world.ceiling-roughness", plugin.getConfig().getInt("world.cavern-roughness", 8)),
                            plugin.getConfig().getInt("world.sculk-density", 55)
                    ));
            loadedWorld = creator.createWorld();
        }

        if (loadedWorld == null) {
            throw new IllegalStateException("Could not load DeepDark world: " + worldName);
        }

        loadedWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        loadedWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        loadedWorld.setTime(18000L);
        loadedWorld.setSpawnLocation(safeSpawnLocation(loadedWorld));
        world = loadedWorld;
        return loadedWorld;
    }

    public World world() {
        return world == null ? ensureWorld() : world;
    }

    public boolean isDeepDarkWorld(World candidate) {
        return candidate != null && worldName().equals(candidate.getName());
    }

    public String worldName() {
        return plugin.getConfig().getString("world.name", "zyrox_deepdark");
    }

    public Location configuredSpawn(World targetWorld) {
        return new Location(
                targetWorld,
                plugin.getConfig().getDouble("world.spawn.x", 0.5),
                plugin.getConfig().getDouble("world.spawn.y", 70.0),
                plugin.getConfig().getDouble("world.spawn.z", 0.5),
                (float) plugin.getConfig().getDouble("world.spawn.yaw", 0.0),
                (float) plugin.getConfig().getDouble("world.spawn.pitch", 0.0)
        );
    }

    public Location safeSpawnLocation(World targetWorld) {
        return ensureSafeTeleportLocation(configuredSpawn(targetWorld));
    }

    public int portalPlatformY(World targetWorld, int x, int z) {
        targetWorld.getChunkAt(x >> 4, z >> 4).load(true);
        int preferredY = Math.max(targetWorld.getMinHeight() + 8, Math.min(70, targetWorld.getMaxHeight() - 16));
        for (int y = preferredY; y >= targetWorld.getMinHeight() + 6; y--) {
            if (isPortalPlatformCandidate(targetWorld, x, y, z)) {
                return y;
            }
        }
        for (int y = preferredY + 1; y <= Math.min(targetWorld.getMaxHeight() - 16, preferredY + 24); y++) {
            if (isPortalPlatformCandidate(targetWorld, x, y, z)) {
                return y;
            }
        }
        return Math.max(targetWorld.getMinHeight() + 8, Math.min(preferredY, targetWorld.getMaxHeight() - 14));
    }

    public Location ensureSafeTeleportLocation(Location preferred) {
        if (preferred == null || preferred.getWorld() == null) {
            return null;
        }

        World targetWorld = preferred.getWorld();
        int startX = preferred.getBlockX();
        int startZ = preferred.getBlockZ();
        int startY = Math.max(targetWorld.getMinHeight() + 4, Math.min(preferred.getBlockY(), targetWorld.getMaxHeight() - 4));

        for (int radius = 0; radius <= 6; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -8; dy <= 8; dy++) {
                        int x = startX + dx;
                        int y = startY + dy;
                        int z = startZ + dz;
                        if (isSafe(targetWorld, x, y, z)) {
                            return new Location(targetWorld, x + 0.5, y, z + 0.5, preferred.getYaw(), preferred.getPitch());
                        }
                    }
                }
            }
        }

        int y = startY;
        targetWorld.getBlockAt(startX, y - 1, startZ).setType(Material.SCULK, false);
        targetWorld.getBlockAt(startX, y, startZ).setType(Material.AIR, false);
        targetWorld.getBlockAt(startX, y + 1, startZ).setType(Material.AIR, false);
        targetWorld.getBlockAt(startX, y + 2, startZ).setType(Material.AIR, false);
        return new Location(targetWorld, startX + 0.5, y, startZ + 0.5, preferred.getYaw(), preferred.getPitch());
    }

    private boolean isSafe(World targetWorld, int x, int y, int z) {
        if (y <= targetWorld.getMinHeight() + 1 || y >= targetWorld.getMaxHeight() - 2) {
            return false;
        }
        Material floor = targetWorld.getBlockAt(x, y - 1, z).getType();
        Material feet = targetWorld.getBlockAt(x, y, z).getType();
        Material head = targetWorld.getBlockAt(x, y + 1, z).getType();
        return floor.isSolid() && floor != Material.LAVA && feet.isAir() && head.isAir();
    }

    private boolean isPortalPlatformCandidate(World targetWorld, int x, int y, int z) {
        if (y <= targetWorld.getMinHeight() + 3 || y >= targetWorld.getMaxHeight() - 12) {
            return false;
        }
        Material floor = targetWorld.getBlockAt(x, y - 1, z).getType();
        if (!floor.isSolid() || floor == Material.LAVA) {
            return false;
        }
        for (int dy = 0; dy <= 10; dy++) {
            if (!targetWorld.getBlockAt(x, y + dy, z).getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
