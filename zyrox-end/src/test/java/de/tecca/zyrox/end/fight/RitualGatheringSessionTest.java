package de.tecca.zyrox.end.fight;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RitualGatheringSessionTest {

    @Test
    void requiresContinuousHoldAndResetsBelowQuorum() {
        RitualGatheringSession gathering = new RitualGatheringSession(new RitualGatheringCircle(0.0D, 0.0D, 42.0D));

        assertFalse(gathering.updateGathering(3, 4, 0.75D, 100L, 200L));
        assertEquals(100L, gathering.heldTicks());
        assertFalse(gathering.updateGathering(2, 4, 0.75D, 20L, 200L));
        assertEquals(0L, gathering.heldTicks());
        assertFalse(gathering.updateGathering(3, 4, 0.75D, 180L, 200L));
        assertTrue(gathering.updateGathering(3, 4, 0.75D, 20L, 200L));
        assertEquals(200L, gathering.heldTicks());
    }

    @Test
    void freezesAStableParticipantSnapshot() {
        RitualGatheringSession gathering = new RitualGatheringSession(new RitualGatheringCircle(0.0D, 0.0D, 42.0D));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        gathering.freeze(List.of(first, second, first));

        assertEquals(2, gathering.frozenParticipants().size());
        assertTrue(gathering.frozenParticipants().contains(first));
        assertTrue(gathering.frozenParticipants().contains(second));
    }
}
