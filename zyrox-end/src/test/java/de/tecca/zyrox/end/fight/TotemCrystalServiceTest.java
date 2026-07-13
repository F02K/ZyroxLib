package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TotemCrystalServiceTest {

    private final TotemCrystalService service = new TotemCrystalService(
            null,
            null,
            () -> EndFightConfig.from(new YamlConfiguration()),
            (ignored, fallback) -> fallback
    );

    @Test
    void firstRitualReplacementRemovesCrystalsNearValidCapAndDeactivatesNode() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(-42, -1, Material.BEDROCK, 81, 81);
        TestCrystal near = world.addCrystal(-41.5D, 82.0D, -0.5D);
        TestCrystal far = world.addCrystal(-41.5D, 90.0D, -0.5D);
        PillarRitualNode node = new PillarRitualNode(
                UUID.randomUUID(),
                new Location(world.proxy(), -41.5D, 82.0D, -0.5D),
                near.uniqueId()
        );

        Location location = service.prepareFirstRitualReplacement(node, world.proxy()).orElseThrow();

        assertEquals(-41.5D, location.getX(), 0.0001D);
        assertEquals(82.0D, location.getY(), 0.0001D);
        assertEquals(-0.5D, location.getZ(), 0.0001D);
        assertTrue(near.removed());
        assertFalse(far.removed());
        assertFalse(node.active());
    }

    @Test
    void firstRitualReplacementRejectsInvalidCapWithoutRemovingCrystal() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(-42, -1, Material.OBSIDIAN, 70, 80);
        TestCrystal crystal = world.addCrystal(-41.5D, 82.0D, -0.5D);
        PillarRitualNode node = new PillarRitualNode(
                UUID.randomUUID(),
                new Location(world.proxy(), -41.5D, 82.0D, -0.5D),
                crystal.uniqueId()
        );

        assertTrue(service.prepareFirstRitualReplacement(node, world.proxy()).isEmpty());
        assertFalse(crystal.removed());
        assertTrue(node.active());
    }

    @Test
    void portalAnchorLocationUsesCenterBedrockColumn() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(0, 0, Material.BEDROCK, 65, 70);

        Location location = service.portalAnchorLocation(world.proxy());

        assertEquals(0.5D, location.getX(), 0.0001D);
        assertEquals(71.0D, location.getY(), 0.0001D);
        assertEquals(0.5D, location.getZ(), 0.0001D);
    }

    private static final class FakeWorld {

        private final Map<String, Material> blocks = new HashMap<>();
        private final List<EnderCrystal> crystals = new ArrayList<>();
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
                            case "getEntitiesByClass" -> crystals;
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

        TestCrystal addCrystal(double x, double y, double z) {
            TestCrystal crystal = new TestCrystal(UUID.randomUUID());
            EnderCrystal proxyCrystal = (EnderCrystal) Proxy.newProxyInstance(
                    EnderCrystal.class.getClassLoader(),
                    new Class<?>[]{EnderCrystal.class},
                    (ignored, method, args) -> switch (method.getName()) {
                        case "getLocation" -> new Location(proxy(), x, y, z);
                        case "getUniqueId" -> crystal.uniqueId();
                        case "remove" -> {
                            crystal.remove();
                            yield null;
                        }
                        default -> defaultValue(method.getReturnType());
                    }
            );
            crystals.add(proxyCrystal);
            return crystal;
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

    private static final class TestCrystal {

        private final UUID uniqueId;
        private boolean removed;

        private TestCrystal(UUID uniqueId) {
            this.uniqueId = uniqueId;
        }

        UUID uniqueId() {
            return uniqueId;
        }

        boolean removed() {
            return removed;
        }

        void remove() {
            removed = true;
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
