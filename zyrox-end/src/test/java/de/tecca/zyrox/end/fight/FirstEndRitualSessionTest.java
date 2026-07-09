package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FirstEndRitualSessionTest {

    @Test
    void activationWindowTransformsEveryNodeExactlyOnce() {
        List<PillarRitualNode> nodes = List.of(
                node(10.0D, 0.0D),
                node(0.0D, 10.0D),
                node(-10.0D, 0.0D),
                node(0.0D, -10.0D)
        );
        FirstEndRitualSession ritual = new FirstEndRitualSession(nodes, new Location(null, 0.0D, 65.0D, 0.0D));

        ritual.advance(300L);
        assertEquals(0, ritual.targetActivatedNodes(300L, 1000L));
        ritual.advance(249L);
        assertEquals(0, ritual.targetActivatedNodes(300L, 1000L));
        ritual.advance(1L);
        assertEquals(1, ritual.targetActivatedNodes(300L, 1000L));
        ritual.advance(750L);
        assertEquals(4, ritual.targetActivatedNodes(300L, 1000L));

        for (int i = 0; i < 4; i++) {
            assertTrue(ritual.nextNodeId().isPresent());
            ritual.markActivated();
        }
        assertTrue(ritual.nextNodeId().isEmpty());
    }

    @Test
    void usesExactNinetySecondActBoundaries() {
        FirstEndRitualSession ritual = new FirstEndRitualSession(
                List.of(node(10.0D, 0.0D)),
                new Location(null, 0.0D, 65.0D, 0.0D)
        );

        assertEquals(FirstEndRitualAct.AWAKENING, ritual.act(300L, 1000L, 1640L, 1800L));
        ritual.advance(300L);
        assertEquals(FirstEndRitualAct.TRANSFORMATIONS, ritual.act(300L, 1000L, 1640L, 1800L));
        ritual.advance(1000L);
        assertEquals(FirstEndRitualAct.CONVERGENCE, ritual.act(300L, 1000L, 1640L, 1800L));
        ritual.advance(340L);
        assertEquals(FirstEndRitualAct.FINALE, ritual.act(300L, 1000L, 1640L, 1800L));
        ritual.advance(160L);
        assertEquals(FirstEndRitualAct.COMPLETE, ritual.act(300L, 1000L, 1640L, 1800L));
    }

    @Test
    void channelingIsVisibleBeforeEachTransformation() {
        FirstEndRitualSession ritual = new FirstEndRitualSession(
                List.of(node(10.0D, 0.0D), node(-10.0D, 0.0D)),
                new Location(null, 0.0D, 65.0D, 0.0D)
        );

        ritual.advance(300L);
        assertTrue(ritual.channelingNodeId(300L, 1000L).isPresent());
        assertEquals(0.0D, ritual.channelProgress(300L, 1000L), 0.0001D);
        ritual.advance(499L);
        assertEquals(0, ritual.targetActivatedNodes(300L, 1000L));
        assertEquals(0.998D, ritual.channelProgress(300L, 1000L), 0.0001D);
        ritual.advance(1L);
        assertEquals(1, ritual.targetActivatedNodes(300L, 1000L));
        ritual.markActivated();
        assertEquals(0.0D, ritual.channelProgress(300L, 1000L), 0.0001D);
        assertFalse(ritual.crossed(800L, 300L));
    }

    @Test
    void tenNodesTransformOnceAfterTheirFiveSecondChannel() {
        List<PillarRitualNode> nodes = IntStream.range(0, 10)
                .mapToObj(index -> {
                    double angle = Math.PI * 2.0D * index / 10.0D;
                    return node(Math.cos(angle) * 40.0D, Math.sin(angle) * 40.0D);
                })
                .toList();
        FirstEndRitualSession ritual = new FirstEndRitualSession(
                nodes,
                new Location(null, 0.0D, 65.0D, 0.0D)
        );

        ritual.advance(300L);
        for (int transformed = 0; transformed < 10; transformed++) {
            ritual.advance(99L);
            assertEquals(transformed, ritual.targetActivatedNodes(300L, 1000L));
            ritual.advance(1L);
            assertEquals(transformed + 1, ritual.targetActivatedNodes(300L, 1000L));
            assertTrue(ritual.nextNodeId().isPresent());
            ritual.markActivated();
        }

        assertEquals(10, ritual.activatedNodes());
        assertTrue(ritual.nextNodeId().isEmpty());
        ritual.markActivated();
        assertEquals(10, ritual.activatedNodes());
    }

    private PillarRitualNode node(double x, double z) {
        return new PillarRitualNode(
                UUID.randomUUID(),
                new Location(null, x, 80.0D, z),
                UUID.randomUUID()
        );
    }
}
