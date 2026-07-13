package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collection;

record RitualGatheringCircle(double centerX, double centerZ, double radius) {

    private static final double MIN_RADIUS = 1.0D;
    private static final int MIN_DERIVED_RING_NODES = 10;

    static RitualGatheringCircle from(
            Collection<PillarRitualNode> nodes,
            double fallbackCenterX,
            double fallbackCenterZ,
            double fallbackRadius
    ) {
        if (nodes == null || nodes.size() < MIN_DERIVED_RING_NODES) {
            return new RitualGatheringCircle(
                    fallbackCenterX,
                    fallbackCenterZ,
                    Math.max(MIN_RADIUS, fallbackRadius)
            );
        }

        double centerX = nodes.stream().mapToDouble(node -> node.pillarTop().getX()).average().orElse(fallbackCenterX);
        double centerZ = nodes.stream().mapToDouble(node -> node.pillarTop().getZ()).average().orElse(fallbackCenterZ);
        double radius = nodes.stream()
                .mapToDouble(node -> horizontalDistance(centerX, centerZ, node.pillarTop()))
                .average()
                .orElse(fallbackRadius);
        return new RitualGatheringCircle(centerX, centerZ, Math.max(MIN_RADIUS, radius));
    }

    boolean contains(Location location) {
        if (location == null) {
            return false;
        }
        double dx = location.getX() - centerX;
        double dz = location.getZ() - centerZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    Location center(World world) {
        return new Location(world, centerX, world.getHighestBlockYAt((int) Math.round(centerX), (int) Math.round(centerZ)) + 0.2D, centerZ);
    }

    static int requiredPlayers(int eligiblePlayers, double requiredRatio) {
        if (eligiblePlayers <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(eligiblePlayers * Math.max(0.0D, Math.min(1.0D, requiredRatio))));
    }

    private static double horizontalDistance(double centerX, double centerZ, Location location) {
        double dx = location.getX() - centerX;
        double dz = location.getZ() - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
