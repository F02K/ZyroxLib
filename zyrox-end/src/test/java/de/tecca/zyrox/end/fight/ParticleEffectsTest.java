package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ParticleEffectsTest {

    @Test
    void floatParticlesReceiveFloatDataWhenSpawnedThroughSimpleHelper() {
        AtomicBoolean floatDataSeen = new AtomicBoolean(false);
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("spawnParticle") && args != null) {
                        for (Object arg : args) {
                            if (arg instanceof Float) {
                                floatDataSeen.set(true);
                            }
                        }
                    }
                    return defaultValue(method.getReturnType());
                }
        );

        ParticleEffects.spawnSimple(world, Particle.DRAGON_BREATH, new Location(world, 0.0D, 80.0D, 0.0D), 1, 0.0D, 0.0D, 0.0D, 0.0D);

        assertTrue(floatDataSeen.get());
    }

    @Test
    void runtimeFloatParticlesAreKnownEvenWhenCompileApiDoesNotExposeThem() {
        assertTrue(ParticleEffects.requiresFloatData(Particle.DRAGON_BREATH));
        assertTrue(ParticleEffects.requiresFloatData(Particle.PORTAL));
        assertFalse(ParticleEffects.requiresFloatData(Particle.REVERSE_PORTAL));
    }

    @Test
    void helperFallsBackWhenRuntimeRejectsFloatData() {
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("spawnParticle") && args != null) {
                        for (Object arg : args) {
                            if (arg instanceof Float) {
                                throw new IllegalArgumentException("data (class java.lang.Float) should be class java.lang.Void");
                            }
                        }
                    }
                    return defaultValue(method.getReturnType());
                }
        );

        assertDoesNotThrow(() -> ParticleEffects.spawnSimple(world, Particle.PORTAL, new Location(world, 0.0D, 80.0D, 0.0D), 1, 0.0D, 0.0D, 0.0D, 0.0D));
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
