package de.tecca.zyrox.end.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FirstDragonEggStateTest {

    @Test
    void claimPhasesTransitionOnlyForMatchingId() {
        FirstDragonEggState state = new FirstDragonEggState();
        DragonEggClaim claim = new DragonEggClaim(UUID.randomUUID(), UUID.randomUUID(), "Owner", 100L);

        assertEquals(DragonEggClaimPhase.UNCLAIMED, state.eggClaimPhase());
        state.reserveEggClaim(claim);
        assertEquals(DragonEggClaimPhase.PENDING, state.eggClaimPhase());
        assertFalse(state.commitPendingEggClaim(UUID.randomUUID()));
        assertTrue(state.commitPendingEggClaim(claim.eggId()));
        assertEquals(DragonEggClaimPhase.CLAIMED, state.eggClaimPhase());
        assertEquals(claim, state.claimedEgg().orElseThrow());
    }

    @Test
    void trackingDescriptionReportsPlacedCoordinates() {
        DragonEggTrackingState tracking = new DragonEggTrackingState(
                DragonEggTrackingForm.PLACED,
                UUID.randomUUID(),
                "world",
                12,
                70,
                -4,
                null,
                200L
        );

        assertEquals("world 12,70,-4", tracking.description());
    }
}
