package de.tecca.zyrox.end.fight;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FinalAnchorAttackState {

    enum Stage {
        IDLE,
        CHARGING,
        EXECUTING,
        COOLDOWN
    }

    private FinalAnchorAttackType nextAttack = FinalAnchorAttackType.GROUND_WAVE;
    private FinalAnchorAttackType activeAttack;
    private Stage stage = Stage.IDLE;
    private long stageTicks;
    private long totalTicks;
    private double beamStartAngle;
    private final Map<UUID, Location> targetLocations = new HashMap<>();
    private final Set<UUID> targetCircleHits = new HashSet<>();
    private final List<Set<UUID>> waveHits = new ArrayList<>();
    private final Map<UUID, Long> lastBeamHitTicks = new HashMap<>();

    FinalAnchorAttackType begin(int waveCount, double beamStartAngle) {
        activeAttack = nextAttack;
        nextAttack = nextAttack.next();
        stage = Stage.CHARGING;
        stageTicks = 0L;
        this.beamStartAngle = beamStartAngle;
        targetLocations.clear();
        targetCircleHits.clear();
        waveHits.clear();
        for (int index = 0; index < Math.max(1, waveCount); index++) {
            waveHits.add(new HashSet<>());
        }
        lastBeamHitTicks.clear();
        return activeAttack;
    }

    void advance(long ticks) {
        long safeTicks = Math.max(0L, ticks);
        stageTicks += safeTicks;
        totalTicks += safeTicks;
    }

    void startExecution() {
        stage = Stage.EXECUTING;
        stageTicks = 0L;
    }

    void startCooldown() {
        stage = Stage.COOLDOWN;
        stageTicks = 0L;
    }

    void finishCooldown() {
        stage = Stage.IDLE;
        stageTicks = 0L;
        activeAttack = null;
        targetLocations.clear();
        targetCircleHits.clear();
        waveHits.clear();
        lastBeamHitTicks.clear();
    }

    void reset() {
        stage = Stage.IDLE;
        stageTicks = 0L;
        activeAttack = null;
        targetLocations.clear();
        targetCircleHits.clear();
        waveHits.clear();
        lastBeamHitTicks.clear();
    }

    Stage stage() {
        return stage;
    }

    long stageTicks() {
        return stageTicks;
    }

    long totalTicks() {
        return totalTicks;
    }

    FinalAnchorAttackType activeAttack() {
        return activeAttack;
    }

    FinalAnchorAttackType nextAttack() {
        return nextAttack;
    }

    double beamStartAngle() {
        return beamStartAngle;
    }

    Map<UUID, Location> targetLocations() {
        return targetLocations;
    }

    boolean markTargetCircleHit(UUID playerUniqueId) {
        return targetCircleHits.add(playerUniqueId);
    }

    boolean markWaveHit(int waveIndex, UUID playerUniqueId) {
        return waveIndex >= 0
                && waveIndex < waveHits.size()
                && waveHits.get(waveIndex).add(playerUniqueId);
    }

    boolean markBeamHit(UUID playerUniqueId, long cooldownTicks) {
        long lastHit = lastBeamHitTicks.getOrDefault(playerUniqueId, Long.MIN_VALUE / 2L);
        if (totalTicks - lastHit < Math.max(1L, cooldownTicks)) {
            return false;
        }
        lastBeamHitTicks.put(playerUniqueId, totalTicks);
        return true;
    }
}
