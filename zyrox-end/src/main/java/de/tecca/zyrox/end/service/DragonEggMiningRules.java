package de.tecca.zyrox.end.service;

import org.bukkit.Material;

public final class DragonEggMiningRules {

    static final double OBSIDIAN_HARDNESS = 50.0D;
    private static final double TICKS_PER_CORRECT_TOOL_BREAK = 30.0D;
    private static final double DIAMOND_PICKAXE_SPEED = 8.0D;
    private static final double NETHERITE_PICKAXE_SPEED = 9.0D;

    private DragonEggMiningRules() {
    }

    public static boolean isDragonEgg(Material material) {
        return material == Material.DRAGON_EGG;
    }

    public static boolean isObsidianCapableTool(Material material) {
        return material == Material.DIAMOND_PICKAXE || material == Material.NETHERITE_PICKAXE;
    }

    public static int baseBreakTicks(Material toolType) {
        return adjustedBreakTicks(toolType, 0, -1, -1, false, false);
    }

    public static int adjustedBreakTicks(
            Material toolType,
            int efficiencyLevel,
            int hasteAmplifier,
            int miningFatigueAmplifier,
            boolean underwaterWithoutAquaAffinity,
            boolean airborne
    ) {
        double damagePerTick = damagePerTick(
                toolType,
                efficiencyLevel,
                hasteAmplifier,
                miningFatigueAmplifier,
                underwaterWithoutAquaAffinity,
                airborne
        );
        if (damagePerTick <= 0.0D) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.ceil(1.0D / damagePerTick));
    }

    public static double damagePerTick(
            Material toolType,
            int efficiencyLevel,
            int hasteAmplifier,
            int miningFatigueAmplifier,
            boolean underwaterWithoutAquaAffinity,
            boolean airborne
    ) {
        if (!isObsidianCapableTool(toolType)) {
            return 0.0D;
        }

        double speed = baseToolSpeed(toolType);
        if (efficiencyLevel > 0) {
            speed += efficiencyLevel * efficiencyLevel + 1.0D;
        }
        if (hasteAmplifier >= 0) {
            speed *= 1.0D + (hasteAmplifier + 1) * 0.2D;
        }
        if (miningFatigueAmplifier >= 0) {
            speed *= miningFatigueMultiplier(miningFatigueAmplifier);
        }
        if (underwaterWithoutAquaAffinity) {
            speed /= 5.0D;
        }
        if (airborne) {
            speed /= 5.0D;
        }

        return speed / OBSIDIAN_HARDNESS / TICKS_PER_CORRECT_TOOL_BREAK;
    }

    private static double baseToolSpeed(Material toolType) {
        if (toolType == Material.NETHERITE_PICKAXE) {
            return NETHERITE_PICKAXE_SPEED;
        }
        return DIAMOND_PICKAXE_SPEED;
    }

    private static double miningFatigueMultiplier(int amplifier) {
        return switch (amplifier) {
            case 0 -> 0.3D;
            case 1 -> 0.09D;
            case 2 -> 0.0027D;
            default -> 0.00081D;
        };
    }
}
