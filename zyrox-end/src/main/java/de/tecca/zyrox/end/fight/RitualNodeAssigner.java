package de.tecca.zyrox.end.fight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class RitualNodeAssigner {

    private static final List<RitualNodeType> DEFAULT_TEN = List.of(
            RitualNodeType.DRAGON_WARD,
            RitualNodeType.LIFE_SPIRE,
            RitualNodeType.CRYSTAL_THORNS,
            RitualNodeType.STORM_SPIRE,
            RitualNodeType.GRAVITY_ANCHOR,
            RitualNodeType.PILLAR_BEAM,
            RitualNodeType.VOID_RIFT,
            RitualNodeType.ENDER_VOLLEY,
            RitualNodeType.WING_GUST,
            RitualNodeType.DRAGON_WARD
    );

    private static final List<RitualNodeType> FALLBACK_POOL = List.of(
            RitualNodeType.DRAGON_WARD,
            RitualNodeType.LIFE_SPIRE,
            RitualNodeType.CRYSTAL_THORNS,
            RitualNodeType.STORM_SPIRE,
            RitualNodeType.GRAVITY_ANCHOR,
            RitualNodeType.PILLAR_BEAM,
            RitualNodeType.VOID_RIFT,
            RitualNodeType.ENDER_VOLLEY,
            RitualNodeType.WING_GUST
    );

    public void assign(List<PillarRitualNode> nodes, UUID dragonUniqueId) {
        List<PillarRitualNode> activeNodes = nodes.stream()
                .filter(PillarRitualNode::active)
                .toList();
        List<RitualNodeType> roles = rolesFor(activeNodes.size(), dragonUniqueId);
        for (int i = 0; i < activeNodes.size(); i++) {
            activeNodes.get(i).assignType(roles.get(i));
        }
    }

    List<RitualNodeType> rolesFor(int nodeCount, UUID dragonUniqueId) {
        List<RitualNodeType> roles = new ArrayList<>();
        List<RitualNodeType> source = nodeCount == DEFAULT_TEN.size() ? DEFAULT_TEN : FALLBACK_POOL;
        for (int i = 0; i < nodeCount; i++) {
            roles.add(source.get(i % source.size()));
        }
        Collections.shuffle(roles, random(dragonUniqueId));
        return roles;
    }

    private Random random(UUID dragonUniqueId) {
        long seed = dragonUniqueId.getMostSignificantBits() ^ Long.rotateLeft(dragonUniqueId.getLeastSignificantBits(), 17);
        return new Random(seed);
    }
}
