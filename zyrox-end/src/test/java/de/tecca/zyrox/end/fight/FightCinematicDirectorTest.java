package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FightCinematicDirectorTest {

    @Test
    void detailFactorUsesConfiguredDistanceBands() {
        EndFightSettings.Cinematics settings = EndFightSettings.from(new YamlConfiguration()).cinematics();

        assertEquals(1.0D, FightCinematicDirector.detailFactor(32.0D * 32.0D, settings), 0.0001D);
        assertEquals(0.5D, FightCinematicDirector.detailFactor(100.0D * 100.0D, settings), 0.0001D);
        assertEquals(0.25D, FightCinematicDirector.detailFactor(160.0D * 160.0D, settings), 0.0001D);
        assertEquals(0.0D, FightCinematicDirector.detailFactor(220.0D * 220.0D, settings), 0.0001D);
    }

    @Test
    void activeRespawnPhasesStartCinematicsAndEndCleansUpAfterAftermath() {
        // Sound is registry-backed on 26.x; without a running server there is no RegistryAccess.
        Assumptions.assumeTrue(registryAvailable(), "Bukkit registries need a running server");
        FightCinematicDirector director = new FightCinematicDirector(EndFightSettings.from(new YamlConfiguration()).cinematics());
        World world = world("world_the_end");
        Location center = new Location(world, 0.5D, 65.0D, 0.5D);

        assertTrue(FightCinematicDirector.activeRespawnPhase(DragonBattle.RespawnPhase.START));
        assertTrue(FightCinematicDirector.activeRespawnPhase(DragonBattle.RespawnPhase.SUMMONING_DRAGON));
        assertFalse(FightCinematicDirector.activeRespawnPhase(DragonBattle.RespawnPhase.END));
        assertFalse(FightCinematicDirector.activeRespawnPhase(DragonBattle.RespawnPhase.NONE));

        director.beginRespawnPhase(world, DragonBattle.RespawnPhase.START, center, List.of());
        assertTrue(director.hasRespawnSequence("world_the_end"));

        director.beginRespawnPhase(world, DragonBattle.RespawnPhase.END, center, List.of());
        assertTrue(director.hasRespawnSequence("world_the_end"));
        for (int tick = 0; tick < 60; tick++) {
            director.advanceClock(world);
            director.tickRespawn(world, center);
        }
        assertFalse(director.hasRespawnSequence("world_the_end"));

        director.beginRespawnPhase(world, DragonBattle.RespawnPhase.NONE, center, List.of());
        assertFalse(director.hasRespawnSequence("world_the_end"));
    }

    private static boolean registryAvailable() {
        try {
            Class.forName("org.bukkit.Sound");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private World world(String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getPlayers" -> List.of();
                    case "getEnderDragonBattle" -> null;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0F;
        }
        if (returnType == double.class) {
            return 0.0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
