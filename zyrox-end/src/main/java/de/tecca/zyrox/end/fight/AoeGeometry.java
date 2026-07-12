package de.tecca.zyrox.end.fight;

import org.bukkit.Location;

import java.util.List;

final class AoeGeometry {

    private AoeGeometry() {
    }

    static boolean hitsPoint(Location playerLocation, Location origin, double radius, double verticalRange) {
        if (!sameWorld(playerLocation, origin)) {
            return false;
        }
        double dx = playerLocation.getX() - origin.getX();
        double dz = playerLocation.getZ() - origin.getZ();
        return dx * dx + dz * dz <= radius * radius
                && Math.abs(playerLocation.getY() - origin.getY()) <= verticalRange;
    }

    static boolean hitsLane(Location playerLocation, List<Location> lane, double radius, double verticalRange) {
        for (Location point : lane) {
            if (hitsPoint(playerLocation, point, radius, verticalRange)) {
                return true;
            }
        }
        return false;
    }

    static boolean hitsRing(Location playerLocation, Location origin, double ringRadius, double ringWidth) {
        if (!sameWorld(playerLocation, origin)) {
            return false;
        }
        double dx = playerLocation.getX() - origin.getX();
        double dz = playerLocation.getZ() - origin.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        return Math.abs(distance - Math.max(0.0D, ringRadius)) <= Math.max(0.0D, ringWidth) * 0.5D;
    }

    static boolean hitsRay(
            Location playerLocation,
            Location origin,
            double angle,
            double length,
            double width,
            double verticalRange
    ) {
        if (!sameWorld(playerLocation, origin)
                || Math.abs(playerLocation.getY() - origin.getY()) > Math.max(0.0D, verticalRange)) {
            return false;
        }
        double dx = playerLocation.getX() - origin.getX();
        double dz = playerLocation.getZ() - origin.getZ();
        double directionX = Math.cos(angle);
        double directionZ = Math.sin(angle);
        double projection = dx * directionX + dz * directionZ;
        if (projection < 0.0D || projection > Math.max(0.0D, length)) {
            return false;
        }
        double perpendicular = Math.abs(dx * directionZ - dz * directionX);
        return perpendicular <= Math.max(0.0D, width) * 0.5D;
    }

    private static boolean sameWorld(Location first, Location second) {
        return first != null && second != null && first.getWorld() == second.getWorld();
    }
}
