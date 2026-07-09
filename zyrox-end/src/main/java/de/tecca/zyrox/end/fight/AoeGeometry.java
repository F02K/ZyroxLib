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

    private static boolean sameWorld(Location first, Location second) {
        return first != null && second != null && first.getWorld() == second.getWorld();
    }
}
