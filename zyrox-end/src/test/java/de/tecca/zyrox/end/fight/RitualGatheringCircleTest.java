package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RitualGatheringCircleTest {

    @Test
    void derivesCenterAndRadiusFromTenPillars() {
        List<PillarRitualNode> nodes = IntStream.range(0, 10)
                .mapToObj(index -> {
                    double angle = Math.PI * 2.0D * index / 10.0D;
                    return node(12.0D + Math.cos(angle) * 42.0D, -7.0D + Math.sin(angle) * 42.0D);
                })
                .toList();

        RitualGatheringCircle circle = RitualGatheringCircle.from(nodes, 0.0D, 0.0D, 64.0D);

        assertEquals(12.0D, circle.centerX(), 0.0001D);
        assertEquals(-7.0D, circle.centerZ(), 0.0001D);
        assertEquals(42.0D, circle.radius(), 0.0001D);
        assertTrue(circle.contains(new Location(null, 12.0D, 300.0D, -7.0D)));
        assertTrue(circle.contains(new Location(null, 54.0D, -50.0D, -7.0D)));
        assertFalse(circle.contains(new Location(null, 54.1D, 80.0D, -7.0D)));
    }

    @Test
    void usesArenaFallbackWithoutEnoughPillars() {
        RitualGatheringCircle circle = RitualGatheringCircle.from(List.of(node(4.0D, 4.0D)), 2.0D, 3.0D, 64.0D);

        assertEquals(2.0D, circle.centerX(), 0.0001D);
        assertEquals(3.0D, circle.centerZ(), 0.0001D);
        assertEquals(64.0D, circle.radius(), 0.0001D);
    }

    @Test
    void roundsQuorumUpToARealSeventyFivePercentMajority() {
        assertEquals(0, RitualGatheringCircle.requiredPlayers(0, 0.75D));
        assertEquals(1, RitualGatheringCircle.requiredPlayers(1, 0.75D));
        assertEquals(3, RitualGatheringCircle.requiredPlayers(4, 0.75D));
        assertEquals(4, RitualGatheringCircle.requiredPlayers(5, 0.75D));
    }

    private PillarRitualNode node(double x, double z) {
        return new PillarRitualNode(
                UUID.randomUUID(),
                new Location(null, x, 80.0D, z),
                UUID.randomUUID()
        );
    }
}
