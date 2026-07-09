package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AoeGeometryTest {

    @Test
    void pointAoeHitsWithinHorizontalRadiusAndVerticalRange() {
        Location origin = new Location(null, 0.0D, 64.0D, 0.0D);

        assertTrue(AoeGeometry.hitsPoint(new Location(null, 3.0D, 69.5D, 0.0D), origin, 4.0D, 6.0D));
    }

    @Test
    void pointAoeMissesOutsideRadiusOrVerticalRange() {
        Location origin = new Location(null, 0.0D, 64.0D, 0.0D);

        assertFalse(AoeGeometry.hitsPoint(new Location(null, 5.0D, 64.0D, 0.0D), origin, 4.0D, 6.0D));
        assertFalse(AoeGeometry.hitsPoint(new Location(null, 3.0D, 71.0D, 0.0D), origin, 4.0D, 6.0D));
    }

    @Test
    void laneAoeAllowsSmallHeightDifferences() {
        List<Location> lane = List.of(
                new Location(null, 0.0D, 64.0D, 0.0D),
                new Location(null, 4.0D, 64.0D, 0.0D),
                new Location(null, 8.0D, 64.0D, 0.0D)
        );

        assertTrue(AoeGeometry.hitsLane(new Location(null, 4.0D, 67.5D, 2.0D), lane, 2.5D, 4.0D));
        assertFalse(AoeGeometry.hitsLane(new Location(null, 4.0D, 69.0D, 2.0D), lane, 2.5D, 4.0D));
    }
}
