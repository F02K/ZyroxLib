package de.tecca.zyrox.end.fight;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class RitualNodeAssignerTest {

    private final RitualNodeAssigner assigner = new RitualNodeAssigner();

    @Test
    void tenInitialCrystalsReceiveDefinedRolesWithoutNoneOrFinalAnchor() {
        List<RitualNodeType> roles = assigner.rolesFor(10, UUID.randomUUID());

        assertEquals(10, roles.size());
        assertFalse(roles.contains(RitualNodeType.NONE));
        assertFalse(roles.contains(RitualNodeType.FINAL_ANCHOR));
        assertEquals(
                EnumSet.of(
                        RitualNodeType.DRAGON_WARD,
                        RitualNodeType.LIFE_SPIRE,
                        RitualNodeType.CRYSTAL_THORNS,
                        RitualNodeType.STORM_SPIRE,
                        RitualNodeType.GRAVITY_ANCHOR,
                        RitualNodeType.PILLAR_BEAM,
                        RitualNodeType.VOID_RIFT,
                        RitualNodeType.ENDER_VOLLEY,
                        RitualNodeType.WING_GUST
                ),
                EnumSet.copyOf(roles)
        );
    }

    @Test
    void fallbackRolesNeverUseNoneForUnexpectedCrystalCounts() {
        List<RitualNodeType> roles = assigner.rolesFor(13, UUID.randomUUID());

        assertEquals(13, roles.size());
        assertFalse(roles.contains(RitualNodeType.NONE));
        assertFalse(roles.contains(RitualNodeType.FINAL_ANCHOR));
    }
}
