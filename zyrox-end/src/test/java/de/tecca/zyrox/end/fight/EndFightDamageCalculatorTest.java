package de.tecca.zyrox.end.fight;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EndFightDamageCalculatorTest {

    private final EndFightDamageCalculator calculator = new EndFightDamageCalculator();

    @Test
    void shieldReductionCapsCorrectly() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        session.addNode(node(RitualNodeType.DRAGON_WARD));
        session.addNode(node(RitualNodeType.DRAGON_WARD));
        session.addNode(node(RitualNodeType.DRAGON_WARD));
        session.addNode(node(RitualNodeType.DRAGON_WARD));

        assertEquals(55.0D, calculator.dragonDamage(100.0D, session, config()), 0.0001D);
    }

    @Test
    void activeFinalAnchorMakesDragonImmuneInFinalPhase() {
        EndFightConfig config = config();
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        session.addNode(node(RitualNodeType.FINAL_ANCHOR));

        assertEquals(100.0D, calculator.dragonDamage(100.0D, session, config), 0.0001D);
        session.updatePhase(25.0D, 200.0D);
        session.finalAnchor().orElseThrow().unlockFor(FightPhase.FINAL);
        assertEquals(0.0D, calculator.dragonDamage(100.0D, session, config), 0.0001D);
        session.finalAnchor().orElseThrow().deactivate();
        assertEquals(100.0D, calculator.dragonDamage(100.0D, session, config), 0.0001D);
    }

    @Test
    void exposedWindowAddsBonusDamage() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        session.startExposedWindow(300L);

        assertEquals(120.0D, calculator.dragonDamage(100.0D, session, config()), 0.0001D);
    }

    @Test
    void explosionDamageIsCapped() {
        assertEquals(10.0D, calculator.cappedExplosionDamage(40.0D, config()), 0.0001D);
        assertEquals(8.0D, calculator.cappedExplosionDamage(8.0D, config()), 0.0001D);
    }

    private EndFightConfig config() {
        return new EndFightConfig(
                true,
                true,
                0.0D,
                0.0D,
                128.0D,
                64.0D,
                120.0D,
                12.0D,
                0.15D,
                0.45D,
                0.20D,
                300L,
                900L,
                100L,
                500L,
                300L,
                60L,
                1800L,
                10.0D,
                78.0D,
                400L
        );
    }

    private PillarRitualNode node(RitualNodeType type) {
        PillarRitualNode node = new PillarRitualNode(UUID.randomUUID(), new org.bukkit.Location(null, 0.0D, 80.0D, 0.0D), UUID.randomUUID());
        node.assignType(type);
        return node;
    }
}
