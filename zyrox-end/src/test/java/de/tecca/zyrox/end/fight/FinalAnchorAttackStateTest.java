package de.tecca.zyrox.end.fight;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FinalAnchorAttackStateTest {

    @Test
    void attacksStrictlyAlternateStartingWithGroundWave() {
        FinalAnchorAttackState state = new FinalAnchorAttackState();

        assertEquals(FinalAnchorAttackType.GROUND_WAVE, state.begin(3, 0.0D));
        state.startExecution();
        state.startCooldown();
        state.finishCooldown();
        assertEquals(FinalAnchorAttackType.ROTATING_BEAM, state.begin(3, 0.0D));
        state.startExecution();
        state.startCooldown();
        state.finishCooldown();
        assertEquals(FinalAnchorAttackType.GROUND_WAVE, state.begin(3, 0.0D));
    }

    @Test
    void wavesHitEachPlayerOnlyOnceAndBeamHonorsCooldown() {
        FinalAnchorAttackState state = new FinalAnchorAttackState();
        UUID player = UUID.randomUUID();
        state.begin(3, 0.0D);

        assertTrue(state.markWaveHit(0, player));
        assertFalse(state.markWaveHit(0, player));
        assertTrue(state.markWaveHit(1, player));
        assertTrue(state.markBeamHit(player, 10L));
        assertFalse(state.markBeamHit(player, 10L));
        state.advance(10L);
        assertTrue(state.markBeamHit(player, 10L));
    }
}
