package de.tecca.zyrox.end.fight;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

final class TotemCrystalService {

    private static final double FIRST_RITUAL_REPLACEMENT_DISTANCE_SQUARED = 2.5D * 2.5D;

    private final NamespacedKey portalAnchorKey;
    private final NamespacedKey ritualCrystalKey;
    private final Supplier<EndFightConfig> configSupplier;
    private final BiFunction<String, String, String> message;
    private final PillarLayout layout;

    TotemCrystalService(
            NamespacedKey portalAnchorKey,
            NamespacedKey ritualCrystalKey,
            Supplier<EndFightConfig> configSupplier,
            BiFunction<String, String, String> message
    ) {
        this(portalAnchorKey, ritualCrystalKey, configSupplier, message, new PillarLayout());
    }

    TotemCrystalService(
            NamespacedKey portalAnchorKey,
            NamespacedKey ritualCrystalKey,
            Supplier<EndFightConfig> configSupplier,
            BiFunction<String, String, String> message,
            PillarLayout layout
    ) {
        this.portalAnchorKey = portalAnchorKey;
        this.ritualCrystalKey = ritualCrystalKey;
        this.configSupplier = configSupplier;
        this.message = message;
        this.layout = layout;
    }

    Optional<Location> prepareFirstRitualReplacement(PillarRitualNode node, World world) {
        Optional<Location> location = firstRitualSpawnLocation(node, world);
        location.ifPresent(spawnLocation -> {
            removeRitualCrystalsNear(world, spawnLocation);
            node.deactivate();
        });
        return location;
    }

    Optional<Location> firstRitualSpawnLocation(PillarRitualNode node, World world) {
        return layout.capSpawnNearNode(node, world);
    }

    Optional<EnderCrystal> spawnRitualCrystal(World world, PillarRitualNode node, double health) {
        Optional<Location> spawnLocation = firstRitualSpawnLocation(node, world);
        if (spawnLocation.isEmpty()) {
            return Optional.empty();
        }
        Location location = spawnLocation.get();
        removeRitualCrystalsNear(world, location);
        EnderCrystal crystal = world.spawn(location, EnderCrystal.class, spawned -> {
            spawned.setShowingBottom(false);
            spawned.getPersistentDataContainer().set(ritualCrystalKey, PersistentDataType.BYTE, (byte) 1);
        });
        node.bindCrystal(crystal.getUniqueId(), false);
        node.setHealth(health);
        return Optional.of(crystal);
    }

    void removeRitualCrystalsNear(World world, Location location) {
        if (world == null || location == null) {
            return;
        }
        for (EnderCrystal crystal : List.copyOf(world.getEntitiesByClass(EnderCrystal.class))) {
            Location crystalLocation = crystal.getLocation();
            if (crystalLocation.getWorld() == world
                    && crystalLocation.distanceSquared(location) <= FIRST_RITUAL_REPLACEMENT_DISTANCE_SQUARED) {
                crystal.remove();
            }
        }
    }

