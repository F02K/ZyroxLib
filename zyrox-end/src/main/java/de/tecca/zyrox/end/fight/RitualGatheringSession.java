package de.tecca.zyrox.end.fight;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

final class RitualGatheringSession {

    private final RitualGatheringCircle circle;
    private long heldTicks;
    private Set<UUID> frozenParticipants = Set.of();

    RitualGatheringSession(RitualGatheringCircle circle) {
        this.circle = circle;
    }

    RitualGatheringCircle circle() {
        return circle;
    }

    long heldTicks() {
        return heldTicks;
    }

    boolean updateGathering(int insidePlayers, int eligiblePlayers, double requiredRatio, long elapsedTicks, long requiredHoldTicks) {
        int required = RitualGatheringCircle.requiredPlayers(eligiblePlayers, requiredRatio);
        if (eligiblePlayers <= 0 || insidePlayers < required) {
            heldTicks = 0L;
            return false;
        }
        heldTicks = Math.min(requiredHoldTicks, heldTicks + Math.max(0L, elapsedTicks));
        return heldTicks >= requiredHoldTicks;
    }

    void freeze(Collection<UUID> participants) {
        frozenParticipants = Set.copyOf(new LinkedHashSet<>(participants));
    }

    Set<UUID> frozenParticipants() {
        return frozenParticipants;
    }
}
