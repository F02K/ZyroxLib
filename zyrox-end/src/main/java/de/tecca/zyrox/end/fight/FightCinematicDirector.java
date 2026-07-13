package de.tecca.zyrox.end.fight;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class FightCinematicDirector {

    private EndFightSettings.Cinematics settings;
    private final Map<String, Long> clocks = new HashMap<>();
    private final Map<String, Long> finalAnchorBlockedUntil = new HashMap<>();
    private final Map<String, PhaseSequence> phaseSequences = new HashMap<>();
    private final Map<String, List<AttackSequence>> attackSequences = new HashMap<>();
    private final Map<String, CollapseSequence> collapseSequences = new HashMap<>();
    private final Map<String, RespawnSequence> respawnSequences = new HashMap<>();

    FightCinematicDirector(EndFightSettings.Cinematics settings) {
        this.settings = settings;
    }

    void reload(EndFightSettings.Cinematics settings) {
        this.settings = settings;
        if (!settings.enabled()) {
            phaseSequences.clear();
            attackSequences.clear();
            collapseSequences.clear();
            respawnSequences.clear();
            finalAnchorBlockedUntil.clear();
        }
    }

    long tickInterval() {
        return settings.tickInterval();
    }

    void beginPhase(World world, FightPhase phase, Location center) {
        if (!settings.enabled() || world == null || center == null) {
            return;
        }
        long transition = phase == FightPhase.FINAL
                ? settings.finalActivationTicks()
                : settings.phaseTransitionTicks();
        phaseSequences.put(
                world.getName(),
                new PhaseSequence(
                        phase,
                        center.clone(),
                        new FightCinematicSequence(
                                Math.max(1L, transition * 2L / 3L),
                                Math.max(1L, transition / 8L),
                                Math.max(1L, transition / 8L),
                                settings.aftermathTicks()
                        )
                )
        );
        if (phase == FightPhase.FINAL) {
            finalAnchorBlockedUntil.put(world.getName(), clock(world) + transition);
        }
    }

    void beginAttack(
            World world,
            DragonAttackType type,
            Location origin,
            List<Location> targets,
            long windupTicks
    ) {
        if (!settings.enabled() || world == null || origin == null || type == null) {
            return;
        }
        attackSequences.computeIfAbsent(world.getName(), ignored -> new ArrayList<>())
                .add(new AttackSequence(
                        type,
                        origin.clone(),
                        copyLocations(targets),
                        new FightCinematicSequence(
                                Math.max(1L, windupTicks),
                                2L,
                                4L,
                                settings.aftermathTicks()
                        )
                ));
    }

    void beginArenaEvent(World world, ArenaEventType type, Location origin, List<Location> targets, long windupTicks) {
        DragonAttackType visualType = switch (type) {
            case VOID_RIFT -> DragonAttackType.VOID_GRASP;
            case BREATH_STORM -> DragonAttackType.BREATH_STRAFE;
            case SHOCKWAVE -> DragonAttackType.WING_RING;
        };
        beginAttack(world, visualType, origin, targets, windupTicks);
    }

    void beginAnchorCollapse(World world, Location origin) {
        if (!settings.enabled() || world == null || origin == null) {
            return;
        }
        collapseSequences.put(
                world.getName(),
                new CollapseSequence(
                        origin.clone(),
                        new FightCinematicSequence(12L, 4L, 6L, settings.aftermathTicks())
                )
        );
    }

    boolean finalAnchorReady(String worldName) {
        return !settings.enabled()
                || clock(worldName) >= finalAnchorBlockedUntil.getOrDefault(worldName, 0L);
    }

    void advanceClock(World world) {
        if (!settings.enabled() || world == null) {
            return;
        }
        clocks.merge(world.getName(), settings.tickInterval(), Long::sum);
    }

    void beginRespawnPhase(
            World world,
            DragonBattle.RespawnPhase phase,
            Location center,
            List<Location> crystals
    ) {
        if (!settings.enabled() || world == null || phase == null || center == null) {
            return;
        }
        if (phase == DragonBattle.RespawnPhase.NONE) {
            return;
        }
        if (phase == DragonBattle.RespawnPhase.END) {
            respawnSequences.put(
                    world.getName(),
                    new RespawnSequence(
                            phase,
                            center.clone(),
                            copyLocations(crystals),
                            new FightCinematicSequence(1L, 6L, 18L, Math.max(settings.aftermathTicks(), 60L))
                    )
            );
            return;
        }
        respawnSequences.put(
                world.getName(),
                new RespawnSequence(
                        phase,
                        center.clone(),
                        copyLocations(crystals),
                        new FightCinematicSequence(180L, 24L, 60L, 120L)
                )
        );
    }

    void tickRespawn(World world, Location center) {
        if (!settings.enabled() || world == null || center == null) {
            return;
        }
        RespawnSequence sequence = respawnSequences.get(world.getName());
        if (sequence == null) {
            return;
        }
        sequence.sequence().advance(settings.tickInterval());
        CinematicFrameBudget budget = new CinematicFrameBudget(settings.maxPointsPerFrame());
        renderRespawnSequence(world, sequence, budget);
        if (sequence.phase() == DragonBattle.RespawnPhase.END && sequence.sequence().complete()) {
            respawnSequences.remove(world.getName());
        }
    }

    boolean hasRespawnSequence(String worldName) {
        return respawnSequences.containsKey(worldName);
    }

    static boolean activeRespawnPhase(DragonBattle.RespawnPhase phase) {
        return phase != null
                && phase != DragonBattle.RespawnPhase.NONE
                && phase != DragonBattle.RespawnPhase.END;
    }

    void tick(World world, EndFightSession session, EnderDragon dragon, Location center) {
        if (!settings.enabled() || world == null || session == null || dragon == null || center == null) {
            return;
        }
        CinematicFrameBudget budget = new CinematicFrameBudget(settings.maxPointsPerFrame());
        renderAmbient(world, session, dragon, center, budget);
        renderPhaseSequence(world, dragon, budget);
        renderAttackSequences(world, budget);
        renderCollapseSequence(world, budget);
    }

    void clear(String worldName) {
        clocks.remove(worldName);
        finalAnchorBlockedUntil.remove(worldName);
        phaseSequences.remove(worldName);
        attackSequences.remove(worldName);
        collapseSequences.remove(worldName);
        respawnSequences.remove(worldName);
    }

    void clearRespawn(String worldName) {
        respawnSequences.remove(worldName);
    }

    private void renderAmbient(
            World world,
            EndFightSession session,
            EnderDragon dragon,
            Location center,
            CinematicFrameBudget budget
    ) {
        long clock = clock(world);
        double phase = clock / 12.0D;
        if (session.activationState() != FightActivationState.ACTIVE) {
            orbit(world, center.clone().add(0.0D, 0.25D, 0.0D), 5.0D, phase, Particle.REVERSE_PORTAL, 42,
                    settings.ritualDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
            orbit(world, center.clone().add(0.0D, 0.4D, 0.0D), 9.0D, -phase * 0.55D, Particle.SOUL_FIRE_FLAME, 54,
                    settings.ritualDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
            verticalColumn(world, center, 18.0D, phase, Particle.REVERSE_PORTAL, 28,
                    settings.ritualDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
            ambientSound(world, center, clock, 80L, Sound.BLOCK_BEACON_AMBIENT, 0.55F, 0.55F);
            return;
        }

        trail(world, dragon.getLocation(), dragon.getVelocity(), session.phase(), budget);
        switch (session.phase()) {
            case OPENING -> {
                groundRing(world, center, 4.5D, phase, Particle.REVERSE_PORTAL, 32,
                        settings.phaseDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
                risingMotes(world, center, clock, Particle.SOUL_FIRE_FLAME, 24, budget);
                ambientSound(world, center, clock, 100L, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.45F, 0.55F);
            }
            case TOTEMS -> {
                renderTotemAuras(world, session, dragon, phase, budget);
                orbit(world, center, 12.0D, -phase * 0.35D, Particle.END_ROD, 48,
                        settings.phaseDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
                ambientSound(world, center, clock, 70L, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.65F, 0.65F);
            }
            case ESCALATION -> {
                renderSkyRifts(world, center, phase, budget);
                orbit(world, center.clone().add(0.0D, 14.0D, 0.0D), 20.0D, phase * 0.4D, Particle.DRAGON_BREATH, 64,
                        settings.phaseDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
                ambientSound(world, center, clock, 55L, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8F, 0.65F);
            }
            case FINAL -> renderFinalAmbient(world, session, dragon, center, phase, clock, budget);
        }
    }

    private void renderTotemAuras(
            World world,
            EndFightSession session,
            EnderDragon dragon,
            double phase,
            CinematicFrameBudget budget
    ) {
        int index = 0;
        for (PillarRitualNode node : session.nodes()) {
            if (!node.active() || node.type() == RitualNodeType.FINAL_ANCHOR) {
                continue;
            }
            Location top = node.pillarTop();
            if (node.locked()) {
                brokenRing(world, top, 2.2D, phase + index, Particle.REVERSE_PORTAL, 30, budget);
            } else {
                orbit(world, top, 1.6D, phase + index, Particle.END_ROD, 24,
                        settings.phaseDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
                if ((clock(world) / settings.tickInterval() + index) % 8L == 0L) {
                    arc(world, top, dragon.getLocation().add(0.0D, 2.0D, 0.0D), Particle.REVERSE_PORTAL, 30,
                            settings.phaseDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
                }
            }
            index++;
        }
    }

    private void renderSkyRifts(
            World world,
            Location center,
            double phase,
            CinematicFrameBudget budget
    ) {
        for (int index = 0; index < 3; index++) {
            double angle = phase * 0.15D + index * Math.PI * 2.0D / 3.0D;
            Location from = center.clone().add(Math.cos(angle) * 26.0D, 20.0D + index * 4.0D, Math.sin(angle) * 26.0D);
            Location to = from.clone().add(Math.sin(angle) * 7.0D, 10.0D, -Math.cos(angle) * 7.0D);
            beam(world, from, to, Particle.REVERSE_PORTAL, 24,
                    settings.phaseDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
        }
    }

    private void renderFinalAmbient(
            World world,
            EndFightSession session,
            EnderDragon dragon,
            Location center,
            double phase,
            long clock,
            CinematicFrameBudget budget
    ) {
        session.finalAnchor().filter(PillarRitualNode::active).ifPresent(anchor -> {
            Location origin = anchor.pillarTop();
            orbit(world, origin, 2.5D, phase, Particle.END_ROD, 44,
                    settings.anchorDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
            orbit(world, origin.clone().add(0.0D, 1.0D, 0.0D), 5.0D, -phase * 0.7D, Particle.DRAGON_BREATH, 60,
                    settings.anchorDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
            verticalColumn(world, origin, 12.0D, phase, Particle.REVERSE_PORTAL, 38,
                    settings.anchorDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
            beam(world, origin.clone().add(0.0D, 2.0D, 0.0D), dragon.getLocation().add(0.0D, 2.0D, 0.0D),
                    Particle.END_ROD, 56, settings.anchorDensity(), CinematicFrameBudget.Priority.TELEGRAPH, budget);
        });
        orbit(world, center, 30.0D, phase * 0.15D, Particle.REVERSE_PORTAL, 80,
                settings.phaseDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
        ambientSound(world, center, clock, 40L, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.9F, 0.5F);
    }

    private void renderPhaseSequence(World world, EnderDragon dragon, CinematicFrameBudget budget) {
        PhaseSequence active = phaseSequences.get(world.getName());
        if (active == null) {
            return;
        }
        active.sequence().advance(settings.tickInterval());
        FightCinematicSequence.Stage stage = active.sequence().stage();
        double progress = active.sequence().stageProgress();
        Location center = active.center();
        if (stage == FightCinematicSequence.Stage.WINDUP) {
            orbit(world, center, 28.0D * (1.0D - progress) + 3.0D, progress * Math.PI * 4.0D,
                    Particle.REVERSE_PORTAL, 110, settings.phaseDensity(), CinematicFrameBudget.Priority.TELEGRAPH, budget);
            beam(world, center.clone().add(0.0D, 1.0D, 0.0D), dragon.getLocation().add(0.0D, 2.0D, 0.0D),
                    Particle.DRAGON_BREATH, 60, settings.phaseDensity(), CinematicFrameBudget.Priority.TELEGRAPH, budget);
            if (active.sequence().fireOnce("windup")) {
                playLayer(world, center, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 2.2F, 0.45F);
                playLayer(world, center, Sound.BLOCK_BEACON_AMBIENT, 1.8F, 0.5F);
            }
        } else if (stage == FightCinematicSequence.Stage.RELEASE || stage == FightCinematicSequence.Stage.IMPACT) {
            for (int ring = 1; ring <= 4; ring++) {
                groundRing(world, center, ring * (4.0D + progress * 5.0D), ring * 0.3D,
                        ring % 2 == 0 ? Particle.END_ROD : Particle.DRAGON_BREATH, 72,
                        settings.phaseDensity(), CinematicFrameBudget.Priority.IMPACT, budget);
            }
            verticalColumn(world, center, 34.0D, progress * Math.PI * 2.0D, Particle.END_ROD, 72,
                    settings.phaseDensity(), CinematicFrameBudget.Priority.IMPACT, budget);
            if (active.sequence().fireOnce("release")) {
                playLayer(world, center, Sound.BLOCK_END_PORTAL_SPAWN, 3.2F, 0.55F);
                playLayer(world, center, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.5F, 0.65F);
                world.strikeLightningEffect(center);
            }
        } else if (stage == FightCinematicSequence.Stage.AFTERMATH) {
            risingMotes(world, center, active.sequence().elapsedTicks(), Particle.SOUL_FIRE_FLAME, 50, budget);
        }
        if (active.sequence().complete()) {
            phaseSequences.remove(world.getName());
        }
    }

    private void renderAttackSequences(World world, CinematicFrameBudget budget) {
        List<AttackSequence> sequences = attackSequences.get(world.getName());
        if (sequences == null) {
            return;
        }
        Iterator<AttackSequence> iterator = sequences.iterator();
        while (iterator.hasNext()) {
            AttackSequence attack = iterator.next();
            attack.sequence().advance(settings.tickInterval());
            renderAttack(world, attack, budget);
            if (attack.sequence().complete()) {
                iterator.remove();
            }
        }
        if (sequences.isEmpty()) {
            attackSequences.remove(world.getName());
        }
    }

    private void renderAttack(World world, AttackSequence attack, CinematicFrameBudget budget) {
        FightCinematicSequence.Stage stage = attack.sequence().stage();
        double progress = attack.sequence().stageProgress();
        Particle primary = attackParticle(attack.type());
        if (stage == FightCinematicSequence.Stage.WINDUP) {
            orbit(world, attack.origin(), 1.5D + progress * 3.5D, progress * Math.PI * 5.0D, primary, 46,
                    settings.attackDensity(), CinematicFrameBudget.Priority.TELEGRAPH, budget);
            for (Location target : attack.targets()) {
                renderAttackTelegraph(world, attack.type(), attack.origin(), target, progress, primary, budget);
            }
            if (attack.sequence().fireOnce("windup")) {
                playLayer(world, attack.origin(), attackWindupSound(attack.type()), 1.6F, 0.55F);
            }
        } else if (stage == FightCinematicSequence.Stage.RELEASE || stage == FightCinematicSequence.Stage.IMPACT) {
            for (Location target : attack.targets()) {
                beam(world, attack.origin(), target, primary, 54,
                        settings.attackDensity(), CinematicFrameBudget.Priority.IMPACT, budget);
                orbit(world, target, 2.0D + progress * 5.0D, progress * Math.PI * 3.0D, Particle.END_ROD, 56,
                        settings.attackDensity(), CinematicFrameBudget.Priority.IMPACT, budget);
            }
            if (attack.sequence().fireOnce("impact")) {
                playLayer(world, attack.origin(), attackImpactSound(attack.type()), 2.1F, 0.7F);
            }
        } else if (stage == FightCinematicSequence.Stage.AFTERMATH) {
            for (Location target : attack.targets()) {
                risingMotes(world, target, attack.sequence().elapsedTicks(), primary, 22, budget);
            }
        }
    }

    private void renderAttackTelegraph(
            World world,
            DragonAttackType type,
            Location origin,
            Location target,
            double progress,
            Particle particle,
            CinematicFrameBudget budget
    ) {
        switch (type) {
            case DRAGON_DIVE_MARK, BREATH_STRAFE, SKY_CRASH, PILLAR_BEAM ->
                    beam(world, origin, target, particle, 48,
                            settings.attackDensity(), CinematicFrameBudget.Priority.TELEGRAPH, budget);
            case WING_GUST ->
                    cone(world, origin, target, progress, particle, 54, budget);
            case WING_RING, VOID_GRASP, PERCH_RITUAL ->
                    groundRing(world, target, 3.0D + progress * 9.0D, -progress * Math.PI * 4.0D, particle, 64,
                            settings.attackDensity(), CinematicFrameBudget.Priority.TELEGRAPH, budget);
            case VOID_RIFT, CRYSTAL_BARRAGE, ENDER_VOLLEY ->
                    brokenRing(world, target, 2.0D + progress * 3.0D, progress * Math.PI * 4.0D, particle, 48, budget);
        }
    }

    private void renderCollapseSequence(World world, CinematicFrameBudget budget) {
        CollapseSequence collapse = collapseSequences.get(world.getName());
        if (collapse == null) {
            return;
        }
        collapse.sequence().advance(settings.tickInterval());
        double progress = collapse.sequence().stageProgress();
        if (collapse.sequence().stage() == FightCinematicSequence.Stage.WINDUP) {
            for (int ring = 0; ring < 5; ring++) {
                orbit(world, collapse.origin(), Math.max(0.5D, (18.0D + ring * 3.0D) * (1.0D - progress)),
                        progress * Math.PI * 6.0D + ring, Particle.REVERSE_PORTAL, 70,
                        settings.anchorDensity(), CinematicFrameBudget.Priority.IMPACT, budget);
            }
        } else if (collapse.sequence().stage() == FightCinematicSequence.Stage.RELEASE
                || collapse.sequence().stage() == FightCinematicSequence.Stage.IMPACT) {
            verticalColumn(world, collapse.origin(), 30.0D, progress * Math.PI * 5.0D, Particle.END_ROD, 90,
                    settings.anchorDensity(), CinematicFrameBudget.Priority.IMPACT, budget);
            for (int ring = 1; ring <= 5; ring++) {
                orbit(world, collapse.origin(), ring * 5.0D, ring, Particle.DRAGON_BREATH, 80,
                        settings.anchorDensity(), CinematicFrameBudget.Priority.IMPACT, budget);
            }
            if (collapse.sequence().fireOnce("collapse")) {
                world.strikeLightningEffect(collapse.origin());
                playLayer(world, collapse.origin(), Sound.BLOCK_END_PORTAL_SPAWN, 3.5F, 0.45F);
                playLayer(world, collapse.origin(), Sound.ENTITY_GENERIC_EXPLODE, 3.0F, 0.6F);
            }
        }
        if (collapse.sequence().complete()) {
            collapseSequences.remove(world.getName());
        }
    }

    private void renderRespawnSequence(World world, RespawnSequence respawn, CinematicFrameBudget budget) {
        long clock = clock(world);
        double phase = clock / 9.0D;
        FightCinematicSequence.Stage stage = respawn.sequence().stage();
        double progress = respawn.sequence().stageProgress();
        Location center = respawn.center();
        renderRespawnCrystalBeams(world, respawn, center, budget);
        switch (respawn.phase()) {
            case START, PREPARING_TO_SUMMON_PILLARS -> {
                for (int ring = 0; ring < 4; ring++) {
                    orbit(world, center.clone().add(0.0D, 1.0D + ring * 0.35D, 0.0D),
                            6.0D + ring * 4.0D + progress * 8.0D,
                            phase * (ring % 2 == 0 ? 1.0D : -0.75D),
                            ring % 2 == 0 ? Particle.REVERSE_PORTAL : Particle.END_ROD,
                            96,
                            settings.respawnDensity(),
                            CinematicFrameBudget.Priority.TELEGRAPH,
                            budget);
                }
                verticalColumn(world, center, 28.0D, phase, Particle.PORTAL, 96,
                        settings.respawnDensity(), CinematicFrameBudget.Priority.TELEGRAPH, budget);
                ambientSound(world, center, clock, 24L, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.3F, 0.45F);
            }
            case SUMMONING_PILLARS -> {
                for (int ring = 1; ring <= 5; ring++) {
                    groundRing(world, center, ring * (5.0D + progress * 3.0D), phase * 0.4D + ring,
                            ring % 2 == 0 ? Particle.DRAGON_BREATH : Particle.REVERSE_PORTAL,
                            96,
                            settings.respawnDensity(),
                            CinematicFrameBudget.Priority.IMPACT,
                            budget);
                }
                verticalColumn(world, center, 42.0D, phase * 1.4D, Particle.END_ROD, 120,
                        settings.respawnDensity(), CinematicFrameBudget.Priority.IMPACT, budget);
                renderSkyRifts(world, center, phase * 0.8D, budget);
                ambientSound(world, center, clock, 28L, Sound.BLOCK_BEACON_AMBIENT, 1.6F, 0.42F);
            }
            case SUMMONING_DRAGON -> {
                orbit(world, center.clone().add(0.0D, 20.0D, 0.0D),
                        24.0D * Math.max(0.15D, 1.0D - progress) + 3.0D,
                        phase * 2.0D,
                        Particle.DRAGON_BREATH,
                        180,
                        settings.respawnDensity(),
                        CinematicFrameBudget.Priority.IMPACT,
                        budget);
                verticalColumn(world, center, 58.0D, phase * 2.2D, Particle.END_ROD, 160,
                        settings.respawnDensity(), CinematicFrameBudget.Priority.IMPACT, budget);
                beam(world, center.clone().add(0.0D, 2.0D, 0.0D), center.clone().add(0.0D, 42.0D, 0.0D),
                        Particle.DRAGON_BREATH, 120, settings.respawnDensity(),
                        CinematicFrameBudget.Priority.IMPACT, budget);
                if (respawn.sequence().fireOnce("summon-roar")) {
                    world.strikeLightningEffect(center);
                    playLayer(world, center, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0F, 0.55F);
                    playLayer(world, center, Sound.BLOCK_END_PORTAL_SPAWN, 3.2F, 0.5F);
                }
                ambientSound(world, center, clock, 18L, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.2F, 0.5F);
            }
            case END -> {
                if (stage == FightCinematicSequence.Stage.RELEASE || stage == FightCinematicSequence.Stage.IMPACT) {
                    for (int ring = 1; ring <= 5; ring++) {
                        groundRing(world, center, ring * 6.0D + progress * 14.0D, ring,
                                ring % 2 == 0 ? Particle.END_ROD : Particle.DRAGON_BREATH,
                                100,
                                settings.respawnDensity(),
                                CinematicFrameBudget.Priority.IMPACT,
                                budget);
                    }
                    if (respawn.sequence().fireOnce("complete")) {
                        world.strikeLightningEffect(center);
                        playLayer(world, center, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.5F, 0.72F);
                        playLayer(world, center, Sound.BLOCK_BEACON_ACTIVATE, 3.0F, 0.62F);
                    }
                } else {
                    risingMotes(world, center, clock, Particle.SOUL_FIRE_FLAME, 80, budget);
                    orbit(world, center.clone().add(0.0D, 2.0D, 0.0D), 16.0D, phase * 0.4D,
                            Particle.REVERSE_PORTAL, 90, settings.respawnDensity(),
                            CinematicFrameBudget.Priority.AMBIENT, budget);
                }
            }
            case NONE -> {
            }
        }
    }

    private void renderRespawnCrystalBeams(
            World world,
            RespawnSequence respawn,
            Location center,
            CinematicFrameBudget budget
    ) {
        DragonBattle battle = world.getEnderDragonBattle();
        List<Location> crystals = respawn.crystals().isEmpty() && battle != null
                ? battle.getRespawnCrystals().stream().map(EnderCrystal::getLocation).toList()
                : respawn.crystals();
        for (Location crystal : crystals) {
            beam(world, crystal.clone().add(0.0D, 1.0D, 0.0D), center.clone().add(0.0D, 2.0D, 0.0D),
                    Particle.END_ROD, 44, settings.respawnDensity(),
                    CinematicFrameBudget.Priority.TELEGRAPH, budget);
            brokenRing(world, crystal, 1.8D, clock(world) / 6.0D, Particle.REVERSE_PORTAL, 36, budget);
        }
    }

    private void trail(
            World world,
            Location dragonLocation,
            org.bukkit.util.Vector velocity,
            FightPhase phase,
            CinematicFrameBudget budget
    ) {
        if (velocity.lengthSquared() < 0.01D) {
            return;
        }
        Location tail = dragonLocation.clone().subtract(velocity.clone().normalize().multiply(8.0D));
        Particle particle = phase == FightPhase.FINAL ? Particle.DRAGON_BREATH : Particle.REVERSE_PORTAL;
        beam(world, dragonLocation, tail, particle, 20,
                settings.phaseDensity(), CinematicFrameBudget.Priority.AMBIENT, budget);
    }

    private void risingMotes(
            World world,
            Location origin,
            long clock,
            Particle particle,
            int count,
            CinematicFrameBudget budget
    ) {
        int granted = budget.claim(CinematicFrameBudget.Priority.AMBIENT, scaled(count, settings.phaseDensity()));
        for (int index = 0; index < granted; index++) {
            double angle = (clock + index * 17L) * 0.17D;
            double radius = 2.0D + index % 7;
            Location point = origin.clone().add(
                    Math.cos(angle) * radius,
                    0.5D + (clock * 0.08D + index * 0.7D) % 12.0D,
                    Math.sin(angle) * radius
            );
            emit(world, point, particle, index);
        }
    }

    private void orbit(
            World world,
            Location center,
            double radius,
            double phase,
            Particle particle,
            int points,
            double density,
            CinematicFrameBudget.Priority priority,
            CinematicFrameBudget budget
    ) {
        int granted = budget.claim(priority, scaled(points, density));
        for (int index = 0; index < granted; index++) {
            double angle = phase + Math.PI * 2.0D * index / Math.max(1, granted);
            Location point = center.clone().add(
                    Math.cos(angle) * radius,
                    Math.sin(angle * 2.0D) * 0.18D,
                    Math.sin(angle) * radius
            );
            emit(world, point, particle, index);
        }
    }

    private void brokenRing(
            World world,
            Location center,
            double radius,
            double phase,
            Particle particle,
            int points,
            CinematicFrameBudget budget
    ) {
        int granted = budget.claim(CinematicFrameBudget.Priority.TELEGRAPH, scaled(points, settings.phaseDensity()));
        for (int index = 0; index < granted; index++) {
            if ((index / 5) % 2 != 0) {
                continue;
            }
            double angle = phase + Math.PI * 2.0D * index / Math.max(1, granted);
            emit(world, center.clone().add(Math.cos(angle) * radius, 0.15D, Math.sin(angle) * radius), particle, index);
        }
    }

    private void beam(
            World world,
            Location from,
            Location to,
            Particle particle,
            int points,
            double density,
            CinematicFrameBudget.Priority priority,
            CinematicFrameBudget budget
    ) {
        int granted = budget.claim(priority, scaled(points, density));
        for (int index = 0; index < granted; index++) {
            double progress = index / (double) Math.max(1, granted - 1);
            emit(world, ParticleEffects.interpolate(from, to, progress), particle, index);
        }
    }

    private void arc(
            World world,
            Location from,
            Location to,
            Particle particle,
            int points,
            double density,
            CinematicFrameBudget.Priority priority,
            CinematicFrameBudget budget
    ) {
        int granted = budget.claim(priority, scaled(points, density));
        Vector direction = to.toVector().subtract(from.toVector());
        Vector lateral = new Vector(-direction.getZ(), 0.0D, direction.getX());
        if (lateral.lengthSquared() > 0.001D) {
            lateral.normalize();
        }
        for (int index = 0; index < granted; index++) {
            double progress = index / (double) Math.max(1, granted - 1);
            Location point = ParticleEffects.interpolate(from, to, progress);
            point.add(
                    lateral.getX() * Math.sin(progress * Math.PI * 3.0D) * 0.7D,
                    Math.sin(progress * Math.PI) * 4.0D,
                    lateral.getZ() * Math.sin(progress * Math.PI * 3.0D) * 0.7D
            );
            emit(world, point, particle, index);
        }
    }

    private void groundRing(
            World world,
            Location center,
            double radius,
            double phase,
            Particle particle,
            int points,
            double density,
            CinematicFrameBudget.Priority priority,
            CinematicFrameBudget budget
    ) {
        int granted = budget.claim(priority, scaled(points, density));
        for (int index = 0; index < granted; index++) {
            double angle = phase + Math.PI * 2.0D * index / Math.max(1, granted);
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 0.15D;
            emit(world, new Location(world, x, y, z), particle, index);
        }
    }

    private void cone(
            World world,
            Location origin,
            Location target,
            double progress,
            Particle particle,
            int points,
            CinematicFrameBudget budget
    ) {
        Vector forward = target.toVector().subtract(origin.toVector());
        if (forward.lengthSquared() <= 0.001D) {
            return;
        }
        double length = forward.length();
        forward.normalize();
        Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
        if (side.lengthSquared() > 0.001D) {
            side.normalize();
        }
        int granted = budget.claim(
                CinematicFrameBudget.Priority.TELEGRAPH,
                scaled(points, settings.attackDensity())
        );
        for (int index = 0; index < granted; index++) {
            double distanceProgress = (index % 12) / 11.0D;
            double spread = ((index / 12) - 2.0D) * 0.22D * distanceProgress * (0.5D + progress);
            Vector offset = forward.clone().multiply(length * distanceProgress)
                    .add(side.clone().multiply(length * spread));
            emit(world, origin.clone().add(offset), particle, index);
        }
    }

    private void verticalColumn(
            World world,
            Location origin,
            double height,
            double phase,
            Particle particle,
            int points,
            double density,
            CinematicFrameBudget.Priority priority,
            CinematicFrameBudget budget
    ) {
        int granted = budget.claim(priority, scaled(points, density));
        for (int index = 0; index < granted; index++) {
            double progress = index / (double) Math.max(1, granted - 1);
            double angle = phase + progress * Math.PI * 8.0D;
            Location point = origin.clone().add(
                    Math.cos(angle) * (0.5D + progress * 2.0D),
                    progress * height,
                    Math.sin(angle) * (0.5D + progress * 2.0D)
            );
            emit(world, point, particle, index);
        }
    }

    private void emit(World world, Location location, Particle particle, int sampleIndex) {
        for (Player player : world.getPlayers()) {
            double detail = detailFactor(player.getLocation().distanceSquared(location), settings);
            if (detail <= 0.0D || (detail < 1.0D && sampleIndex % Math.max(1, (int) Math.round(1.0D / detail)) != 0)) {
                continue;
            }
            try {
                if (particle.getDataType() == Float.class || particle == Particle.DRAGON_BREATH || particle == Particle.PORTAL) {
                    player.spawnParticle(particle, location, 1, 0.0D, 0.0D, 0.0D, 0.0D, 1.0F, true);
                } else if (particle.getDataType() == Void.class) {
                    player.spawnParticle(particle, location, 1, 0.0D, 0.0D, 0.0D, 0.0D, null, true);
                } else {
                    player.spawnParticle(Particle.END_ROD, location, 1, 0.0D, 0.0D, 0.0D, 0.0D, null, true);
                }
            } catch (IllegalArgumentException ignored) {
                player.spawnParticle(Particle.END_ROD, location, 1, 0.0D, 0.0D, 0.0D, 0.0D, null, true);
            }
        }
    }

    static double detailFactor(double distanceSquared, EndFightSettings.Cinematics settings) {
        if (distanceSquared <= settings.fullDetailRange() * settings.fullDetailRange()) {
            return 1.0D;
        }
        if (distanceSquared <= settings.mediumDetailRange() * settings.mediumDetailRange()) {
            return 0.5D;
        }
        if (distanceSquared <= settings.maximumViewRange() * settings.maximumViewRange()) {
            return 0.25D;
        }
        return 0.0D;
    }

    private void ambientSound(
            World world,
            Location location,
            long clock,
            long baseInterval,
            Sound sound,
            float volume,
            float pitch
    ) {
        long interval = Math.max(settings.tickInterval(), Math.round(baseInterval / settings.ambientFrequencyMultiplier()));
        if (clock % interval < settings.tickInterval()) {
            playLayer(world, location, sound, volume, pitch);
        }
    }

    private void playLayer(World world, Location location, Sound sound, float volume, float pitch) {
        world.playSound(location, sound, (float) (volume * settings.soundVolumeMultiplier()), pitch);
    }

    private Particle attackParticle(DragonAttackType type) {
        return switch (type) {
            case WING_GUST, WING_RING -> Particle.CLOUD;
            case BREATH_STRAFE, SKY_CRASH -> Particle.DRAGON_BREATH;
            case PILLAR_BEAM, ENDER_VOLLEY -> Particle.END_ROD;
            default -> Particle.REVERSE_PORTAL;
        };
    }

    private Sound attackWindupSound(DragonAttackType type) {
        return switch (type) {
            case WING_GUST, WING_RING -> Sound.ENTITY_ENDER_DRAGON_FLAP;
            case BREATH_STRAFE -> Sound.ENTITY_ENDER_DRAGON_SHOOT;
            case SKY_CRASH, DRAGON_DIVE_MARK -> Sound.ENTITY_ENDER_DRAGON_GROWL;
            case PILLAR_BEAM, ENDER_VOLLEY, CRYSTAL_BARRAGE -> Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
            default -> Sound.BLOCK_BEACON_AMBIENT;
        };
    }

    private Sound attackImpactSound(DragonAttackType type) {
        return switch (type) {
            case WING_GUST, WING_RING -> Sound.ENTITY_ENDER_DRAGON_FLAP;
            case SKY_CRASH, DRAGON_DIVE_MARK -> Sound.ENTITY_GENERIC_EXPLODE;
            case BREATH_STRAFE -> Sound.ENTITY_ENDER_DRAGON_SHOOT;
            case VOID_GRASP, VOID_RIFT -> Sound.ENTITY_WARDEN_SONIC_BOOM;
            default -> Sound.BLOCK_BEACON_ACTIVATE;
        };
    }

    private int scaled(int points, double localDensity) {
        return Math.max(1, (int) Math.round(points * localDensity * settings.particleDensityMultiplier()));
    }

    private long clock(World world) {
        return clock(world.getName());
    }

    private long clock(String worldName) {
        return clocks.getOrDefault(worldName, 0L);
    }

    private List<Location> copyLocations(List<Location> locations) {
        if (locations == null) {
            return List.of();
        }
        return locations.stream().filter(java.util.Objects::nonNull).map(Location::clone).toList();
    }

    private record PhaseSequence(FightPhase phase, Location center, FightCinematicSequence sequence) {
    }

    private record AttackSequence(
            DragonAttackType type,
            Location origin,
            List<Location> targets,
            FightCinematicSequence sequence
    ) {
    }

    private record CollapseSequence(Location origin, FightCinematicSequence sequence) {
    }

    private record RespawnSequence(
            DragonBattle.RespawnPhase phase,
            Location center,
            List<Location> crystals,
            FightCinematicSequence sequence
    ) {
    }
}
