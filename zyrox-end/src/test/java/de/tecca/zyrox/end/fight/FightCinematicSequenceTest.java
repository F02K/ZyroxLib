package de.tecca.zyrox.end.fight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FightCinematicSequenceTest {

    @Test
    void advancesThroughAllCinematicStages() {
        FightCinematicSequence sequence = new FightCinematicSequence(10L, 4L, 6L, 8L);

        assertEquals(FightCinematicSequence.Stage.WINDUP, sequence.stage());
        sequence.advance(10L);
        assertEquals(FightCinematicSequence.Stage.RELEASE, sequence.stage());
        sequence.advance(4L);
        assertEquals(FightCinematicSequence.Stage.IMPACT, sequence.stage());
        sequence.advance(6L);
        assertEquals(FightCinematicSequence.Stage.AFTERMATH, sequence.stage());
        sequence.advance(8L);
        assertEquals(FightCinematicSequence.Stage.COMPLETE, sequence.stage());
        assertTrue(sequence.complete());
    }

    @Test
    void soundAndImpactCuesFireOnlyOnce() {
        FightCinematicSequence sequence = new FightCinematicSequence(5L, 2L, 2L, 2L);

        assertTrue(sequence.fireOnce("release"));
        assertFalse(sequence.fireOnce("release"));
        assertTrue(sequence.fireOnce("impact"));
    }
}
