package de.tecca.zyrox.end.fight;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FightCinematicDirectorTest {

    @Test
    void detailFactorUsesConfiguredDistanceBands() {
        EndFightSettings.Cinematics settings = EndFightSettings.from(new YamlConfiguration()).cinematics();

        assertEquals(1.0D, FightCinematicDirector.detailFactor(32.0D * 32.0D, settings), 0.0001D);
        assertEquals(0.5D, FightCinematicDirector.detailFactor(100.0D * 100.0D, settings), 0.0001D);
        assertEquals(0.25D, FightCinematicDirector.detailFactor(160.0D * 160.0D, settings), 0.0001D);
        assertEquals(0.0D, FightCinematicDirector.detailFactor(220.0D * 220.0D, settings), 0.0001D);
    }
}
