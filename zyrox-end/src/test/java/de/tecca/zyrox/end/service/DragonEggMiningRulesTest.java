package de.tecca.zyrox.end.service;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DragonEggMiningRulesTest {

    @Test
    void onlyDragonEggsUseCustomMining() {
        assertTrue(DragonEggMiningRules.isDragonEgg(Material.DRAGON_EGG));
        assertFalse(DragonEggMiningRules.isDragonEgg(Material.OBSIDIAN));
        assertFalse(DragonEggMiningRules.isDragonEgg(Material.AIR));
        assertFalse(DragonEggMiningRules.isDragonEgg(null));
    }

    @Test
    void onlyDiamondAndNetheritePickaxesCanMineLikeObsidian() {
        assertTrue(DragonEggMiningRules.isObsidianCapableTool(Material.DIAMOND_PICKAXE));
        assertTrue(DragonEggMiningRules.isObsidianCapableTool(Material.NETHERITE_PICKAXE));
        assertFalse(DragonEggMiningRules.isObsidianCapableTool(Material.IRON_PICKAXE));
        assertFalse(DragonEggMiningRules.isObsidianCapableTool(Material.GOLDEN_PICKAXE));
        assertFalse(DragonEggMiningRules.isObsidianCapableTool(Material.DRAGON_EGG));
        assertFalse(DragonEggMiningRules.isObsidianCapableTool(null));
    }

    @Test
    void baseBreakTicksMatchObsidianTiming() {
        assertEquals(188, DragonEggMiningRules.baseBreakTicks(Material.DIAMOND_PICKAXE));
        assertEquals(167, DragonEggMiningRules.baseBreakTicks(Material.NETHERITE_PICKAXE));
        assertEquals(Integer.MAX_VALUE, DragonEggMiningRules.baseBreakTicks(Material.IRON_PICKAXE));
    }

    @Test
    void efficiencyReducesBreakTime() {
        int baseTicks = DragonEggMiningRules.baseBreakTicks(Material.DIAMOND_PICKAXE);
        int efficientTicks = DragonEggMiningRules.adjustedBreakTicks(
                Material.DIAMOND_PICKAXE,
                5,
                -1,
                -1,
                false,
                false
        );

        assertTrue(efficientTicks < baseTicks);
        assertEquals(45, efficientTicks);
    }

    @Test
    void miningFatigueIncreasesBreakTime() {
        int baseTicks = DragonEggMiningRules.baseBreakTicks(Material.DIAMOND_PICKAXE);
        int fatiguedTicks = DragonEggMiningRules.adjustedBreakTicks(
                Material.DIAMOND_PICKAXE,
                0,
                -1,
                0,
                false,
                false
        );

        assertTrue(fatiguedTicks > baseTicks);
        assertEquals(625, fatiguedTicks);
    }

    @Test
    void waterAndAirbornePenaltiesStack() {
        assertEquals(
                4688,
                DragonEggMiningRules.adjustedBreakTicks(
                        Material.DIAMOND_PICKAXE,
                        0,
                        -1,
                        -1,
                        true,
                        true
                )
        );
    }
}