    void ensurePortalFinalAnchor(EndFightSession session, World world, double health) {
        if (session.finalAnchorDestroyed()) {
            return;
        }

        Location anchorLocation = portalAnchorLocation(world);
        List<EnderCrystal> taggedAnchors = world.getEntitiesByClass(EnderCrystal.class).stream()
                .filter(this::isPortalAnchor)
                .sorted(Comparator.comparingDouble(crystal -> crystal.getLocation().distanceSquared(anchorLocation)))
                .toList();
        EnderCrystal crystal = taggedAnchors.isEmpty() ? null : taggedAnchors.get(0);
        for (int index = 1; index < taggedAnchors.size(); index++) {
            taggedAnchors.get(index).remove();
        }

        PillarRitualNode node = session.finalAnchor().orElse(null);
        boolean boundCrystalPresent = node != null
                && node.active()
                && node.crystalUniqueId().map(Bukkit::getEntity).filter(EnderCrystal.class::isInstance).isPresent();
        if (boundCrystalPresent) {
            if (crystal != null && node.crystalUniqueId().filter(crystal.getUniqueId()::equals).isEmpty()) {
                crystal.remove();
            }
            return;
        }

        if (crystal == null || !crystal.isValid()) {
            crystal = world.spawn(anchorLocation, EnderCrystal.class, spawned -> {
                spawned.setShowingBottom(false);
                spawned.getPersistentDataContainer().set(portalAnchorKey, PersistentDataType.BYTE, (byte) 1);
            });
        } else if (crystal.getLocation().distanceSquared(anchorLocation) > 0.25D) {
            crystal.teleport(anchorLocation);
        }

        if (node == null) {
            node = new PillarRitualNode(UUID.randomUUID(), anchorLocation, null);
            session.addNode(node);
        }
        node.bindCrystal(crystal.getUniqueId(), true);
        node.assignType(RitualNodeType.FINAL_ANCHOR);
        node.setHealth(health);
        if (session.finalPhaseActive()) {
            node.unlockFor(FightPhase.FINAL);
        }
        syncCrystalIndicator(node, world);
    }

    boolean isPortalAnchor(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(portalAnchorKey, PersistentDataType.BYTE);
    }

    boolean isRitualCrystal(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(ritualCrystalKey, PersistentDataType.BYTE);
    }

    void clearRitualCrystalTag(PillarRitualNode node) {
        node.crystalUniqueId()
                .map(Bukkit::getEntity)
                .filter(this::isRitualCrystal)
                .ifPresent(entity -> entity.getPersistentDataContainer().remove(ritualCrystalKey));
    }

    Location portalAnchorLocation(World world) {
        EndFightConfig config = config();
        int blockX = (int) Math.floor(config.arenaCenterX());
        int blockZ = (int) Math.floor(config.arenaCenterZ());
        int upperY = Math.min(world.getMaxHeight() - 1, 90);
        int lowerY = Math.max(world.getMinHeight(), 40);
        for (int y = upperY; y >= lowerY; y--) {
            if (world.getBlockAt(blockX, y, blockZ).getType() == Material.BEDROCK) {
                return new Location(world, blockX + 0.5D, y + 1.0D, blockZ + 0.5D);
            }
        }
        return new Location(world, config.arenaCenterX(), 65.0D, config.arenaCenterZ());
    }

    void syncCrystalIndicators(EndFightSession session, World world) {
        if (!config().crystalIndicatorEnabled()) {
            return;
        }
        for (PillarRitualNode node : session.nodes()) {
            if (node.active()) {
                syncCrystalIndicator(node, world);
            }
        }
    }

    void syncCrystalIndicator(PillarRitualNode node, World world) {
        EndFightConfig config = config();
        if (!config.crystalIndicatorEnabled()) {
            return;
        }
        Optional<EnderCrystal> crystal = node.crystalUniqueId()
                .map(Bukkit::getEntity)
                .filter(EnderCrystal.class::isInstance)
                .map(EnderCrystal.class::cast);
        if (crystal.isEmpty()) {
            removeIndicator(node);
            return;
        }

        Location location = crystal.get().getLocation().add(0.0D, config.crystalIndicatorYOffset(), 0.0D);
        TextDisplay display = node.indicatorUniqueId()
                .map(Bukkit::getEntity)
                .filter(TextDisplay.class::isInstance)
                .map(TextDisplay.class::cast)
                .orElseGet(() -> {
                    TextDisplay spawned = world.spawn(location, TextDisplay.class, entity -> {
                        entity.setPersistent(false);
                        entity.setBillboard(Display.Billboard.CENTER);
                        entity.setSeeThrough(true);
                        entity.setShadowed(true);
                        entity.setBackgroundColor(Color.fromARGB(80, 0, 0, 0));
                    });
                    node.bindIndicator(spawned.getUniqueId());
                    return spawned;
                });
        display.teleport(location);
        updateCrystalIndicator(node);
    }

