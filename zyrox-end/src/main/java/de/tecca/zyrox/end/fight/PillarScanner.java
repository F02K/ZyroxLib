package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PillarScanner {

    public List<PillarRitualNode> scan(World world, EndFightConfig config) {
        List<PillarRitualNode> nodes = new ArrayList<>();
        for (EnderCrystal crystal : world.getEntitiesByClass(EnderCrystal.class)) {
            if (!nearArena(crystal.getLocation(), config)) {
                continue;
            }
            Location top = detectPillarTop(world, crystal.getLocation());
            if (top != null) {
                nodes.add(new PillarRitualNode(UUID.randomUUID(), top, crystal.getUniqueId()));
            }
        }

        if (nodes.isEmpty()) {
            nodes.addAll(scanObsidianPillars(world, config));
        }

        nodes.sort(Comparator.comparingDouble(node -> node.pillarTop().distanceSquared(center(world, config))));
        return nodes;
    }

    private List<PillarRitualNode> scanObsidianPillars(World world, EndFightConfig config) {
        List<PillarRitualNode> nodes = new ArrayList<>();
        int radius = (int) Math.ceil(config.arenaRadius());
        int step = 4;
        for (int x = -radius; x <= radius; x += step) {
            for (int z = -radius; z <= radius; z += step) {
                Location candidate = new Location(world, config.arenaCenterX() + x, 100.0D, config.arenaCenterZ() + z);
                if (!nearArena(candidate, config)) {
                    continue;
                }
                Location top = detectPillarTop(world, candidate);
                if (top == null || tooClose(nodes, top)) {
                    continue;
                }
                nodes.add(new PillarRitualNode(UUID.randomUUID(), top, null));
            }
        }
        return nodes;
    }

    private Location detectPillarTop(World world, Location near) {
        int baseX = near.getBlockX();
        int baseZ = near.getBlockZ();
        for (int y = Math.min(world.getMaxHeight() - 2, Math.max(near.getBlockY() + 4, 90)); y >= world.getMinHeight(); y--) {
            Material material = world.getBlockAt(baseX, y, baseZ).getType();
            if (material != Material.OBSIDIAN && material != Material.BEDROCK) {
                continue;
            }
            int support = 0;
            for (int dy = 0; dy < 8; dy++) {
                Material below = world.getBlockAt(baseX, y - dy, baseZ).getType();
                if (below == Material.OBSIDIAN || below == Material.BEDROCK) {
                    support++;
                }
            }
            if (support >= 5) {
                return new Location(world, baseX + 0.5D, y + 1.0D, baseZ + 0.5D);
            }
        }
        return null;
    }

    private boolean tooClose(List<PillarRitualNode> nodes, Location location) {
        for (PillarRitualNode node : nodes) {
            if (node.pillarTop().distanceSquared(location) < 12.0D * 12.0D) {
                return true;
            }
        }
        return false;
    }

    private boolean nearArena(Location location, EndFightConfig config) {
        double dx = location.getX() - config.arenaCenterX();
        double dz = location.getZ() - config.arenaCenterZ();
        return dx * dx + dz * dz <= config.arenaRadius() * config.arenaRadius();
    }

    private Location center(World world, EndFightConfig config) {
        return new Location(world, config.arenaCenterX(), 65.0D, config.arenaCenterZ());
    }
}
