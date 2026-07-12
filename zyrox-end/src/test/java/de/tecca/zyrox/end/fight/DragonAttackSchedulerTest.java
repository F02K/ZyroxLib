package de.tecca.zyrox.end.fight;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DragonAttackSchedulerTest {

    private final DragonAttackScheduler scheduler = new DragonAttackScheduler();

    @Test
    void noAttackWithoutActivePlayers() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");

        assertTrue(scheduler.nextAttack(session, config(), 0).isEmpty());
    }

    @Test
    void diveAttackIsAvailableWithoutAttackTotems() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");

        assertEquals(DragonAttackType.DRAGON_DIVE_MARK, scheduler.nextAttack(session, config(), 1).orElseThrow());
    }

    @Test
    void openingContainsIndependentDragonAttacksWithoutTotems() {
        EndFightSession breathSession = new EndFightSession(UUID.randomUUID(), "world_the_end");
        breathSession.setTick(20L);
        assertEquals(DragonAttackType.BREATH_STRAFE, scheduler.nextAttack(breathSession, config(), 1).orElseThrow());

        EndFightSession ringSession = new EndFightSession(UUID.randomUUID(), "world_the_end");
        ringSession.setTick(60L);
        assertEquals(DragonAttackType.WING_RING, scheduler.nextAttack(ringSession, config(), 1).orElseThrow());
    }

    @Test
    void cooldownUsesStormReductionAndMinimum() {
        EndFightConfig config = config();
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        session.addNode(activeNode(RitualNodeType.STORM_SPIRE));
        session.addNode(activeNode(RitualNodeType.STORM_SPIRE));

        assertEquals(config.attackBaseCooldownTicks() - 2L * config.attackStormReductionTicks(), scheduler.cooldown(session, config));

        for (int i = 0; i < 20; i++) {
            session.addNode(activeNode(RitualNodeType.STORM_SPIRE));
        }
        assertEquals(config.attackMinimumCooldownTicks(), scheduler.cooldown(session, config));
    }

    @Test
    void doesNotImmediatelyRepeatAttackWhenAlternativesExist() {
        EndFightConfig config = config();
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        session.addNode(activeNode(RitualNodeType.PILLAR_BEAM));
        session.addNode(activeNode(RitualNodeType.VOID_RIFT));
        DragonAttackType first = scheduler.nextAttack(session, config, 1).orElseThrow();

        session.setTick(session.nextDragonAttackTick());
        DragonAttackType second = scheduler.nextAttack(session, config, 1).orElseThrow();

        assertNotEquals(first, second);
    }

    @Test
    void lockedAttackTotemDoesNotEnableAttack() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        PillarRitualNode node = activeNode(RitualNodeType.VOID_RIFT);
        node.assignType(RitualNodeType.VOID_RIFT);
        session.addNode(node);

        assertNotEquals(DragonAttackType.VOID_RIFT, scheduler.nextAttack(session, config(), 1).orElseThrow());
    }

    @Test
    void finalPhaseUsesFinalCooldownWithMinimumCap() {
        EndFightConfig config = config();
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        session.updatePhase(20.0D, 100.0D);

        assertEquals(config.attackFinalCooldownTicks(), scheduler.cooldown(session, config));

        for (int i = 0; i < 20; i++) {
            session.addNode(activeNode(RitualNodeType.STORM_SPIRE));
        }
        assertEquals(config.attackMinimumCooldownTicks(), scheduler.cooldown(session, config));
    }

    @Test
    void finalVoidGraspQueuesSkyCrashCombo() {
        EndFightConfig config = config();
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        session.updatePhase(20.0D, 100.0D);
        session.setTick(80L);

        assertEquals(DragonAttackType.VOID_GRASP, scheduler.nextAttack(session, config, 1).orElseThrow());
        session.setTick(session.nextDragonAttackTick());
        assertEquals(DragonAttackType.SKY_CRASH, scheduler.nextAttack(session, config, 1).orElseThrow());
    }

    private PillarRitualNode activeNode(RitualNodeType type) {
        PillarRitualNode node = new PillarRitualNode(UUID.randomUUID(), new org.bukkit.Location(null, 0.0D, 80.0D, 0.0D), UUID.randomUUID());
        node.assignType(type);
        node.unlockFor(FightPhase.FINAL);
        node.setHealth(50.0D);
        return node;
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
}
