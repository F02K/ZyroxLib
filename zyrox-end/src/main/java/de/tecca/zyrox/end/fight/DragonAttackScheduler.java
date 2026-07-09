package de.tecca.zyrox.end.fight;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DragonAttackScheduler {

    public Optional<DragonAttackType> nextAttack(EndFightSession session, EndFightConfig config, int activePlayerCount) {
        return nextAttack(session, config, null, activePlayerCount);
    }

    public Optional<DragonAttackType> nextAttack(
            EndFightSession session,
            EndFightConfig config,
            EndFightSettings settings,
            int activePlayerCount
    ) {
        if (activePlayerCount <= 0 || session.tick() < session.nextDragonAttackTick()) {
            return Optional.empty();
        }

        Optional<DragonAttackType> queued = config.comboEnabled()
                ? session.consumeQueuedDragonAttack()
                : Optional.empty();
        if (queued.isPresent()) {
            DragonAttackType type = queued.get();
            session.markDragonAttack(type, session.tick() + cooldown(session, config, settings));
            return Optional.of(type);
        }

        List<DragonAttackType> candidates = new ArrayList<>();
        addOpeningAttacks(candidates, session, config);
        if (session.phase().ordinal() >= FightPhase.TOTEMS.ordinal()) {
            addTotemPhaseAttacks(candidates, session, config);
        }
        if (session.phase().ordinal() >= FightPhase.ESCALATION.ordinal()) {
            addEscalationAttacks(candidates, session);
        }
        if (session.finalPhaseActive()) {
            addFinalAttacks(candidates, session);
        }

        if (session.activeNodeCount(RitualNodeType.PILLAR_BEAM) > 0 || session.finalAnchorCount() > 0) {
            add(candidates, DragonAttackType.PILLAR_BEAM, session.finalPhaseActive() ? 3 : 2);
        }
        if (session.activeNodeCount(RitualNodeType.VOID_RIFT) > 0) {
            add(candidates, DragonAttackType.VOID_RIFT, session.phase().ordinal() >= FightPhase.ESCALATION.ordinal() ? 2 : 1);
        }
        if (session.activeNodeCount(RitualNodeType.ENDER_VOLLEY) > 0) {
            add(candidates, DragonAttackType.ENDER_VOLLEY, session.finalPhaseActive() ? 3 : 2);
        }
        if (session.activeNodeCount(RitualNodeType.WING_GUST) > 0) {
            add(candidates, DragonAttackType.WING_GUST, 2);
        }
        if (session.activeNodeCount(RitualNodeType.GRAVITY_ANCHOR) > 0 && session.phase().ordinal() >= FightPhase.TOTEMS.ordinal()) {
            add(candidates, DragonAttackType.WING_GUST, 1);
        }
        if (session.lastDragonAttackType() != null && candidates.size() > 1) {
            candidates.removeIf(type -> type == session.lastDragonAttackType());
            if (candidates.isEmpty()) {
                candidates.add(session.lastDragonAttackType());
            }
        }

        int index = (int) ((session.tick() / 20L) % candidates.size());
        DragonAttackType type = candidates.get(index);
        if (type == DragonAttackType.VOID_GRASP && session.finalPhaseActive() && config.comboEnabled()) {
            session.queueDragonAttack(DragonAttackType.SKY_CRASH);
        }
        session.markDragonAttack(type, session.tick() + cooldown(session, config, settings));
        return Optional.of(type);
    }

    public long cooldown(EndFightSession session, EndFightConfig config) {
        return cooldown(session, config, null);
    }

    public long cooldown(EndFightSession session, EndFightConfig config, EndFightSettings settings) {
        long base = session.finalPhaseActive() ? config.attackFinalCooldownTicks() : config.attackBaseCooldownTicks();
        long reduced = base - session.activeNodeCount(RitualNodeType.STORM_SPIRE) * config.attackStormReductionTicks();
        long cooldown = Math.max(config.attackMinimumCooldownTicks(), reduced);
        return settings == null ? cooldown : settings.cooldown(session.fightProfile(), cooldown);
    }

    private void add(List<DragonAttackType> candidates, DragonAttackType type, int weight) {
        for (int i = 0; i < Math.max(1, weight); i++) {
            candidates.add(type);
        }
    }

    private void addOpeningAttacks(List<DragonAttackType> candidates, EndFightSession session, EndFightConfig config) {
        add(candidates, DragonAttackType.DRAGON_DIVE_MARK, session.finalPhaseActive() ? 2 : 1);
        add(candidates, DragonAttackType.BREATH_STRAFE, session.phase() == FightPhase.OPENING ? 2 : 1);
        add(candidates, DragonAttackType.WING_RING, stackedPlayerPressure(session, config) ? 2 : 1);
    }

    private void addTotemPhaseAttacks(List<DragonAttackType> candidates, EndFightSession session, EndFightConfig config) {
        add(candidates, DragonAttackType.VOID_GRASP, 1 + session.activeNodeCount(RitualNodeType.GRAVITY_ANCHOR));
        if (hasPillarCamper(session, config)) {
            add(candidates, DragonAttackType.SKY_CRASH, config.antiCampExtraWeight());
        }
        if (session.activeNodeCount(RitualNodeType.CRYSTAL_THORNS) > 0) {
            add(candidates, DragonAttackType.WING_RING, 1);
        }
    }

    private void addEscalationAttacks(List<DragonAttackType> candidates, EndFightSession session) {
        add(candidates, DragonAttackType.SKY_CRASH, 2);
        add(candidates, DragonAttackType.CRYSTAL_BARRAGE, 2 + session.activeNodeCount(RitualNodeType.ENDER_VOLLEY));
        add(candidates, DragonAttackType.BREATH_STRAFE, 1);
    }

    private void addFinalAttacks(List<DragonAttackType> candidates, EndFightSession session) {
        add(candidates, DragonAttackType.PERCH_RITUAL, 2);
        add(candidates, DragonAttackType.VOID_GRASP, 2 + session.finalAnchorCount());
        add(candidates, DragonAttackType.SKY_CRASH, 2);
    }

    private boolean hasPillarCamper(EndFightSession session, EndFightConfig config) {
        return session.participants().stream()
                .anyMatch(participant -> participant.highGroundTicks() >= config.pillarCampingThresholdTicks());
    }

    private boolean stackedPlayerPressure(EndFightSession session, EndFightConfig config) {
        return session.activeParticipantCount(config) >= 2 && config.antiStackPlayerDistance() > 0.0D;
    }
}