    void updateCrystalIndicator(PillarRitualNode node) {
        node.indicatorUniqueId()
                .map(Bukkit::getEntity)
                .filter(TextDisplay.class::isInstance)
                .map(TextDisplay.class::cast)
                .ifPresent(display -> display.text(Component.text(indicatorText(node))));
    }

    void removeIndicator(PillarRitualNode node) {
        node.indicatorUniqueId().map(Bukkit::getEntity).ifPresent(Entity::remove);
        node.clearIndicator();
    }

    private String indicatorText(PillarRitualNode node) {
        String nodeName = nodeName(node.type());
        if (node.locked()) {
            return message.apply("fight.messages.locked-indicator", "%node%\nVersiegelt - erwacht in Phase: %phase%")
                    .replace("%node%", nodeName)
                    .replace("%phase%", phaseName(node.requiredPhase()));
        }
        return message.apply("fight.messages.crystal-indicator", "%node%\n%bar% %health%/%max_health%")
                .replace("%node%", nodeName)
                .replace("%bar%", healthBar(node.health(), node.maxHealth()))
                .replace("%health%", Long.toString(Math.round(node.health())))
                .replace("%max_health%", Long.toString(Math.round(node.maxHealth())));
    }

    private String healthBar(double health, double maxHealth) {
        int width = Math.max(4, config().crystalIndicatorBarWidth());
        double ratio = maxHealth <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, health / maxHealth));
        int filled = (int) Math.round(width * ratio);
        return "|".repeat(filled) + "-".repeat(width - filled);
    }

    private String nodeName(RitualNodeType type) {
        return message.apply("fight.node-names." + nodeKey(type), fallbackNodeName(type));
    }

    private String phaseName(FightPhase phase) {
        return message.apply("fight.phases." + phaseKey(phase), switch (phase) {
            case OPENING -> "Erwachen";
            case TOTEMS -> "Kristallritual";
            case ESCALATION -> "Eskalation";
            case FINAL -> "Finale Bindung";
        });
    }

    private String phaseKey(FightPhase phase) {
        return switch (phase) {
            case OPENING -> "opening";
            case TOTEMS -> "totems";
            case ESCALATION -> "escalation";
            case FINAL -> "final";
        };
    }

    private String nodeKey(RitualNodeType type) {
        return switch (type) {
            case NONE -> "none";
            case DRAGON_WARD -> "dragon-ward";
            case LIFE_SPIRE -> "life-spire";
            case CRYSTAL_THORNS -> "crystal-thorns";
            case STORM_SPIRE -> "storm-spire";
            case GRAVITY_ANCHOR -> "gravity-anchor";
            case PILLAR_BEAM -> "pillar-beam";
            case VOID_RIFT -> "void-rift";
            case ENDER_VOLLEY -> "ender-volley";
            case WING_GUST -> "wing-gust";
            case FINAL_ANCHOR -> "final-anchor";
        };
    }

    private String fallbackNodeName(RitualNodeType type) {
        return switch (type) {
            case NONE -> "Endkristall";
            case DRAGON_WARD -> "Drachenwacht";
            case LIFE_SPIRE -> "Lebensspitze";
            case CRYSTAL_THORNS -> "Kristalldornen";
            case STORM_SPIRE -> "Sturmspitze";
            case GRAVITY_ANCHOR -> "Schwerkraftanker";
            case PILLAR_BEAM -> "Pfeilerstrahl";
            case VOID_RIFT -> "Leerenriss";
            case ENDER_VOLLEY -> "Endersalve";
            case WING_GUST -> "Fluegelstoss";
            case FINAL_ANCHOR -> "Finaler Anker";
        };
    }

    private EndFightConfig config() {
        return configSupplier.get();
    }
}
