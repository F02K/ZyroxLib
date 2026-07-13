package de.tecca.zyrox.end.listener;

import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EndDragonEggListenerTest {

    @Test
    void untaggedEggsCanOnlyAutoClaimInTheEnd() {
        assertTrue(EndDragonEggListener.canAutoClaimUntaggedEgg(World.Environment.THE_END));
        assertFalse(EndDragonEggListener.canAutoClaimUntaggedEgg(World.Environment.NORMAL));
        assertFalse(EndDragonEggListener.canAutoClaimUntaggedEgg(World.Environment.NETHER));
        assertFalse(EndDragonEggListener.canAutoClaimUntaggedEgg(World.Environment.CUSTOM));
        assertFalse(EndDragonEggListener.canAutoClaimUntaggedEgg(null));
    }

    @Test
    void recoveryRegistryMatchesNearbyEggDropsBeforeExpiry() {
        EndDragonEggListener.RecoveryRegistry registry = new EndDragonEggListener.RecoveryRegistry();
        UUID worldId = UUID.randomUUID();
        UUID fallingId = UUID.randomUUID();
        EndDragonEggListener.PendingRecovery recovery = registry.add(
                new EndDragonEggListener.RecoveryKey(worldId, "world_the_end", 10, 64, -5),
                EndDragonEggListener.RecoveryReason.FALLING_BREAK,
                fallingId,
                100L,
                3L
        );

        Optional<EndDragonEggListener.PendingRecovery> match = registry.findMatching(
                worldId,
                10.5D,
                64.5D,
                -4.5D,
                102L
        );

        assertEquals(recovery, match.orElseThrow());
        assertEquals(Optional.of(fallingId), recovery.optionalFallingUniqueId());
        assertTrue(recovery.expiresToLost());
    }

    @Test
    void recoveryRegistryRejectsFarWrongWorldAndExpiredDrops() {
        EndDragonEggListener.RecoveryRegistry registry = new EndDragonEggListener.RecoveryRegistry();
        UUID worldId = UUID.randomUUID();
        registry.add(
                new EndDragonEggListener.RecoveryKey(worldId, "world_the_end", 0, 64, 0),
                EndDragonEggListener.RecoveryReason.EXPLOSION,
                null,
                200L,
                3L
        );

        assertTrue(registry.findMatching(UUID.randomUUID(), 0.5D, 64.5D, 0.5D, 201L).isEmpty());
        assertTrue(registry.findMatching(worldId, 12.0D, 64.5D, 0.5D, 201L).isEmpty());
        assertTrue(registry.findMatching(worldId, 0.5D, 64.5D, 0.5D, 204L).isEmpty());
    }

    @Test
    void recoveryRegistryStopsMatchingAfterRecoveryIsRemoved() {
        EndDragonEggListener.RecoveryRegistry registry = new EndDragonEggListener.RecoveryRegistry();
        UUID worldId = UUID.randomUUID();
        EndDragonEggListener.PendingRecovery recovery = registry.add(
                new EndDragonEggListener.RecoveryKey(worldId, "world_the_end", 0, 64, 0),
                EndDragonEggListener.RecoveryReason.BLOCK_BREAK_BY_BLOCK,
                null,
                300L,
                3L
        );

        assertTrue(registry.remove(recovery));
        assertTrue(registry.findMatching(worldId, 0.5D, 64.5D, 0.5D, 301L).isEmpty());
    }

    @Test
    void fallingSourceRecoveryDoesNotExpireToLostByItself() {
        EndDragonEggListener.RecoveryRegistry registry = new EndDragonEggListener.RecoveryRegistry();
        EndDragonEggListener.PendingRecovery recovery = registry.add(
                new EndDragonEggListener.RecoveryKey(UUID.randomUUID(), "world_the_end", 0, 64, 0),
                EndDragonEggListener.RecoveryReason.FALLING_SOURCE,
                UUID.randomUUID(),
                400L,
                3L
        );

        assertFalse(recovery.expiresToLost());
    }

    @Test
    void activeDestructionCausesAreFinalLossCandidates() {
        assertTrue(EndDragonEggListener.activeDestructionCause(EntityDamageEvent.DamageCause.LAVA));
        assertTrue(EndDragonEggListener.activeDestructionCause(EntityDamageEvent.DamageCause.FIRE));
        assertTrue(EndDragonEggListener.activeDestructionCause(EntityDamageEvent.DamageCause.FIRE_TICK));
        assertTrue(EndDragonEggListener.activeDestructionCause(EntityDamageEvent.DamageCause.HOT_FLOOR));
        assertTrue(EndDragonEggListener.activeDestructionCause(EntityDamageEvent.DamageCause.CONTACT));
        assertTrue(EndDragonEggListener.activeDestructionCause(EntityDamageEvent.DamageCause.VOID));
        assertFalse(EndDragonEggListener.activeDestructionCause(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION));
        assertFalse(EndDragonEggListener.activeDestructionCause(EntityDamageEvent.DamageCause.ENTITY_ATTACK));
        assertFalse(EndDragonEggListener.activeDestructionCause(null));
    }
}
