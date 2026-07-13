package de.tecca.zyrox.end.fight;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EndFightConfigTest {

    @Test
    void readsBossBehaviorAndNewAttackDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("fight.behavior.phase-control-enabled", false);
        yaml.set("fight.behavior.combo-enabled", false);
        yaml.set("fight.behavior.anti-stack-player-distance", 6.5D);
        yaml.set("fight.behavior.anti-camp-extra-weight", 4);
        yaml.set("fight.behavior.direct-attack-engage-ticks", 30L);
        yaml.set("fight.behavior.direct-attack-timeout-ticks", 140L);
        yaml.set("fight.behavior.direct-attack-retry-ticks", 15L);
        yaml.set("fight.behavior.direct-attack-chase-speed", 1.8D);
        yaml.set("fight.behavior.direct-attack-control-interval-ticks", 4L);
        yaml.set("fight.behavior.direct-attack-stall-distance", 3.25D);
        yaml.set("fight.behavior.direct-attack-stall-reset-ticks", 16L);
        yaml.set("fight.behavior.direct-attack-stall-reposition-ticks", 36L);
        yaml.set("fight.behavior.fallback-ritual-on-timeout", false);
        yaml.set("fight.crystals.player-hit-cooldown-ticks", 12L);
        yaml.set("fight.crystals.global-hit-cooldown-ticks", 5L);
        yaml.set("fight.crystals.spam-guardian-focus-seconds", 9.0D);
        yaml.set("fight.guardians.spawn-interval-seconds", 11.0D);
        yaml.set("fight.guardians.max-per-player", 3);
        yaml.set("fight.guardians.max-total", 12);
        yaml.set("fight.guardians.phase-weights.totems.enderman", 4);
        yaml.set("fight.guardians.phase-weights.escalation.shulker", 5);
        yaml.set("fight.guardians.void-intercept.damage", 10.0D);
        yaml.set("fight.guardians.anchor-shot.damage", 8.5D);
        yaml.set("fight.guardians.crystal-ward.damage-reduction", 0.65D);
        yaml.set("fight.guardians.reactive.enabled", false);
        yaml.set("fight.guardians.reactive.cooldown-seconds", 9.0D);
        yaml.set("fight.guardians.reactive.phantom-weight", 5);
        yaml.set("fight.guardians.reactive.vex-weight", 6);
        yaml.set("fight.attacks.lane-vertical-range", 5.0D);
        yaml.set("fight.attacks.point-vertical-range", 7.0D);
        yaml.set("fight.attacks.breath-strafe.damage", 9.0D);
        yaml.set("fight.attacks.breath-strafe.engage-range", 70.0D);
        yaml.set("fight.attacks.sky-crash.aftershock-damage", 6.0D);
        yaml.set("fight.attacks.sky-crash.impact-range", 33.0D);
        yaml.set("fight.attacks.wing-gust.damage", 7.0D);
        yaml.set("fight.attacks.void-grasp.max-targets", 2);
        yaml.set("fight.attacks.crystal-barrage.radius", 5.5D);
        yaml.set("fight.attacks.crystal-barrage.impact-range", 88.0D);
        yaml.set("fight.attacks.perch-ritual.damage", 11.0D);

        EndFightConfig config = EndFightConfig.from(yaml);

        assertFalse(config.phaseControlEnabled());
        assertFalse(config.comboEnabled());
        assertEquals(6.5D, config.antiStackPlayerDistance(), 0.0001D);
        assertEquals(4, config.antiCampExtraWeight());
        assertEquals(30L, config.directAttackEngageTicks());
        assertEquals(140L, config.directAttackTimeoutTicks());
        assertEquals(15L, config.directAttackRetryTicks());
        assertEquals(1.8D, config.directAttackChaseSpeed(), 0.0001D);
        assertEquals(4L, config.directAttackControlIntervalTicks());
        assertEquals(3.25D, config.directAttackStallDistance(), 0.0001D);
        assertEquals(16L, config.directAttackStallResetTicks());
        assertEquals(36L, config.directAttackStallRepositionTicks());
        assertFalse(config.fallbackRitualOnTimeout());
        assertEquals(12L, config.crystalPlayerHitCooldownTicks());
        assertEquals(5L, config.crystalGlobalHitCooldownTicks());
        assertEquals(180L, config.crystalSpamGuardianFocusTicks());
        assertEquals(220L, config.guardians().spawnIntervalTicks());
        assertEquals(3, config.guardians().maxPerPlayer());
        assertEquals(12, config.guardians().maxTotal());
        assertEquals(4, config.guardians().totemsEndermanWeight());
        assertEquals(5, config.guardians().escalationShulkerWeight());
        assertEquals(10.0D, config.guardians().endermanVoidInterceptDamage(), 0.0001D);
        assertEquals(8.5D, config.guardians().shulkerAnchorShotDamage(), 0.0001D);
        assertEquals(0.65D, config.guardians().crystalWardReduction(), 0.0001D);
        assertFalse(config.guardians().reactiveEnabled());
        assertEquals(180L, config.guardians().reactiveCooldownTicks());
        assertEquals(5, config.guardians().reactivePhantomWeight());
        assertEquals(6, config.guardians().reactiveVexWeight());
        assertEquals(5.0D, config.laneAttackVerticalRange(), 0.0001D);
        assertEquals(7.0D, config.pointAttackVerticalRange(), 0.0001D);
        assertEquals(9.0D, config.breathStrafeDamage(), 0.0001D);
        assertEquals(70.0D, config.breathStrafeEngageRange(), 0.0001D);
        assertEquals(6.0D, config.skyCrashAftershockDamage(), 0.0001D);
        assertEquals(33.0D, config.skyCrashImpactRange(), 0.0001D);
        assertEquals(7.0D, config.wingGustDamage(), 0.0001D);
        assertEquals(2, config.voidGraspMaxTargets());
        assertEquals(5.5D, config.crystalBarrageRadius(), 0.0001D);
        assertEquals(88.0D, config.crystalBarrageImpactRange(), 0.0001D);
        assertEquals(11.0D, config.perchRitualDamage(), 0.0001D);
    }

    @Test
    void boostedAttackDefaultsAreApplied() {
        EndFightConfig config = EndFightConfig.from(new YamlConfiguration());

        assertEquals(13.0D, config.diveDamage(), 0.0001D);
        assertEquals(12.0D, config.pillarBeamDamage(), 0.0001D);
        assertEquals(10.5D, config.enderVolleyDamage(), 0.0001D);
        assertEquals(6.0D, config.wingGustDamage(), 0.0001D);
        assertEquals(9.5D, config.voidRiftDamage(), 0.0001D);
        assertEquals(1.25D, config.directAttackChaseSpeed(), 0.0001D);
        assertEquals(5L, config.directAttackControlIntervalTicks());
        assertEquals(2.5D, config.directAttackStallDistance(), 0.0001D);
        assertEquals(20L, config.directAttackStallResetTicks());
        assertEquals(45L, config.directAttackStallRepositionTicks());
        assertEquals(10L, config.crystalPlayerHitCooldownTicks());
        assertEquals(4L, config.crystalGlobalHitCooldownTicks());
        assertEquals(160L, config.crystalSpamGuardianFocusTicks());
        assertTrue(config.guardians().enabled());
        assertEquals(14, config.guardians().maxForPlayers(5));
        assertEquals(9.5D, config.breathStrafeDamage(), 0.0001D);
        assertEquals(15.5D, config.skyCrashDamage(), 0.0001D);
        assertEquals(6.5D, config.skyCrashAftershockDamage(), 0.0001D);
        assertEquals(9.5D, config.wingRingDamage(), 0.0001D);
        assertEquals(10.5D, config.voidGraspDamage(), 0.0001D);
        assertEquals(10.5D, config.crystalBarrageDamage(), 0.0001D);
        assertEquals(10.5D, config.perchRitualDamage(), 0.0001D);
        assertEquals(6.0D, config.pointAttackVerticalRange(), 0.0001D);
        assertEquals(4.0D, config.laneAttackVerticalRange(), 0.0001D);
        assertEquals(2, config.guardians().phantomMaxAlive());
        assertEquals(2, config.guardians().vexMaxAlive());
        assertEquals(10.5D, config.guardians().phantomDiveDamage(), 0.0001D);
        assertEquals(1.05D, config.guardians().phantomLaunchVelocity(), 0.0001D);
        assertEquals(30L, config.guardians().phantomWarningTicks());
        assertEquals(120L, config.guardians().phantomAttackCooldownTicks());
        assertEquals(7.5D, config.guardians().vexCurseDamage(), 0.0001D);
        assertEquals(160L, config.guardians().vexCurseTicks());
        assertEquals(0.20D, config.guardians().vexOutgoingReduction(), 0.0001D);
        assertEquals(0.45D, config.guardians().vexIncomingIncrease(), 0.0001D);
        assertEquals(100L, config.guardians().vexAttackCooldownTicks());
        assertTrue(config.guardians().reactiveEnabled());
        assertEquals(240L, config.guardians().reactiveCooldownTicks());
        assertEquals(3, config.guardians().reactivePhantomWeight());
        assertEquals(4, config.guardians().reactiveVexWeight());
        assertEquals(300.0D, config.guardians().obeliskMaxHealth(), 0.0001D);
        assertEquals(0.10D, config.guardians().obeliskComboStep(), 0.0001D);
        assertEquals(0.50D, config.guardians().obeliskComboCap(), 0.0001D);
        assertTrue(config.guardians().totemsPhantomWeight() > config.guardians().totemsEndermanWeight());
        assertEquals(330.0D, config.finalAnchor().health(0), 0.0001D);
        assertEquals(40L, config.finalAnchor().groundChargeTicks());
        assertEquals(50L, config.finalAnchor().beamChargeTicks());
        assertEquals(80L, config.finalAnchor().beamSweepTicks());
        assertEquals(3, config.finalAnchor().waveCount());
    }

    @Test
    void finalAnchorSettingsAreConfigurableAndSafelyBounded() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("fight.final-phase.anchor.base-health", -20.0D);
        yaml.set("fight.final-phase.anchor.health-per-participant", 40.0D);
        yaml.set("fight.final-phase.anchor.attack-cooldown-seconds", 6.0D);
        yaml.set("fight.final-phase.anchor.ground.wave-count", 0);
        yaml.set("fight.final-phase.anchor.ground.wave-speed-per-tick", 1.2D);
        yaml.set("fight.final-phase.anchor.beam.width", -1.0D);
        yaml.set("fight.final-phase.anchor.particle-points", 5);

        EndFightConfig.FinalAnchorConfig anchor = EndFightConfig.from(yaml).finalAnchor();

        assertEquals(1.0D, anchor.baseHealth(), 0.0001D);
        assertEquals(41.0D, anchor.health(1), 0.0001D);
        assertEquals(120L, anchor.attackCooldownTicks());
        assertEquals(1, anchor.waveCount());
        assertEquals(1.2D, anchor.waveSpeedPerTick(), 0.0001D);
        assertEquals(0.25D, anchor.beamWidth(), 0.0001D);
        assertEquals(24, anchor.particlePoints());
        assertTrue(anchor.groundExecutionTicks(96.0D) > anchor.groundExecutionTicks());
    }

    @Test
    void repeatProfileAndGuardianBudgetsUsePlannedDefaults() {
        EndFightSettings settings = EndFightSettings.from(new YamlConfiguration());

        assertEquals(850.0D, settings.dragonHealth(FightProfile.REPEAT, 1000.0D), 0.0001D);
        assertEquals(85.0D, settings.nodeHealth(FightProfile.REPEAT, 100.0D), 0.0001D);
        assertEquals(8.5D, settings.damage(FightProfile.REPEAT, 10.0D), 0.0001D);
        assertEquals(230L, settings.cooldown(FightProfile.REPEAT, 200L));
        assertEquals(11, settings.guardianBudget(FightProfile.FIRST, FightPhase.TOTEMS, 4));
        assertEquals(9, settings.guardianBudget(FightProfile.REPEAT, FightPhase.TOTEMS, 4));
        assertEquals(16, settings.guardianBudget(FightProfile.FIRST, FightPhase.ESCALATION, 4));
        assertEquals(12, settings.guardianBudget(FightProfile.REPEAT, FightPhase.ESCALATION, 4));
        assertEquals(0, settings.guardianBudget(FightProfile.FIRST, FightPhase.OPENING, 10));
    }

    @Test
    void ritualTimingAndBudgetsAreConfigurable() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("fight.first-ritual.total-seconds", 50.0D);
        yaml.set("fight.first-ritual.build-up-seconds", 10.0D);
        yaml.set("fight.first-ritual.activation-seconds", 30.0D);
        yaml.set("fight.first-ritual.gathering.required-ratio", 0.8D);
        yaml.set("fight.first-ritual.gathering.hold-seconds", 12.0D);
        yaml.set("fight.first-ritual.gathering.particle-points", 80);
        yaml.set("fight.guardians.phase-budgets.final.base", 5);
        yaml.set("fight.guardians.phase-budgets.final.per-player", 3);
        yaml.set("fight.guardians.phase-budgets.final.maximum", 15);
        yaml.set("fight.cinematics.tick-interval", 3L);
        yaml.set("fight.cinematics.maximum-view-range", 220.0D);
        yaml.set("fight.cinematics.max-points-per-frame", 1200);
        yaml.set("fight.cinematics.final-activation-seconds", 4.0D);
        yaml.set("fight.cinematics.attack-density", 1.4D);
        yaml.set("fight.cinematics.respawn-density", 1.8D);

        EndFightSettings settings = EndFightSettings.from(yaml);

        assertEquals(1000L, settings.ritual().totalTicks());
        assertEquals(200L, settings.ritual().buildUpTicks());
        assertEquals(600L, settings.ritual().activationTicks());
        assertEquals(800L, settings.ritual().convergenceStartTick());
        assertEquals(840L, settings.ritual().finaleStartTick());
        assertEquals(4L, settings.ritual().animationIntervalTicks());
        assertEquals(0.8D, settings.gathering().requiredRatio(), 0.0001D);
        assertEquals(240L, settings.gathering().holdTicks());
        assertEquals(80, settings.gathering().particlePoints());
        assertEquals(14, settings.guardianBudget(FightProfile.FIRST, FightPhase.FINAL, 3));
        assertTrue(settings.cinematics().enabled());
        assertEquals(3L, settings.cinematics().tickInterval());
        assertEquals(220.0D, settings.cinematics().maximumViewRange(), 0.0001D);
        assertEquals(1200, settings.cinematics().maxPointsPerFrame());
        assertEquals(80L, settings.cinematics().finalActivationTicks());
        assertEquals(1.4D, settings.cinematics().attackDensity(), 0.0001D);
        assertEquals(1.8D, settings.cinematics().respawnDensity(), 0.0001D);
    }
}
