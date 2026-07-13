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

final class PillarScannerTest {

    private static final List<int[]> OUTER_PILLARS = List.of(
            new int[]{-42, -1},
            new int[]{-34, -25},
            new int[]{-34, 24},
            new int[]{-13, -40},
            new int[]{-13, 39},
            new int[]{12, -40},
            new int[]{12, 39},
            new int[]{33, -25},
            new int[]{33, 24},
            new int[]{42, 0}
    );

    private final PillarScanner scanner = new PillarScanner();
    private final EndFightConfig config = EndFightConfig.from(new YamlConfiguration());

    @Test
    void liveCrystalsAndStructuralScanMergeIntoTenPillars() {
        FakeWorld world = new FakeWorld();
        placeOuterPillars(world);
        for (int index = 0; index < 7; index++) {
            int[] pillar = OUTER_PILLARS.get(index);
            world.addCrystal(pillar[0] + 0.5D, 82.0D, pillar[1] + 0.5D);
        }

        List<PillarRitualNode> nodes = scanner.scan(world.proxy(), config);

        assertEquals(10, nodes.size());
        assertEquals(7, nodes.stream().filter(PillarRitualNode::active).count());
    }

    @Test
    void structuralScanFindsTenPillarsWithoutLiveCrystals() {
        FakeWorld world = new FakeWorld();
        placeOuterPillars(world);

        List<PillarRitualNode> nodes = scanner.scan(world.proxy(), config);

        assertEquals(10, nodes.size());
        assertEquals(0, nodes.stream().filter(PillarRitualNode::active).count());
    }

    @Test
    void centerBedrockPortalIsNotCountedAsOuterPillar() {
        FakeWorld world = new FakeWorld();
        placeOuterPillars(world);
        world.placeColumn(0, 0, Material.BEDROCK, 70, 80);

        List<PillarRitualNode> nodes = scanner.scan(world.proxy(), config);

        assertEquals(10, nodes.size());
    }

    @Test
    void bedrockAtFixedPillarPositionReturnsSpawnPositionAboveBedrock() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(-42, -1, Material.BEDROCK, 81, 81);

        List<PillarRitualNode> nodes = scanner.scan(world.proxy(), config);

        assertEquals(1, nodes.size());
        Location top = nodes.get(0).pillarTop();
        assertEquals(-41.5D, top.getX(), 0.0001D);
        assertEquals(82.0D, top.getY(), 0.0001D);
        assertEquals(-0.5D, top.getZ(), 0.0001D);
    }

    @Test
    void fixedPillarPositionWithoutBedrockIsIgnored() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(-42, -1, Material.OBSIDIAN, 70, 80);

        List<PillarRitualNode> nodes = scanner.scan(world.proxy(), config);

        assertEquals(0, nodes.size());
    }

    @Test
    void stackedBedrockAtFixedPillarPositionUsesTopmostBedrock() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(-42, -1, Material.BEDROCK, 81, 82);

        List<PillarRitualNode> nodes = scanner.scan(world.proxy(), config);

        assertEquals(1, nodes.size());
        Location top = nodes.get(0).pillarTop();
        assertEquals(-41.5D, top.getX(), 0.0001D);
        assertEquals(83.0D, top.getY(), 0.0001D);
        assertEquals(-0.5D, top.getZ(), 0.0001D);
    }

    @Test
    void bedrockAwayFromVanillaPillarPositionsIsIgnored() {
        FakeWorld world = new FakeWorld();
        world.placeColumn(64, 0, Material.BEDROCK, 81, 81);

        List<PillarRitualNode> nodes = scanner.scan(world.proxy(), config);

        assertEquals(0, nodes.size());
    }

    @Test
    void liveCrystalAwayFromValidCapDoesNotActivateNode() {
        FakeWorld world = new FakeWorld();
        placeOuterPillars(world);
        int[] pillar = OUTER_PILLARS.get(0);
        world.addCrystal(pillar[0] + 0.5D, 90.0D, pillar[1] + 0.5D);

        List<PillarRitualNode> nodes = scanner.scan(world.proxy(), config);

        assertEquals(10, nodes.size());
        assertEquals(0, nodes.stream().filter(PillarRitualNode::active).count());
    }

    private void placeOuterPillars(FakeWorld world) {
        for (int[] pillar : OUTER_PILLARS) {
            world.placeColumn(pillar[0], pillar[1], Material.BEDROCK, 81, 81);
        }
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

        void addCrystal(double x, double y, double z) {
            UUID uniqueId = UUID.randomUUID();
            crystals.add((EnderCrystal) Proxy.newProxyInstance(
                    EnderCrystal.class.getClassLoader(),
                    new Class<?>[]{EnderCrystal.class},
                    (ignored, method, args) -> switch (method.getName()) {
                        case "getLocation" -> new Location(proxy(), x, y, z);
                        case "getUniqueId" -> uniqueId;
                        default -> defaultValue(method.getReturnType());
                    }
            ));
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
