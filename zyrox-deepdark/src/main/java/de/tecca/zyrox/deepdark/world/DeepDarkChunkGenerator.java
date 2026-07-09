package de.tecca.zyrox.deepdark.world;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

public final class DeepDarkChunkGenerator extends ChunkGenerator {

    private final int cavernHeight;
    private final int hallDensity;
    private final int tunnelDensity;
    private final int floorRoughness;
    private final int ceilingRoughness;
    private final int sculkDensity;

    public DeepDarkChunkGenerator(int cavernHeight, int hallDensity, int tunnelDensity, int floorRoughness, int ceilingRoughness, int sculkDensity) {
        this.cavernHeight = Math.max(48, Math.min(96, cavernHeight));
        this.hallDensity = Math.max(0, Math.min(100, hallDensity));
        this.tunnelDensity = Math.max(0, Math.min(100, tunnelDensity));
        this.floorRoughness = Math.max(1, Math.min(22, floorRoughness));
        this.ceilingRoughness = Math.max(1, Math.min(26, ceilingRoughness));
        this.sculkDensity = Math.max(0, Math.min(100, sculkDensity));
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int minY = Math.max(chunkData.getMinHeight(), 0);
        int maxY = Math.min(chunkData.getMaxHeight(), 128);
        int baseFloor = 22;
        int baseCeiling = baseFloor + cavernHeight;
        long seed = worldInfo.getSeed();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkX * 16 + localX;
                int worldZ = chunkZ * 16 + localZ;
                ColumnProfile profile = profile(worldX, worldZ, seed, baseFloor, baseCeiling);

                for (int y = minY; y < maxY; y++) {
                    if (y <= minY + 1 || y >= maxY - 2) {
                        chunkData.setBlock(localX, y, localZ, Material.BEDROCK);
                        continue;
                    }

                    if (isMainMass(y, profile) && !isTunnelAir(worldX, y, worldZ, seed, profile)) {
                        chunkData.setBlock(localX, y, localZ, terrainBlock(worldX, y, worldZ, seed, y == profile.floor()));
                        continue;
                    }

                    if (isColumn(worldX, y, worldZ, seed, profile)) {
                        chunkData.setBlock(localX, y, localZ, terrainBlock(worldX, y, worldZ, seed, false));
                        continue;
                    }

                    if (isStalagmite(y, profile, worldX, worldZ, seed) || isStalactite(y, profile, worldX, worldZ, seed)) {
                        chunkData.setBlock(localX, y, localZ, terrainBlock(worldX, y, worldZ, seed, false));
                        continue;
                    }

                    decorateAir(chunkData, localX, y, localZ, worldX, worldZ, seed, profile);
                }
            }
        }

        if (chunkX == 0 && chunkZ == 0) {
            carveSpawnPocket(chunkData);
        }
    }

    @Override
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        return 70;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, 70, 0.5, 0, 0);
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    private ColumnProfile profile(int x, int z, long seed, int baseFloor, int baseCeiling) {
        double macro = octaveNoise(x, z, seed + 11, 220.0, 3, 0.55);
        double hallShape = ridge(octaveNoise(x - 6000, z + 6000, seed + 31, 180.0, 3, 0.52));
        double hallThreshold = 0.78 - hallDensity * 0.0036;
        double hall = smoothClamp((hallShape - hallThreshold) / Math.max(0.001, 1.0 - hallThreshold));
        double plateau = smoothClamp((valueNoise(x + 17000, z - 17000, seed + 77, 145.0) - 0.52) / 0.28);
        double floorNoise = octaveNoise(x + 3000, z - 3000, seed + 101, 78.0, 4, 0.5) - 0.5;
        double ceilingNoise = octaveNoise(x - 9000, z + 9000, seed + 151, 96.0, 4, 0.48) - 0.5;

        double floorOffset = floorNoise * floorRoughness * (1.0 - hall * 0.45);
        double targetFlatFloor = (macro - 0.5) * 5.0 + 2.0;
        int floor = baseFloor + (int) Math.round(lerp(floorOffset, targetFlatFloor, plateau * 0.7) - hall * 4.0);
        int ceiling = baseCeiling + (int) Math.round(ceilingNoise * ceilingRoughness + hall * 18.0 + (macro - 0.5) * 7.0);

        if (ceiling - floor < 26) {
            ceiling = floor + 26;
        }
        return new ColumnProfile(floor, ceiling, hall);
    }

    private boolean isMainMass(int y, ColumnProfile profile) {
        return y <= profile.floor() || y >= profile.ceiling();
    }

    private boolean isTunnelAir(int x, int y, int z, long seed, ColumnProfile profile) {
        if (tunnelDensity <= 0 || y < profile.floor() - 14 || y > profile.ceiling() + 14) {
            return false;
        }
        double tunnelA = ridge(valueNoise3d(x + 5000, y * 2, z - 5000, seed + 401, 34.0));
        double tunnelB = ridge(valueNoise3d(x - 9000, y, z + 9000, seed + 409, 72.0));
        double verticalCenter = (profile.floor() + profile.ceiling()) / 2.0;
        double verticalWeight = 1.0 - Math.min(1.0, Math.abs(y - verticalCenter) / Math.max(18.0, cavernHeight * 0.55));
        double score = (tunnelA * 0.72 + tunnelB * 0.28) * (0.58 + verticalWeight * 0.42);
        return score > 0.88 - tunnelDensity * 0.0028;
    }

    private boolean isColumn(int x, int y, int z, long seed, ColumnProfile profile) {
        if (y <= profile.floor() + 3 || y >= profile.ceiling() - 3 || profile.hallPower() < 0.25) {
            return false;
        }
        double column = ridge(valueNoise(x + 21000, z - 21000, seed + 503, 28.0));
        double core = valueNoise(x - 12000, z + 12000, seed + 509, 11.0);
        double vertical = 1.0 - Math.abs(y - (profile.floor() + profile.ceiling()) / 2.0) / ((profile.ceiling() - profile.floor()) / 2.0);
        return column > 0.86 && core > 0.45 && vertical > 0.16;
    }

    private boolean isStalagmite(int y, ColumnProfile profile, int x, int z, long seed) {
        int height = spikeHeight(x, z, seed + 601, 8);
        return height > 2 && y > profile.floor() && y <= profile.floor() + height;
    }

    private boolean isStalactite(int y, ColumnProfile profile, int x, int z, long seed) {
        int height = spikeHeight(x, z, seed + 607, 10);
        return height > 3 && y < profile.ceiling() && y >= profile.ceiling() - height;
    }

    private int spikeHeight(int x, int z, long seed, int maxHeight) {
        double patch = ridge(valueNoise(x, z, seed, 18.0));
        double core = valueNoise(x + 300, z - 300, seed + 5, 7.0);
        if (patch < 0.8 || core < 0.58) {
            return 0;
        }
        return 2 + (int) Math.round((patch * core) * maxHeight);
    }

    private void decorateAir(ChunkData chunkData, int localX, int y, int localZ, int worldX, int worldZ, long seed, ColumnProfile profile) {
        int patchScore = patchScore(worldX, worldZ, seed);
        if (y == profile.floor() + 1 && patchScore < sculkDensity * 6) {
            chunkData.setBlock(localX, y, localZ, sculkFeature(worldX, worldZ, patchScore));
            return;
        }
        if (y == profile.ceiling() - 1 && patchScore < sculkDensity * 2) {
            chunkData.setBlock(localX, y, localZ, Material.SCULK_VEIN);
        }
    }

    private Material terrainBlock(int x, int y, int z, long seed, boolean surface) {
        if (surface && patchScore(x, z, seed) < sculkDensity * 8) {
            return Material.SCULK;
        }
        double strata = valueNoise(x + y * 3, z - y * 2, seed + 701, 24.0);
        double biome = valueNoise(x - 26000, z + 26000, seed + 709, 130.0);
        if (biome > 0.72 && strata > 0.35) {
            return Material.BLACKSTONE;
        }
        if (biome < 0.24 && strata > 0.45) {
            return Material.TUFF;
        }
        if (strata > 0.86) {
            return Material.POLISHED_DEEPSLATE;
        }
        if (strata > 0.72) {
            return Material.COBBLED_DEEPSLATE;
        }
        return Material.DEEPSLATE;
    }

    private Material sculkFeature(int x, int z, int score) {
        int value = Math.floorMod(x * 41 + z * 67 + score, 100);
        if (value > 97) {
            return Material.SCULK_SHRIEKER;
        }
        if (value > 93) {
            return Material.SCULK_CATALYST;
        }
        if (value > 86) {
            return Material.SCULK_SENSOR;
        }
        return Material.SCULK_VEIN;
    }

    private void carveSpawnPocket(ChunkData chunkData) {
        for (int x = 1; x <= 14; x++) {
            for (int z = 1; z <= 14; z++) {
                for (int y = 64; y <= 78; y++) {
                    chunkData.setBlock(x, y, z, Material.AIR);
                }
                chunkData.setBlock(x, 63, z, Material.COBBLED_DEEPSLATE);
                chunkData.setBlock(x, 64, z, x == 1 || x == 14 || z == 1 || z == 14 ? Material.DEEPSLATE_BRICKS : Material.POLISHED_DEEPSLATE);
            }
        }
        chunkData.setBlock(7, 65, 7, Material.SCULK_CATALYST);
        chunkData.setBlock(8, 65, 8, Material.SCULK);
    }

    private int patchScore(int x, int z, long seed) {
        double patch = valueNoise(x, z, seed + 771, 54.0);
        double local = valueNoise(x + 1200, z - 1200, seed - 337, 18.0);
        return (int) Math.round((patch * 0.78 + local * 0.22) * 1000.0);
    }

    private double octaveNoise(int x, int z, long seed, double scale, int octaves, double persistence) {
        double value = 0.0;
        double amplitude = 1.0;
        double total = 0.0;
        double currentScale = scale;
        for (int octave = 0; octave < octaves; octave++) {
            value += valueNoise(x, z, seed + octave * 1297L, currentScale) * amplitude;
            total += amplitude;
            amplitude *= persistence;
            currentScale *= 0.5;
        }
        return value / total;
    }

    private double valueNoise(int x, int z, long seed, double scale) {
        double sampleX = x / scale;
        double sampleZ = z / scale;
        int x0 = fastFloor(sampleX);
        int z0 = fastFloor(sampleZ);
        double tx = smooth(sampleX - x0);
        double tz = smooth(sampleZ - z0);

        double a = randomUnit(x0, z0, seed);
        double b = randomUnit(x0 + 1, z0, seed);
        double c = randomUnit(x0, z0 + 1, seed);
        double d = randomUnit(x0 + 1, z0 + 1, seed);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    private double valueNoise3d(int x, int y, int z, long seed, double scale) {
        double sampleX = x / scale;
        double sampleY = y / scale;
        double sampleZ = z / scale;
        int x0 = fastFloor(sampleX);
        int y0 = fastFloor(sampleY);
        int z0 = fastFloor(sampleZ);
        double tx = smooth(sampleX - x0);
        double ty = smooth(sampleY - y0);
        double tz = smooth(sampleZ - z0);

        double c000 = randomUnit(x0, y0, z0, seed);
        double c100 = randomUnit(x0 + 1, y0, z0, seed);
        double c010 = randomUnit(x0, y0 + 1, z0, seed);
        double c110 = randomUnit(x0 + 1, y0 + 1, z0, seed);
        double c001 = randomUnit(x0, y0, z0 + 1, seed);
        double c101 = randomUnit(x0 + 1, y0, z0 + 1, seed);
        double c011 = randomUnit(x0, y0 + 1, z0 + 1, seed);
        double c111 = randomUnit(x0 + 1, y0 + 1, z0 + 1, seed);

        double x00 = lerp(c000, c100, tx);
        double x10 = lerp(c010, c110, tx);
        double x01 = lerp(c001, c101, tx);
        double x11 = lerp(c011, c111, tx);
        return lerp(lerp(x00, x10, ty), lerp(x01, x11, ty), tz);
    }

    private int fastFloor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private double smooth(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private double smoothClamp(double value) {
        return smooth(Math.max(0.0, Math.min(1.0, value)));
    }

    private double ridge(double value) {
        return 1.0 - Math.abs(value * 2.0 - 1.0);
    }

    private double lerp(double left, double right, double amount) {
        return left + (right - left) * amount;
    }

    private double randomUnit(int x, int z, long seed) {
        long value = x * 341873128712L + z * 132897987541L + seed * 31L;
        return randomUnit(value);
    }

    private double randomUnit(int x, int y, int z, long seed) {
        long value = x * 341873128712L + y * 83492791L + z * 132897987541L + seed * 31L;
        return randomUnit(value);
    }

    private double randomUnit(long value) {
        value ^= value >>> 13;
        value *= 1274126177L;
        value ^= value >>> 16;
        return Math.floorMod(value, 10000L) / 10000.0;
    }

    private record ColumnProfile(int floor, int ceiling, double hallPower) {
    }
}
