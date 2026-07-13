package de.tecca.zyrox.end.fight;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PillarScanner {

    private static final double CRYSTAL_LINK_DISTANCE_SQUARED = 2.5D * 2.5D;

    private final PillarLayout layout;

    public PillarScanner() {
        this(new PillarLayout());
    }

    PillarScanner(PillarLayout layout) {
        this.layout = layout;
    }

    public List<PillarRitualNode> scan(World world, EndFightConfig config) {
        List<PillarRitualNode> nodes = new ArrayList<>();
        List<EnderCrystal> crystals = List.copyOf(world.getEntitiesByClass(EnderCrystal.class));
        layout.vanillaPillarPositions().stream()
                .map(position -> layout.capSpawnAt(world, position[0], position[1]))
                .flatMap(Optional::stream)
                .forEach(top -> addNode(nodes, top, matchingCrystal(top, crystals).map(EnderCrystal::getUniqueId).orElse(null)));

        nodes.sort(Comparator
                .comparingDouble((PillarRitualNode node) -> angle(node.pillarTop(), config))
                .thenComparingDouble(node -> node.pillarTop().distanceSquared(center(world, config))));
        return nodes;
    }

    private Optional<EnderCrystal> matchingCrystal(Location top, List<EnderCrystal> crystals) {
        return crystals.stream()
                .filter(crystal -> crystal.getLocation().getWorld() == top.getWorld())
                .filter(crystal -> crystal.getLocation().distanceSquared(top) <= CRYSTAL_LINK_DISTANCE_SQUARED)
                .min(Comparator.comparingDouble(crystal -> crystal.getLocation().distanceSquared(top)));
    }

    private void addNode(List<PillarRitualNode> nodes, Location top, UUID crystalUniqueId) {
        nodes.add(new PillarRitualNode(UUID.randomUUID(), top, crystalUniqueId));
    }

    private Location center(World world, EndFightConfig config) {
        return new Location(world, config.arenaCenterX(), 65.0D, config.arenaCenterZ());
    }

    private double angle(Location location, EndFightConfig config) {
        return Math.atan2(location.getZ() - config.arenaCenterZ(), location.getX() - config.arenaCenterX());
    }
}
