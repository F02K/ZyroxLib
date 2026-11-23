package de.tecca.zyrox.end.listener;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EndDragonEggMiningServiceTest {

    @Test
    void creativeBreaksInstantly() {
        assertEquals(
                EndDragonEggMiningService.MiningMode.INSTANT,
                EndDragonEggMiningService.miningMode(GameMode.CREATIVE)
        );
    }

    @Test
    void survivalAndAdventureUseMiningSessions() {
        assertEquals(
                EndDragonEggMiningService.MiningMode.SESSION,
                EndDragonEggMiningService.miningMode(GameMode.SURVIVAL)
        );
        assertEquals(
                EndDragonEggMiningService.MiningMode.SESSION,
                EndDragonEggMiningService.miningMode(GameMode.ADVENTURE)
        );
    }

    @Test
    void spectatorAndUnknownModesAreIgnored() {
        assertEquals(
                EndDragonEggMiningService.MiningMode.IGNORED,
                EndDragonEggMiningService.miningMode(GameMode.SPECTATOR)
        );
        assertEquals(
                EndDragonEggMiningService.MiningMode.IGNORED,
                EndDragonEggMiningService.miningMode(null)
        );
    }
}
