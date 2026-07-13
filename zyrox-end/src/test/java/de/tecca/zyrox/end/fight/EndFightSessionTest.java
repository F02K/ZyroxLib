package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EndFightSessionTest {

    @Test
    void obeliskComboScalesByTenPercentAndCapsAtFiftyPercent() {
        assertEquals(1.0D, EndFightService.obeliskComboMultiplier(0, 0.10D, 0.50D), 0.0001D);
        assertEquals(1.3D, EndFightService.obeliskComboMultiplier(3, 0.10D, 0.50D), 0.0001D);
        assertEquals(1.5D, EndFightService.obeliskComboMultiplier(12, 0.10D, 0.50D), 0.0001D);
    }

    @Test
    void ritualFacingDirectionPointsFromPillarToCenter() {
        Location pillar = new Location(null, 20.0D, 82.0D, -10.0D);
        Location center = new Location(null, 0.0D, 66.0D, 0.0D);

        Vector direction = EndFightService.ritualFacingDirection(pillar, center);
        Vector expected = center.toVector().subtract(pillar.toVector()).normalize();

        assertEquals(1.0D, direction.dot(expected), 0.000001D);
        assertTrue(direction.getX() < 0.0D);
        assertTrue(direction.getY() < 0.0D);
        assertTrue(direction.getZ() > 0.0D);
    }

    @Test
    void phaseThresholdsMatchDragonHealth() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");

        assertEquals(FightPhase.OPENING, session.updatePhase(180.0D, 200.0D));
        assertEquals(FightPhase.TOTEMS, session.updatePhase(150.0D, 200.0D));
        assertEquals(FightPhase.ESCALATION, session.updatePhase(100.0D, 200.0D));
        assertEquals(FightPhase.FINAL, session.updatePhase(50.0D, 200.0D));
    }

    @Test
    void phaseSpawnMarkersOnlyApplyOnce() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");

        assertTrue(session.markPhaseSpawned(FightPhase.TOTEMS));
        assertFalse(session.markPhaseSpawned(FightPhase.TOTEMS));
    }

    @Test
    void exposedWindowExpiresAfterConfiguredTicks() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");

        session.setTick(100L);
        session.startExposedWindow(300L);

        assertTrue(session.exposed());
        session.setTick(401L);
        assertFalse(session.exposed());
    }

    @Test
    void participantsBecomeInactiveAfterTimeout() {
        EndFightConfig config = config();
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        session.markActive(UUID.randomUUID());

        assertEquals(1, session.activeParticipantCount(config));
        session.setTick(config.inactiveAfterTicks() + 1L);
        assertEquals(0, session.activeParticipantCount(config));
    }

    @Test
    void crystalDestructionDeactivatesNode() {
        UUID crystalUniqueId = UUID.randomUUID();
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        PillarRitualNode node = new PillarRitualNode(UUID.randomUUID(), new org.bukkit.Location(null, 0.0D, 80.0D, 0.0D), crystalUniqueId);
        node.assignType(RitualNodeType.DRAGON_WARD);
        session.addNode(node);

        assertEquals(1, session.activeNodeCount(RitualNodeType.DRAGON_WARD));
        session.nodeByCrystal(crystalUniqueId).orElseThrow().deactivate();
        assertEquals(0, session.activeNodeCount(RitualNodeType.DRAGON_WARD));
    }

    @Test
    void crystalDamageDoesNotDeactivateBeforeZeroHealth() {
        PillarRitualNode node = new PillarRitualNode(UUID.randomUUID(), new org.bukkit.Location(null, 0.0D, 80.0D, 0.0D), UUID.randomUUID());
        node.assignType(RitualNodeType.DRAGON_WARD);
        node.setHealth(30.0D);

        assertFalse(node.damage(12.0D, 10L));
        assertTrue(node.active());
        assertEquals(18.0D, node.health(), 0.0001D);

        assertTrue(node.damage(18.0D, 20L));
        node.deactivate();
        assertFalse(node.active());
        assertEquals(20L, node.lastDamagedAtTick());
    }

    @Test
    void lockedCrystalDoesNotTakeDamageBeforeRequiredPhase() {
        PillarRitualNode node = new PillarRitualNode(UUID.randomUUID(), new org.bukkit.Location(null, 0.0D, 80.0D, 0.0D), UUID.randomUUID());
        node.assignType(RitualNodeType.VOID_RIFT);
        node.setHealth(30.0D);

        assertTrue(node.locked());
        assertFalse(node.damage(30.0D, 10L));
        assertEquals(30.0D, node.health(), 0.0001D);

        assertTrue(node.unlockFor(FightPhase.ESCALATION));
        assertFalse(node.locked());
        assertTrue(node.damage(30.0D, 20L));
    }

    @Test
    void fallbackPillarNodeWithoutCrystalStaysPassiveUntilBound() {
        PillarRitualNode node = new PillarRitualNode(UUID.randomUUID(), new org.bukkit.Location(null, 0.0D, 80.0D, 0.0D), null);

        assertFalse(node.active());
        assertTrue(node.crystalUniqueId().isEmpty());
    }

    @Test
    void finalAnchorStaysLockedUntilFinalPhaseUnlock() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        PillarRitualNode node = new PillarRitualNode(UUID.randomUUID(), new org.bukkit.Location(null, 0.0D, 80.0D, 0.0D), null);
        node.bindCrystal(UUID.randomUUID(), true);
        node.setHealth(50.0D);
        node.assignType(RitualNodeType.FINAL_ANCHOR);
        session.addNode(node);
        assertTrue(node.active());
        assertTrue(node.locked());
        assertTrue(node.temporaryCrystal());
        assertEquals(1, session.temporaryCrystalCount());
        assertEquals(0, session.finalAnchorCount());

        session.updatePhase(20.0D, 100.0D);
        assertTrue(node.unlockFor(FightPhase.FINAL));
        assertEquals(1, session.finalAnchorCount());
    }

    @Test
    void thornsCooldownPreventsRepeatedProcUntilExpired() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        UUID nodeId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        assertTrue(session.markThornsTriggered(nodeId, playerId, 80L));
        assertFalse(session.markThornsTriggered(nodeId, playerId, 80L));
        session.setTick(80L);
        assertTrue(session.markThornsTriggered(nodeId, playerId, 80L));
    }

    @Test
    void crystalHitCooldownBlocksSpamAndExpires() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        UUID crystalId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        assertTrue(session.markCrystalHitAllowed(crystalId, playerId, 10L, 4L));
        assertFalse(session.markCrystalHitAllowed(crystalId, playerId, 10L, 4L));

        session.setTick(4L);
        assertFalse(session.markCrystalHitAllowed(crystalId, playerId, 10L, 4L));

        session.setTick(10L);
        assertTrue(session.markCrystalHitAllowed(crystalId, playerId, 10L, 4L));
    }

    @Test
    void globalCrystalCooldownBlocksDifferentPlayersBriefly() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        UUID crystalId = UUID.randomUUID();

        assertTrue(session.markCrystalHitAllowed(crystalId, UUID.randomUUID(), 10L, 4L));
        assertFalse(session.markCrystalHitAllowed(crystalId, UUID.randomUUID(), 10L, 4L));

        session.setTick(4L);
        assertTrue(session.markCrystalHitAllowed(crystalId, UUID.randomUUID(), 10L, 4L));
    }

    @Test
    void guardianFocusExpiresAfterDuration() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        UUID playerId = UUID.randomUUID();

        session.markGuardianFocus(playerId, 80L);

        assertEquals(playerId, session.focusedPlayer().orElseThrow());
        session.setTick(81L);
        assertTrue(session.focusedPlayer().isEmpty());
    }

    @Test
    void reactiveGuardianCooldownExpiresAfterConfiguredTicks() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");

        session.setNextReactiveGuardianSpawnTick(120L);

        assertEquals(120L, session.nextReactiveGuardianSpawnTick());
        session.setTick(119L);
        assertTrue(session.tick() < session.nextReactiveGuardianSpawnTick());
        session.setTick(120L);
        assertFalse(session.tick() < session.nextReactiveGuardianSpawnTick());
    }

    @Test
    void guardianBudgetResetStartsOneFreshFiniteBudget() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        session.beginGuardianPhase(FightPhase.FINAL);
        session.markGuardianSpawned(UUID.randomUUID());
        session.markGuardianSpawned(UUID.randomUUID());
        assertEquals(2, session.guardianBudgetConsumed(FightPhase.FINAL));

        session.resetGuardianBudget(FightPhase.FINAL);
        assertEquals(0, session.guardianBudgetConsumed(FightPhase.FINAL));

        session.markGuardianSpawned(UUID.randomUUID());
        assertEquals(1, session.guardianBudgetConsumed(FightPhase.FINAL));
    }

    @Test
    void finalAnchorDestructionCanOnlyTriggerOnce() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");

        assertTrue(session.markFinalAnchorDestroyed());
        assertFalse(session.markFinalAnchorDestroyed());
    }

    @Test
    void dragonStallStateCountsOnlyWhenNoProgressHappens() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");

        session.recordDragonAttackProgress(new org.bukkit.Location(null, 0.0D, 70.0D, 0.0D), 30.0D, 2.5D, 5L);
        assertEquals(0L, session.dragonAttackStalledTicks());

        session.recordDragonAttackProgress(new org.bukkit.Location(null, 0.5D, 70.0D, 0.0D), 29.0D, 2.5D, 5L);
        assertEquals(5L, session.dragonAttackStalledTicks());

        session.recordDragonAttackProgress(new org.bukkit.Location(null, 4.0D, 70.0D, 0.0D), 26.0D, 2.5D, 5L);
        assertEquals(0L, session.dragonAttackStalledTicks());
    }

    @Test
    void queuedDragonAttackIsConsumedOnce() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");

        session.queueDragonAttack(DragonAttackType.SKY_CRASH);

        assertEquals(DragonAttackType.SKY_CRASH, session.consumeQueuedDragonAttack().orElseThrow());
        assertTrue(session.consumeQueuedDragonAttack().isEmpty());
    }

    @Test
    void dragonAttackIntentCanBeStartedRetriedAndConsumed() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        UUID targetId = UUID.randomUUID();
        UUID newTargetId = UUID.randomUUID();

        session.setTick(100L);
        session.startDragonAttackIntent(DragonAttackType.SKY_CRASH, targetId, 40L, 120L);

        DragonAttackIntent intent = session.dragonAttackIntent().orElseThrow();
        assertEquals(DragonAttackType.SKY_CRASH, intent.type());
        assertEquals(targetId, intent.targetUniqueId());
        assertEquals(140L, intent.nextImpactTick());
        assertEquals(220L, intent.timeoutTick());

        session.replaceDragonAttackIntent(intent.withNextImpactTick(160L));
        assertEquals(160L, session.dragonAttackIntent().orElseThrow().nextImpactTick());
        session.replaceDragonAttackIntent(session.dragonAttackIntent().orElseThrow().withTarget(newTargetId, 170L));
        assertEquals(newTargetId, session.dragonAttackIntent().orElseThrow().targetUniqueId());
        assertEquals(170L, session.dragonAttackIntent().orElseThrow().nextImpactTick());
        assertEquals(DragonAttackType.SKY_CRASH, session.consumeDragonAttackIntent().orElseThrow().type());
        assertTrue(session.dragonAttackIntent().isEmpty());
    }

    @Test
    void dragonAttackIntentDetectsTimeout() {
        DragonAttackIntent intent = new DragonAttackIntent(DragonAttackType.DRAGON_DIVE_MARK, UUID.randomUUID(), 0L, 40L, 100L);

        assertFalse(intent.timedOut(99L));
        assertTrue(intent.timedOut(100L));
    }

    @Test
    void phaseThresholdsUseScaledDragonHealthPercentages() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");

        assertEquals(FightPhase.OPENING, session.updatePhase(400.0D, 500.0D));
        assertEquals(FightPhase.TOTEMS, session.updatePhase(375.0D, 500.0D));
        assertEquals(FightPhase.ESCALATION, session.updatePhase(250.0D, 500.0D));
        assertEquals(FightPhase.FINAL, session.updatePhase(125.0D, 500.0D));
    }

    @Test
    void survivingGuardiansConsumeTheNextPhaseBudget() {
        EndFightSession session = new EndFightSession(UUID.randomUUID(), "world_the_end");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        session.markGuardianSpawned(first);
        session.markGuardianSpawned(second);

        assertEquals(2, session.guardianBudgetConsumed(FightPhase.OPENING));
        session.beginGuardianPhase(FightPhase.TOTEMS);
        assertEquals(2, session.guardianBudgetConsumed(FightPhase.TOTEMS));

        session.removeGuardian(first);
        session.markGuardianSpawned(UUID.randomUUID());
        assertEquals(3, session.guardianBudgetConsumed(FightPhase.TOTEMS));
    }

    @Test
    void fightCanRemainDormantUntilRitualCompletes() {
        EndFightSession session = new EndFightSession(
                UUID.randomUUID(),
                "world_the_end",
                FightProfile.FIRST,
                FightActivationState.DORMANT
        );

        assertFalse(session.active());
        session.setActivationState(FightActivationState.RITUAL);
        assertFalse(session.active());
        session.setActivationState(FightActivationState.ACTIVE);
        assertTrue(session.active());
    }

    @Test
    void firstRitualStartConditionRequiresDormantSessionAndPlayer() {
        assertTrue(EndFightService.shouldStartFirstRitual(FightActivationState.DORMANT, 1));
        assertTrue(EndFightService.shouldStartFirstRitual(FightActivationState.DORMANT, 3));
        assertFalse(EndFightService.shouldStartFirstRitual(FightActivationState.DORMANT, 0));
        assertFalse(EndFightService.shouldStartFirstRitual(FightActivationState.RITUAL, 1));
        assertFalse(EndFightService.shouldStartFirstRitual(FightActivationState.ACTIVE, 1));
    }

    @Test
    void repeatedSignalsStopQualifyingAfterRitualTransition() {
        EndFightSession session = new EndFightSession(
                UUID.randomUUID(),
                "world_the_end",
                FightProfile.FIRST,
                FightActivationState.DORMANT
        );

        assertTrue(EndFightService.shouldStartFirstRitual(session.activationState(), 1));
        session.setActivationState(FightActivationState.RITUAL);
        assertFalse(EndFightService.shouldStartFirstRitual(session.activationState(), 1));
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
