package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.bukkit.World;

final class VisualCenter {

    private static final double WHOLE_NUMBER_EPSILON = 0.000001D;

    private VisualCenter() {
    }

    static double coordinate(double coordinate) {
        double rounded = Math.rint(coordinate);
        if (Math.abs(coordinate - rounded) <= WHOLE_NUMBER_EPSILON) {
            return coordinate + 0.5D;
        }
        return coordinate;
    }

    static Location location(World world, double x, double y, double z) {
        return new Location(world, coordinate(x), y, coordinate(z));
    }
}
