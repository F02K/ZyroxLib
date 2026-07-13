package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PillarLayoutTest {

    private final PillarLayout layout = new PillarLayout();

    @Test
    void bedrockAtFixedPillarPositionReturnsSpawnPositionAboveBedrock() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(-42, -1, Material.BEDROCK, 81, 81);

        Location location = layout.capSpawnAt(world.proxy(), -42, -1).orElseThrow();

        assertEquals(-41.5D, location.getX(), 0.0001D);
        assertEquals(82.0D, location.getY(), 0.0001D);
        assertEquals(-0.5D, location.getZ(), 0.0001D);
    }

    @Test
    void fixedPillarPositionWithoutBedrockIsIgnored() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(-42, -1, Material.OBSIDIAN, 70, 80);

        assertTrue(layout.capSpawnAt(world.proxy(), -42, -1).isEmpty());
    }

    @Test
    void stackedBedrockUsesTopmostBedrockAsSpawnBase() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(-42, -1, Material.BEDROCK, 81, 82);

        Location location = layout.capSpawnAt(world.proxy(), -42, -1).orElseThrow();

        assertEquals(-41.5D, location.getX(), 0.0001D);
        assertEquals(83.0D, location.getY(), 0.0001D);
        assertEquals(-0.5D, location.getZ(), 0.0001D);
    }

    @Test
    void capSpawnNearNodeUsesBedrockAtNodeColumn() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(-42, -1, Material.BEDROCK, 81, 81);
        PillarRitualNode node = new PillarRitualNode(
                UUID.randomUUID(),
                new Location(world.proxy(), -41.5D, 82.0D, -0.5D),
                null
        );

        Location location = layout.capSpawnNearNode(node, world.proxy()).orElseThrow();

        assertEquals(-41.5D, location.getX(), 0.0001D);
        assertEquals(82.0D, location.getY(), 0.0001D);
        assertEquals(-0.5D, location.getZ(), 0.0001D);
    }

    @Test
    void capSpawnNearNodeUsesTopmostNearbyBedrock() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(-42, -1, Material.BEDROCK, 81, 82);
        PillarRitualNode node = new PillarRitualNode(
                UUID.randomUUID(),
                new Location(world.proxy(), -41.5D, 82.0D, -0.5D),
                null
        );

        Location location = layout.capSpawnNearNode(node, world.proxy()).orElseThrow();

        assertEquals(-41.5D, location.getX(), 0.0001D);
        assertEquals(83.0D, location.getY(), 0.0001D);
        assertEquals(-0.5D, location.getZ(), 0.0001D);
    }

    private static final class FakeWorld {

        private final Map<String, Material> blocks = new HashMap<>();
        private World proxy;

        World proxy() {
            if (proxy == null) {
                proxy = (World) Proxy.newProxyInstance(
                        World.class.getClassLoader(),
                        new Class<?>[]{World.class},
                        (ignored, method, args) -> switch (method.getName()) {
                            case "getName" -> "world_the_end";
                            case "getMinHeight" -> 0;
                            case "getMaxHeight" -> 256;
                            case "getBlockAt" -> block((int) args[0], (int) args[1], (int) args[2]);
                            default -> defaultValue(method.getReturnType());
                        }
                );
            }
            return proxy;
        }

        void placeColumn(int x, int z, Material material, int minY, int maxY) {
            for (int y = minY; y <= maxY; y++) {
                blocks.put(key(x, y, z), material);
            }
        }

        private Block block(int x, int y, int z) {
            Material material = blocks.getOrDefault(key(x, y, z), Material.AIR);
            return (Block) Proxy.newProxyInstance(
                    Block.class.getClassLoader(),
                    new Class<?>[]{Block.class},
                    (ignored, method, args) -> switch (method.getName()) {
                        case "getType" -> material;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }
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
