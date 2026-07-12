package de.tecca.zyrox.end.fight;

import java.util.HashSet;
import java.util.Set;

final class FightCinematicSequence {

    enum Stage {
        WINDUP,
        RELEASE,
        IMPACT,
        AFTERMATH,
        COMPLETE
    }

    private final long windupTicks;
    private final long releaseTicks;
    private final long impactTicks;
    private final long aftermathTicks;
    private final Set<String> firedCues = new HashSet<>();
    private long elapsedTicks;

    FightCinematicSequence(long windupTicks, long releaseTicks, long impactTicks, long aftermathTicks) {
        this.windupTicks = Math.max(1L, windupTicks);
        this.releaseTicks = Math.max(1L, releaseTicks);
        this.impactTicks = Math.max(1L, impactTicks);
        this.aftermathTicks = Math.max(1L, aftermathTicks);
    }

    void advance(long ticks) {
        elapsedTicks += Math.max(0L, ticks);
    }

    Stage stage() {
        if (elapsedTicks < windupTicks) {
            return Stage.WINDUP;
        }
        if (elapsedTicks < windupTicks + releaseTicks) {
            return Stage.RELEASE;
        }
        if (elapsedTicks < windupTicks + releaseTicks + impactTicks) {
            return Stage.IMPACT;
        }
        if (elapsedTicks < totalTicks()) {
            return Stage.AFTERMATH;
        }
        return Stage.COMPLETE;
    }

    double stageProgress() {
        long start;
        long duration;
        switch (stage()) {
            case WINDUP -> {
                start = 0L;
                duration = windupTicks;
            }
            case RELEASE -> {
                start = windupTicks;
                duration = releaseTicks;
            }
            case IMPACT -> {
                start = windupTicks + releaseTicks;
                duration = impactTicks;
            }
            case AFTERMATH -> {
                start = windupTicks + releaseTicks + impactTicks;
                duration = aftermathTicks;
            }
            case COMPLETE -> {
                return 1.0D;
            }
            default -> throw new IllegalStateException();
        }
        return Math.max(0.0D, Math.min(1.0D, (elapsedTicks - start) / (double) duration));
    }

    boolean fireOnce(String cue) {
        return firedCues.add(cue);
    }

    boolean complete() {
        return stage() == Stage.COMPLETE;
    }

    long elapsedTicks() {
        return elapsedTicks;
    }

    long totalTicks() {
        return windupTicks + releaseTicks + impactTicks + aftermathTicks;
    }
}
