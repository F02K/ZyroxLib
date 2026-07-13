package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class PillarLayout {

    private static final int[][] VANILLA_PILLAR_POSITIONS = {
            {-42, -1},
            {-34, -25},
            {-34, 24},
            {-13, -40},
            {-13, 39},
            {12, -40},
            {12, 39},
            {33, -25},
            {33, 24},
            {42, 0}
    };
    private static final int CAP_SEARCH_ABOVE = 2;
    private static final int CAP_SEARCH_BELOW = 6;

    List<int[]> vanillaPillarPositions() {
        return Arrays.stream(VANILLA_PILLAR_POSITIONS)
                .map(position -> new int[]{position[0], position[1]})
                .toList();
    }

    Optional<Location> capSpawnAt(World world, int x, int z) {
        for (int y = world.getMaxHeight() - 2; y > world.getMinHeight(); y--) {
            if (bedrockAt(world, x, y, z)) {
                return Optional.of(capSpawn(world, x, y, z));
            }
        }
        return Optional.empty();
    }

    Optional<Location> capSpawnNearNode(PillarRitualNode node, World world) {
        if (node == null || world == null) {
            return Optional.empty();
        }
        Location top = node.pillarTop();
        int x = top.getBlockX();
        int z = top.getBlockZ();
        int expectedBedrockY = top.getBlockY() - 1;
        int maxY = Math.min(world.getMaxHeight() - 2, expectedBedrockY + CAP_SEARCH_ABOVE);
        int minY = Math.max(world.getMinHeight() + 1, expectedBedrockY - CAP_SEARCH_BELOW);
        for (int y = maxY; y >= minY; y--) {
            if (bedrockAt(world, x, y, z)) {
                return Optional.of(capSpawn(world, x, y, z));
            }
        }
        return Optional.empty();
    }

    private boolean bedrockAt(World world, int x, int bedrockY, int z) {
        if (bedrockY <= world.getMinHeight() || bedrockY >= world.getMaxHeight() - 1) {
            return false;
        }
        return world.getBlockAt(x, bedrockY, z).getType() == Material.BEDROCK;
    }

    private Location capSpawn(World world, int x, int bedrockY, int z) {
        return new Location(world, x + 0.5D, bedrockY + 1.0D, z + 0.5D);
    }
}
