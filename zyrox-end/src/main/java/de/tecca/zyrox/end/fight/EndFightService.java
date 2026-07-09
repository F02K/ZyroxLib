package de.tecca.zyrox.end.fight;

import de.tecca.zyrox.end.service.EndDragonEggService;
import de.tecca.zyrox.end.service.FirstEndRitualStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.attribute.Attribute;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;

public final class EndFightService {

    private final JavaPlugin plugin;
    private final EndDragonEggService dragonEggService;
    private final EndFightDamageCalculator damageCalculator = new EndFightDamageCalculator();
    private final PillarScanner pillarScanner = new PillarScanner();
    private final RitualNodeAssigner nodeAssigner = new RitualNodeAssigner();
    private final DragonAttackScheduler attackScheduler = new DragonAttackScheduler();
    private final NamespacedKey temporaryCrystalKey;
    private final NamespacedKey guardianKey;
    private final NamespacedKey ritualEvokerKey;
    private final NamespacedKey ritualCrystalKey;
    private final Map<String, EndFightSession> sessionsByWorld = new HashMap<>();
    private final Map<String, FirstEndRitualSession> ritualsByWorld = new HashMap<>();
    private final Map<String, BossBar> bossBarsByWorld = new HashMap<>();
    private final Set<UUID> crystalsBeingDestroyed = new HashSet<>();
    private EndFightConfig config;
    private EndFightSettings settings;
    private BukkitTask tickTask;
    private BukkitTask directAttackControlTask;
    private BukkitTask ritualAnimationTask;

    public EndFightService(JavaPlugin plugin, EndDragonEggService dragonEggService) {
        this.plugin = plugin;
        this.dragonEggService = dragonEggService;
        this.config = EndFightConfig.from(plugin.getConfig());
        this.settings = EndFightSettings.from(plugin.getConfig());
        this.temporaryCrystalKey = new NamespacedKey(plugin, "temporary_final_anchor");
        this.guardianKey = new NamespacedKey(plugin, "fight_guardian");
        this.ritualEvokerKey = new NamespacedKey(plugin, "first_ritual_evoker");
        this.ritualCrystalKey = new NamespacedKey(plugin, "first_ritual_crystal");
    }

    public void start() {
        cleanupOrphanedRitualEvokers();
        scanExistingDragons();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        startDirectAttackControlTask();
        startRitualAnimationTask();
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (directAttackControlTask != null) {
            directAttackControlTask.cancel();
            directAttackControlTask = null;
        }
        if (ritualAnimationTask != null) {
            ritualAnimationTask.cancel();
            ritualAnimationTask = null;
        }
        for (EndFightSession session : List.copyOf(sessionsByWorld.values())) {
            cleanup(session);
        }
        sessionsByWorld.clear();
        ritualsByWorld.clear();
    }

    public void reloadConfig() {
        config = EndFightConfig.from(plugin.getConfig());
        settings = EndFightSettings.from(plugin.getConfig());
        startDirectAttackControlTask();
        startRitualAnimationTask();
    }

    public EndFightConfig config() {
        return config;
    }

    public Collection<EndFightSession> sessions() {
        return List.copyOf(sessionsByWorld.values());
    }

    public Optional<EndFightSession> session(String worldName) {
        return Optional.ofNullable(sessionsByWorld.get(worldName));
    }

    public void trackDragon(EnderDragon dragon) {
        if (!config.enabled() || dragon.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }

        EndFightSession session = sessionsByWorld.computeIfAbsent(
                dragon.getWorld().getName(),
                worldName -> {
                    boolean firstFight = !dragonEggService.state().firstDragonKilled();
                    boolean ritualPending = firstFight
                            && settings.ritual().enabled()
                            && dragonEggService.state().ritualStatus() != FirstEndRitualStatus.COMPLETED;
                    EndFightSession created = new EndFightSession(
                            dragon.getUniqueId(),
                            worldName,
                            firstFight ? FightProfile.FIRST : FightProfile.REPEAT,
                            ritualPending ? FightActivationState.DORMANT : FightActivationState.ACTIVE
                    );
                    for (PillarRitualNode node : pillarScanner.scan(dragon.getWorld(), config)) {
                        node.setHealth(nodeHealth(created, config.crystalHealth(0)));
                        created.addNode(node);
                    }
                    nodeAssigner.assign(new ArrayList<>(created.nodes()), dragon.getUniqueId());
                    recordCrystalLayout(worldName, created);
                    if (created.active()) {
                        activateFight(created, dragon);
                    } else {
                        makeDragonDormant(dragon);
                    }
                    plugin.getLogger().info("Enhanced End fight started in " + worldName
                            + " with " + created.detectedPillarCount() + " detected pillar nodes"
                            + (created.active() ? "." : " and is waiting for the first-entry ritual."));
                    return created;
                }
        );
        if (!session.active()) {
            makeDragonDormant(dragon);
            return;
        }
        applyDragonHealth(dragon, session);
        updatePhase(dragon, session);
        syncBossBar(dragon, session);
    }

    public void handleEndEntry(Player player) {
        if (player == null || player.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        ensureFirstRitualStarted(player.getWorld());
    }

    public void ensureFirstRitualStarted(World world) {
        if (world == null || world.getEnvironment() != World.Environment.THE_END) {
            return;
        }
        EndFightSession session = sessionsByWorld.get(world.getName());
        if (session == null) {
            world.getEntitiesByClass(EnderDragon.class).stream()
                    .filter(dragon -> !dragon.isDead())
                    .findFirst()
                    .ifPresent(this::trackDragon);
            session = sessionsByWorld.get(world.getName());
        }
        int eligiblePlayers = (int) world.getPlayers().stream()
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .count();
        if (session == null || !shouldStartFirstRitual(session.activationState(), eligiblePlayers)) {
            return;
        }
        startFirstRitual(session, world);
    }

    static boolean shouldStartFirstRitual(FightActivationState activationState, int eligiblePlayerCount) {
        return activationState == FightActivationState.DORMANT && eligiblePlayerCount > 0;
    }

    public Optional<EndFightSession> sessionForDragon(EnderDragon dragon) {
        EndFightSession session = sessionsByWorld.get(dragon.getWorld().getName());
        if (session == null || !session.dragonUniqueId().equals(dragon.getUniqueId())) {
            return Optional.empty();
        }
        return Optional.of(session);
    }

    private void startFirstRitual(EndFightSession session, World world) {
        Optional<EnderDragon> dragon = dragon(session);
        if (dragon.isEmpty() || session.activationState() != FightActivationState.DORMANT) {
            return;
        }

        if (!ritualsByWorld.isEmpty() || !dragonEggService.startFirstEndRitual(world.getName())) {
            return;
        }
        session.setActivationState(FightActivationState.RITUAL);
        FirstEndRitualSession ritual = new FirstEndRitualSession(new ArrayList<>(session.nodes()), center(world));
        ritualsByWorld.put(world.getName(), ritual);
        startRitualAnimationTask();

        for (PillarRitualNode node : session.nodes()) {
            node.crystalUniqueId().map(Bukkit::getEntity).ifPresent(Entity::remove);
            node.deactivate();
            Location location = node.pillarTop();
            Evoker evoker = world.spawn(location, Evoker.class, spawned -> {
                spawned.getPersistentDataContainer().set(ritualEvokerKey, PersistentDataType.BYTE, (byte) 1);
                spawned.setAI(false);
                spawned.setInvulnerable(true);
                spawned.setCollidable(false);
                spawned.setPersistent(false);
                spawned.setRemoveWhenFarAway(false);
                spawned.customName(Component.text("Ritualwirker"));
                spawned.setCustomNameVisible(false);
                spawned.setSpell(Spellcaster.Spell.WOLOLO);
            });
            orientEvoker(evoker, center(world));
            ritual.bindEvoker(node.id(), evoker.getUniqueId());
        }

        makeDragonDormant(dragon.get());
        BossBar bossBar = bossBarsByWorld.computeIfAbsent(world.getName(), ignored ->
                Bukkit.createBossBar(message("fight.messages.ritual-bossbar", "Das Ritual erwacht"), BarColor.PURPLE, BarStyle.SEGMENTED_10));
        bossBar.setTitle(message("fight.messages.ritual-bossbar", "Das Ritual erwacht"));
        bossBar.setProgress(0.0D);
        for (Player player : world.getPlayers()) {
            bossBar.addPlayer(player);
        }
        announce(world, message("fight.messages.ritual-start", "Die Ritualwirker rufen den Drachen."));
        showRitualTitle(world, "Das Ritual erwacht", "Die Wirker rufen in die Leere");
        world.playSound(center(world), Sound.BLOCK_END_PORTAL_SPAWN, 2.5F, 0.55F);
    }

    private void tickFirstRitual(EndFightSession session, World world, EnderDragon dragon) {
        FirstEndRitualSession ritual = ritualsByWorld.get(world.getName());
        if (ritual == null) {
            session.setActivationState(FightActivationState.DORMANT);
            return;
        }

        EndFightSettings.Ritual timing = settings.ritual();
        long previousTicks = ritual.advance(timing.animationIntervalTicks());
        makeDragonDormant(dragon);
        faceRitualEvokers(ritual, world);
        renderRitualFoundation(ritual, world);

        FirstEndRitualAct act = ritual.act(
                timing.buildUpTicks(),
                timing.activationTicks(),
                timing.finaleStartTick(),
                timing.totalTicks()
        );
        int target = ritual.targetActivatedNodes(timing.buildUpTicks(), timing.activationTicks());
        while (ritual.activatedNodes() < target) {
            ritual.nextNodeId()
                    .flatMap(session::node)
                    .ifPresent(node -> activateRitualNode(session, ritual, world, node));
            ritual.markActivated();
        }
        if (act == FirstEndRitualAct.AWAKENING) {
            renderRitualAwakening(ritual, world);
        } else if (act == FirstEndRitualAct.TRANSFORMATIONS) {
            renderRitualTransformationChannel(session, ritual, world, timing);
        } else if (act == FirstEndRitualAct.CONVERGENCE || act == FirstEndRitualAct.FINALE) {
            renderRitualConvergence(ritual, world, timing, act == FirstEndRitualAct.FINALE);
        }

        renderActivatedTotemLinks(session, ritual, world);
        runRitualStageCues(ritual, world, previousTicks, timing);
        updateRitualBossBar(ritual, world, act, timing);

        if (act == FirstEndRitualAct.COMPLETE) {
            completeFirstRitual(session, ritual, world, dragon);
        }
    }

    private void renderRitualFoundation(FirstEndRitualSession ritual, World world) {
        double phase = ritual.elapsedTicks() / 12.0D;
        Location ritualCenter = center(world).add(0.0D, 0.35D, 0.0D);
        ParticleEffects.spawnForcedOrbit(world, ritualCenter, 5.0D, phase, 0.12D, Particle.REVERSE_PORTAL, 30);
        ParticleEffects.spawnForcedOrbit(world, ritualCenter, 8.0D, -phase * 0.7D, 0.2D, Particle.SOUL_FIRE_FLAME, 38);
        ParticleEffects.spawnForcedDust(world, ritualCenter, Color.fromRGB(35, 0, 55), 1.4F, 4, 2.5D, 0.4D, 2.5D);
    }

    private void renderRitualAwakening(FirstEndRitualSession ritual, World world) {
        long frame = ritual.elapsedTicks() / settings.ritual().animationIntervalTicks();
        if (frame % 3L != 0L) {
            return;
        }
        for (UUID evokerId : ritual.evokerIds()) {
            Entity entity = Bukkit.getEntity(evokerId);
            if (!(entity instanceof Evoker evoker) || !evoker.isValid()) {
                continue;
            }
            Location feet = evoker.getLocation().add(0.0D, 0.1D, 0.0D);
            ParticleEffects.spawnForcedOrbit(
                    world,
                    feet,
                    1.5D,
                    ritual.elapsedTicks() / 8.0D,
                    0.05D,
                    Particle.REVERSE_PORTAL,
                    14
            );
            ParticleEffects.spawnForcedSimple(
                    world,
                    Particle.SOUL_FIRE_FLAME,
                    feet.clone().add(0.0D, 1.0D, 0.0D),
                    2,
                    0.35D,
                    0.8D,
                    0.35D,
                    0.01D
            );
        }
    }

    private void renderRitualTransformationChannel(
            EndFightSession session,
            FirstEndRitualSession ritual,
            World world,
            EndFightSettings.Ritual timing
    ) {
        Optional<PillarRitualNode> currentNode = ritual.channelingNodeId(timing.buildUpTicks(), timing.activationTicks())
                .flatMap(session::node);
        if (currentNode.isEmpty()) {
            return;
        }
        double progress = ritual.channelProgress(timing.buildUpTicks(), timing.activationTicks());
        double eased = progress * progress * (3.0D - 2.0D * progress);
        Location from = center(world).add(0.0D, 1.2D, 0.0D);
        Location to = currentNode.get().pillarTop().add(0.0D, 1.2D, 0.0D);
        Location orb = ParticleEffects.interpolate(from, to, eased);
        ParticleEffects.spawnForcedDust(world, orb, Color.fromRGB(150, 25, 220), 2.0F, 10, 0.25D, 0.25D, 0.25D);
        ParticleEffects.spawnForcedSimple(world, Particle.END_ROD, orb, 5, 0.18D, 0.18D, 0.18D, 0.01D);
        for (int i = 1; i <= 6; i++) {
            Location tail = ParticleEffects.interpolate(from, to, Math.max(0.0D, eased - i * 0.025D));
            ParticleEffects.spawnForcedSimple(world, Particle.REVERSE_PORTAL, tail, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        ritual.evoker(currentNode.get().id())
                .map(Bukkit::getEntity)
                .filter(Evoker.class::isInstance)
                .map(Evoker.class::cast)
                .ifPresent(evoker -> {
                    long slotTicks = Math.max(1L, timing.activationTicks() / Math.max(1, ritual.nodeCount()));
                    long slotElapsed = Math.max(0L, ritual.elapsedTicks() - timing.buildUpTicks()) % slotTicks;
                    if (ritual.elapsedTicks() % 20L < timing.animationIntervalTicks()) {
                        evoker.setSpell(Spellcaster.Spell.SUMMON_VEX);
                    }
                    if (slotElapsed < timing.animationIntervalTicks()) {
                        world.playSound(evoker.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.8F, 0.6F);
                    }
                    ParticleEffects.spawnForcedOrbit(
                            world,
                            evoker.getLocation().add(0.0D, 1.0D, 0.0D),
                            0.7D + progress * 1.3D,
                            ritual.elapsedTicks() / 5.0D,
                            0.35D,
                            Particle.WITCH,
                            18
                    );
                });
    }

    private void renderActivatedTotemLinks(EndFightSession session, FirstEndRitualSession ritual, World world) {
        long frame = ritual.elapsedTicks() / settings.ritual().animationIntervalTicks();
        Location focus = ritualFocus(world);
        int index = 0;
        for (PillarRitualNode node : session.nodes()) {
            if (node.active() && (frame + index) % 5L == 0L) {
                ParticleEffects.spawnForcedBeam(
                        world,
                        node.pillarTop().add(0.0D, 1.0D, 0.0D),
                        focus,
                        Particle.REVERSE_PORTAL,
                        24
                );
            }
            index++;
        }
    }

    private void renderRitualConvergence(
            FirstEndRitualSession ritual,
            World world,
            EndFightSettings.Ritual timing,
            boolean finale
    ) {
        Location focus = ritualFocus(world);
        double phase = ritual.elapsedTicks() / 8.0D;
        double finaleProgress = finale
                ? Math.max(0.0D, Math.min(1.0D, (ritual.elapsedTicks() - timing.finaleStartTick()) / (double) timing.finaleTicks()))
                : 0.0D;
        double contraction = finale ? 1.0D - finaleProgress * 0.75D : 1.0D;
        ParticleEffects.spawnForcedOrbit(world, focus, 4.0D * contraction, phase, 0.8D, Particle.DRAGON_BREATH, 28);
        ParticleEffects.spawnForcedOrbit(world, focus, 7.0D * contraction, -phase * 0.7D, 1.2D, Particle.REVERSE_PORTAL, 34);
        ParticleEffects.spawnForcedOrbit(world, focus, 10.0D * contraction, phase * 0.45D, 1.7D, Particle.SOUL_FIRE_FLAME, 40);
        for (int i = 0; i < 22; i++) {
            double progress = i / 22.0D;
            double angle = phase + progress * Math.PI * 6.0D;
            Location point = center(world).add(
                    Math.cos(angle) * (2.5D + progress * 2.0D) * contraction,
                    1.0D + progress * 28.0D,
                    Math.sin(angle) * (2.5D + progress * 2.0D) * contraction
            );
            ParticleEffects.spawnForcedSimple(world, Particle.DRAGON_BREATH, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        ParticleEffects.spawnForcedBeam(world, focus, center(world).add(0.0D, 36.0D, 0.0D), Particle.END_ROD, 24);

        long pulseInterval = finale ? 20L : 40L;
        if (ritual.elapsedTicks() % pulseInterval < timing.animationIntervalTicks()) {
            double radius = finale ? 4.0D + finaleProgress * 18.0D : 8.0D;
            ParticleEffects.spawnForcedOrbit(
                    world,
                    center(world).add(0.0D, 0.25D, 0.0D),
                    radius,
                    0.0D,
                    0.0D,
                    Particle.END_ROD,
                    56
            );
            world.playSound(focus, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.0F, (float) (0.55D + finaleProgress * 0.65D));
        }
        if (ritual.elapsedTicks() % 80L < timing.animationIntervalTicks()) {
            double convergenceProgress = Math.max(
                    0.0D,
                    Math.min(1.0D, (ritual.elapsedTicks() - timing.convergenceStartTick())
                            / (double) Math.max(1L, timing.totalTicks() - timing.convergenceStartTick()))
            );
            world.playSound(focus, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 3.0F, (float) (0.45D + convergenceProgress * 0.55D));
        }
    }

    private void runRitualStageCues(
            FirstEndRitualSession ritual,
            World world,
            long previousTicks,
            EndFightSettings.Ritual timing
    ) {
        if (ritual.crossed(previousTicks, timing.buildUpTicks())) {
            showRitualTitle(world, "Die erste Bindung beginnt", "Folgt dem Impuls");
            world.playSound(center(world), Sound.BLOCK_BEACON_ACTIVATE, 3.0F, 0.55F);
        }
        if (ritual.crossed(previousTicks, timing.convergenceStartTick())) {
            showRitualTitle(world, "Die Energien vereinen sich", "Das Ende beginnt zu beben");
            world.playSound(ritualFocus(world), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 4.0F, 0.55F);
        }
        if (ritual.crossed(previousTicks, timing.finaleStartTick())) {
            showRitualTitle(world, "Der Drache erwacht", "");
            world.playSound(ritualFocus(world), Sound.ENTITY_ENDER_DRAGON_GROWL, 5.0F, 0.45F);
        }
        if (ritual.elapsedTicks() < timing.buildUpTicks()
                && ritual.elapsedTicks() % 100L < timing.animationIntervalTicks()) {
            float awakeningPitch = (float) (0.45D + 0.4D * ritual.elapsedTicks() / timing.buildUpTicks());
            world.playSound(center(world), Sound.BLOCK_BEACON_AMBIENT, 1.5F, awakeningPitch);
            world.playSound(center(world), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4F, awakeningPitch);
            world.playSound(center(world), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.3F, awakeningPitch);
        }
    }

    private void updateRitualBossBar(
            FirstEndRitualSession ritual,
            World world,
            FirstEndRitualAct act,
            EndFightSettings.Ritual timing
    ) {
        BossBar bossBar = bossBarsByWorld.get(world.getName());
        if (bossBar == null) {
            return;
        }
        bossBar.setProgress(Math.min(1.0D, ritual.elapsedTicks() / (double) timing.totalTicks()));
        String title = switch (act) {
            case AWAKENING -> message("fight.messages.ritual-bossbar", "Das Ritual erwacht");
            case TRANSFORMATIONS -> message("fight.messages.ritual-totem-bossbar", "Totem %current%/%total%")
                    .replace("%current%", Integer.toString(Math.min(ritual.nodeCount(), ritual.activatedNodes() + 1)))
                    .replace("%total%", Integer.toString(ritual.nodeCount()));
            case CONVERGENCE -> message("fight.messages.ritual-convergence-bossbar", "Die Energien vereinen sich");
            case FINALE, COMPLETE -> message("fight.messages.ritual-finale-bossbar", "Der Drache erwacht");
        };
        bossBar.setTitle(title);
        for (Player player : world.getPlayers()) {
            bossBar.addPlayer(player);
        }
    }

    private void faceRitualEvokers(FirstEndRitualSession ritual, World world) {
        Location target = center(world).add(0.0D, 1.0D, 0.0D);
        for (UUID evokerId : ritual.evokerIds()) {
            Entity entity = Bukkit.getEntity(evokerId);
            if (entity instanceof Evoker evoker && evoker.isValid()) {
                orientEvoker(evoker, target);
            }
        }
    }

    private void orientEvoker(Evoker evoker, Location target) {
        Location oriented = evoker.getLocation();
        oriented.setDirection(ritualFacingDirection(oriented, target));
        evoker.teleport(oriented);
    }

    static Vector ritualFacingDirection(Location from, Location target) {
        Vector direction = target.toVector().subtract(from.toVector());
        return direction.lengthSquared() <= 0.0001D ? new Vector(0.0D, 0.0D, 1.0D) : direction.normalize();
    }

    private Location ritualFocus(World world) {
        return center(world).add(0.0D, 18.0D, 0.0D);
    }

    private void showRitualTitle(World world, String title, String subtitle) {
        Title rendered = Title.title(
                Component.text(title),
                Component.text(subtitle),
                Title.Times.times(Duration.ofMillis(300L), Duration.ofSeconds(2L), Duration.ofMillis(700L))
        );
        for (Player player : world.getPlayers()) {
            player.showTitle(rendered);
        }
    }

    private void activateRitualNode(
            EndFightSession session,
            FirstEndRitualSession ritual,
            World world,
            PillarRitualNode node
    ) {
        Location location = node.pillarTop();
        ritual.evoker(node.id())
                .map(Bukkit::getEntity)
                .filter(Evoker.class::isInstance)
                .map(Evoker.class::cast)
                .ifPresent(evoker -> {
                    evoker.setSpell(Spellcaster.Spell.SUMMON_VEX);
                    Location evokerCenter = evoker.getLocation().add(0.0D, 1.0D, 0.0D);
                    ParticleEffects.spawnForcedHelix(
                            world,
                            evoker.getLocation(),
                            1.4D,
                            3.5D,
                            ritual.elapsedTicks() / 5.0D,
                            Particle.REVERSE_PORTAL
                    );
                    ParticleEffects.spawnForcedDust(
                            world,
                            evokerCenter,
                            Color.fromRGB(175, 35, 235),
                            2.2F,
                            22,
                            0.7D,
                            1.1D,
                            0.7D
                    );
                    evoker.remove();
                });
        Location impact = location.clone().add(0.0D, 1.0D, 0.0D);
        ParticleEffects.spawnForcedBeam(world, center(world).add(0.0D, 1.2D, 0.0D), impact, Particle.END_ROD, 36);
        ParticleEffects.spawnForcedOrbit(world, impact, 2.0D, 0.0D, 0.0D, Particle.END_ROD, 42);
        ParticleEffects.spawnForcedOrbit(world, impact, 3.5D, Math.PI / 10.0D, 0.15D, Particle.REVERSE_PORTAL, 52);
        world.strikeLightningEffect(impact);
        world.playSound(location, Sound.ENTITY_EVOKER_CAST_SPELL, 1.8F, 0.7F);
        world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2F, 1.35F);

        EnderCrystal crystal = world.spawn(location, EnderCrystal.class, spawned -> {
            spawned.setShowingBottom(false);
            spawned.getPersistentDataContainer().set(ritualCrystalKey, PersistentDataType.BYTE, (byte) 1);
        });
        node.bindCrystal(crystal.getUniqueId(), false);
        node.setHealth(nodeHealth(session, config.crystalHealth(session.activeParticipantCount(config))));
    }

    private void completeFirstRitual(
            EndFightSession session,
            FirstEndRitualSession ritual,
            World world,
            EnderDragon dragon
    ) {
        while (ritual.activatedNodes() < ritual.nodeCount()) {
            ritual.nextNodeId()
                    .flatMap(session::node)
                    .ifPresent(node -> activateRitualNode(session, ritual, world, node));
            ritual.markActivated();
        }
        removeRitualEvokers(ritual);
        for (PillarRitualNode node : session.nodes()) {
            node.crystalUniqueId()
                    .map(Bukkit::getEntity)
                    .ifPresent(entity -> entity.getPersistentDataContainer().remove(ritualCrystalKey));
        }
        ritualsByWorld.remove(world.getName());
        stopRitualAnimationTaskIfIdle();
        dragonEggService.completeFirstEndRitual(world.getName());
        session.setActivationState(FightActivationState.ACTIVE);
        dragon.setInvisible(false);
        dragon.setInvulnerable(false);
        dragon.setSilent(false);
        for (Player player : world.getPlayers()) {
            player.showEntity(plugin, dragon);
        }
        dragon.teleport(center(world).add(0.0D, 35.0D, 0.0D));
        setDragonPhase(dragon, EnderDragon.Phase.CIRCLING);
        Location finaleCenter = center(world).add(0.0D, 1.0D, 0.0D);
        ParticleEffects.spawnForcedOrbit(world, finaleCenter, 7.0D, 0.0D, 0.15D, Particle.END_ROD, 70);
        ParticleEffects.spawnForcedOrbit(world, finaleCenter, 15.0D, 0.0D, 0.25D, Particle.REVERSE_PORTAL, 100);
        ParticleEffects.spawnForcedOrbit(world, finaleCenter, 24.0D, 0.0D, 0.35D, Particle.DRAGON_BREATH, 130);
        ParticleEffects.spawnForcedDust(
                world,
                ritualFocus(world),
                Color.fromRGB(120, 10, 180),
                3.0F,
                90,
                5.0D,
                7.0D,
                5.0D
        );
        world.strikeLightningEffect(finaleCenter);
        world.playSound(center(world), Sound.ENTITY_ENDER_DRAGON_GROWL, 5.0F, 0.6F);
        announce(world, message("fight.messages.ritual-complete", "Der Drache ist erwacht."));
        activateFight(session, dragon);
    }

    private void activateFight(EndFightSession session, EnderDragon dragon) {
        session.setActivationState(FightActivationState.ACTIVE);
        unlockNodesForPhase(session, FightPhase.OPENING, dragon.getWorld());
        syncCrystalIndicators(session, dragon.getWorld());
        session.setNextArenaEventTick(settings.cooldown(session.fightProfile(), config.eventBaseCooldownTicks()));
        session.setNextDragonAttackTick(settings.cooldown(session.fightProfile(), config.attackBaseCooldownTicks()));
        session.beginGuardianPhase(FightPhase.OPENING);
        applyDragonHealth(dragon, session);
        updatePhase(dragon, session);
        syncBossBar(dragon, session);
    }

    private void makeDragonDormant(EnderDragon dragon) {
        dragon.setInvulnerable(true);
        dragon.setSilent(true);
        dragon.setInvisible(true);
        dragon.setVelocity(new Vector());
        setDragonPhase(dragon, EnderDragon.Phase.HOVER);
        for (Player player : dragon.getWorld().getPlayers()) {
            player.hideEntity(plugin, dragon);
        }
    }

    public void markActive(Player player) {
        if (player != null && player.getWorld().getEnvironment() == World.Environment.THE_END) {
            ensureFirstRitualStarted(player.getWorld());
        }
        session(player.getWorld().getName()).ifPresent(session -> {
            if (nearArena(player.getLocation())) {
                session.markActive(player.getUniqueId());
            }
        });
    }

    public void handlePlayerTeleport(Player player, Location from, Location to) {
        if (to != null && nearArena(to)) {
            markActive(player);
        }
        accelerateTargetedDragonAttack(player, from);
        accelerateTargetedDragonAttack(player, to);
        if (to != null && nearActiveCrystal(to, 18.0D)) {
            session(to.getWorld().getName()).ifPresent(session -> {
                session.markGuardianFocus(player.getUniqueId(), config.crystalSpamGuardianFocusTicks());
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) config.guardians().pearlSnareSlownessTicks(), 1));
            });
        }
    }

    private void accelerateTargetedDragonAttack(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        EndFightSession session = sessionsByWorld.get(location.getWorld().getName());
        if (session == null) {
            return;
        }
        session.dragonAttackIntent()
                .filter(intent -> intent.targetUniqueId().equals(player.getUniqueId()))
                .ifPresent(intent -> session.replaceDragonAttackIntent(intent.withNextImpactTick(session.tick())));
    }

    public double dragonDamage(EnderDragon dragon, double damage) {
        Optional<EndFightSession> session = sessionForDragon(dragon);
        if (session.isEmpty()) {
            return damage;
        }
        if (!session.get().active()) {
            return 0.0D;
        }
        updatePhase(dragon, session.get());
        return damageCalculator.dragonDamage(damage, session.get(), config);
    }

    public double cappedExplosionDamage(double damage) {
        return damageCalculator.cappedExplosionDamage(damage, config);
    }

    public boolean isLinkedCrystal(EnderCrystal crystal) {
        if (crystalsBeingDestroyed.contains(crystal.getUniqueId())) {
            return false;
        }
        EndFightSession session = sessionsByWorld.get(crystal.getWorld().getName());
        return session != null && session.nodeByCrystal(crystal.getUniqueId()).filter(PillarRitualNode::active).isPresent();
    }

    public boolean isSpecialFightMob(Entity entity) {
        return entity != null && (
                entity.getPersistentDataContainer().has(guardianKey, PersistentDataType.BYTE)
                        || entity.getPersistentDataContainer().has(ritualEvokerKey, PersistentDataType.BYTE)
        );
    }

    public boolean damageCrystalNode(EnderCrystal crystal, Player player, double damage, Location sourceLocation) {
        EndFightSession session = sessionsByWorld.get(crystal.getWorld().getName());
        if (session == null) {
            return false;
        }
        if (player != null) {
            session.markActive(player.getUniqueId());
        }

        Optional<PillarRitualNode> node = session.nodeByCrystal(crystal.getUniqueId()).filter(PillarRitualNode::active);
        if (node.isEmpty()) {
            return false;
        }

        if (node.get().locked()) {
            playLockedFeedback(crystal, player, node.get());
            return true;
        }

        if (!session.markCrystalHitAllowed(
                crystal.getUniqueId(),
                player == null ? null : player.getUniqueId(),
                config.crystalPlayerHitCooldownTicks(),
                config.crystalGlobalHitCooldownTicks()
        )) {
            if (player != null) {
                session.markGuardianFocus(player.getUniqueId(), config.crystalSpamGuardianFocusTicks());
                focusGuardians(session, player);
            }
            playCrystalSpamFeedback(crystal, player);
            return true;
        }

        if (player != null) {
            session.markGuardianFocus(player.getUniqueId(), config.crystalSpamGuardianFocusTicks());
        }

        Location location = crystal.getLocation();
        double appliedDamage = session.crystalWarded(crystal.getUniqueId())
                ? damage * (1.0D - config.guardians().crystalWardReduction())
                : damage;
        boolean destroyed = node.get().damage(appliedDamage, session.tick());
        ParticleEffects.spawnDust(location.getWorld(), location, Color.FUCHSIA, 1.2F, 10, 0.35D, 0.35D, 0.35D);
        location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_HURT, 0.8F, 1.5F);
        if (sourceLocation != null) {
            ParticleEffects.spawnBeam(location.getWorld(), sourceLocation, location, Particle.END_ROD);
        }
        updateCrystalIndicator(node.get());
        applyCrystalThorns(session, node.get(), player, location);

        if (destroyed) {
            destroyCrystalNode(session, node.get(), crystal, location);
        }
        return true;
    }

    public boolean blockVanillaDragonHealing(EnderDragon dragon) {
        Optional<EndFightSession> session = sessionForDragon(dragon);
        if (session.isEmpty()) {
            return false;
        }
        double allowed = session.get().lastAllowedDragonHealth();
        if (allowed < 0.0D || dragon.getHealth() < allowed) {
            session.get().markAllowedDragonHealth(dragon.getHealth());
        }
        return true;
    }

    public void finishDragon(EnderDragon dragon) {
        sessionForDragon(dragon).ifPresent(session -> {
            cleanup(session);
            sessionsByWorld.remove(session.worldName());
            plugin.getLogger().info("Enhanced End fight finished in " + session.worldName() + ".");
        });
    }

    private void tick() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.THE_END) {
                continue;
            }
            for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                if (!dragon.isDead()) {
                    trackDragon(dragon);
                }
            }
        }

        for (EndFightSession session : List.copyOf(sessionsByWorld.values())) {
            session.setTick(session.tick() + 20L);
            World world = Bukkit.getWorld(session.worldName());
            if (world == null) {
                cleanup(session);
                sessionsByWorld.remove(session.worldName());
                continue;
            }

            Optional<EnderDragon> dragon = dragon(session);
            if (dragon.isEmpty() || dragon.get().isDead()) {
                cleanup(session);
                sessionsByWorld.remove(session.worldName());
                continue;
            }

            if (!session.active()) {
                makeDragonDormant(dragon.get());
                if (session.activationState() == FightActivationState.DORMANT) {
                    ensureFirstRitualStarted(world);
                    continue;
                }
                continue;
            }

            clampUnauthorizedDragonHealing(session, dragon.get());
            updatePhase(dragon.get(), session);
            deactivateMissingCrystals(session);
            updateHighGroundTracking(session, world);
            applyLifeSpireHealing(session, world, dragon.get());
            rememberAllowedDragonHealth(session, dragon.get());
            applyAnchorPull(session, world);
            showRitualAnimations(session, world, dragon.get());
            syncCrystalIndicators(session, world);
            syncBossBar(dragon.get(), session);
            maybeRunArenaEvent(session, world);
            maybeRunDragonAttack(session, world, dragon.get());
            maybeRunGuardians(session, world);
        }
    }

    private void startDirectAttackControlTask() {
        if (directAttackControlTask != null) {
            directAttackControlTask.cancel();
        }
        directAttackControlTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::controlDirectDragonAttacks,
                Math.max(1L, config.directAttackControlIntervalTicks()),
                Math.max(1L, config.directAttackControlIntervalTicks())
        );
    }

    private void startRitualAnimationTask() {
        if (ritualAnimationTask != null) {
            ritualAnimationTask.cancel();
            ritualAnimationTask = null;
        }
        if (ritualsByWorld.isEmpty()) {
            return;
        }
        long interval = settings.ritual().animationIntervalTicks();
        ritualAnimationTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tickRitualAnimations,
                interval,
                interval
        );
    }

    private void stopRitualAnimationTaskIfIdle() {
        if (!ritualsByWorld.isEmpty() || ritualAnimationTask == null) {
            return;
        }
        ritualAnimationTask.cancel();
        ritualAnimationTask = null;
    }

    private void tickRitualAnimations() {
        for (EndFightSession session : List.copyOf(sessionsByWorld.values())) {
            if (session.activationState() != FightActivationState.RITUAL) {
                continue;
            }
            World world = Bukkit.getWorld(session.worldName());
            Optional<EnderDragon> dragon = dragon(session);
            if (world == null || dragon.isEmpty() || dragon.get().isDead()) {
                continue;
            }
            makeDragonDormant(dragon.get());
            tickFirstRitual(session, world, dragon.get());
        }
    }

    private void controlDirectDragonAttacks() {
        for (EndFightSession session : List.copyOf(sessionsByWorld.values())) {
            World world = Bukkit.getWorld(session.worldName());
            if (world == null) {
                continue;
            }
            Optional<DragonAttackIntent> intent = session.dragonAttackIntent();
            Optional<EnderDragon> dragon = dragon(session);
            if (intent.isEmpty() || dragon.isEmpty() || dragon.get().isDead()) {
                session.clearDragonAttackStall();
                continue;
            }
            Player target = targetForIntent(intent.get(), world)
                    .orElseGet(() -> selectDragonAttackTarget(session, world, dragon.get(), intent.get().type(), activePlayers(world)).orElse(null));
            if (target == null) {
                continue;
            }
            engageDragonAttack(dragon.get(), target, intent.get().type());
            double distance = dragon.get().getLocation().distance(target.getLocation());
            session.recordDragonAttackProgress(
                    dragon.get().getLocation(),
                    distance,
                    config.directAttackStallDistance(),
                    config.directAttackControlIntervalTicks()
            );
            long stalledTicks = session.dragonAttackStalledTicks();
            if (stalledTicks >= config.directAttackStallRepositionTicks()) {
                repositionDragonForAttack(dragon.get(), target);
                session.clearDragonAttackStall();
            } else if (stalledTicks >= config.directAttackStallResetTicks()) {
                setDragonPhase(dragon.get(), EnderDragon.Phase.CIRCLING);
                resetDragonPhase(dragon.get(), phaseForIntent(intent.get().type()), 2L);
            }
        }
    }

    private void repositionDragonForAttack(EnderDragon dragon, Player target) {
        if (!validDelayedEntity(dragon) || target == null || !target.isValid()) {
            return;
        }
        Vector offset = dragon.getLocation().toVector().subtract(target.getLocation().toVector());
        if (offset.lengthSquared() <= 0.001D) {
            offset = new Vector(1.0D, 0.0D, 0.0D);
        }
        offset.setY(0.0D);
        if (offset.lengthSquared() <= 0.001D) {
            offset = new Vector(1.0D, 0.0D, 0.0D);
        }
        Location approach = target.getLocation().clone().add(offset.normalize().multiply(18.0D));
        approach.setY(Math.min(target.getWorld().getMaxHeight() - 8.0D, target.getLocation().getY() + 10.0D));
        dragon.teleport(approach);
        chaseDragonTarget(dragon, target);
    }

    private void applyDragonHealth(EnderDragon dragon, EndFightSession session) {
        if (session.dragonHealthApplied() || config.dragonMaxHealth() <= 0.0D) {
            return;
        }
        double maxHealth = settings.dragonHealth(session.fightProfile(), config.dragonMaxHealth());
        dragon.setMaxHealth(maxHealth);
        dragon.setHealth(Math.min(maxHealth, dragon.getMaxHealth()));
        session.markDragonHealthApplied();
        session.markAllowedDragonHealth(dragon.getHealth());
    }

    private void clampUnauthorizedDragonHealing(EndFightSession session, EnderDragon dragon) {
        double allowed = session.lastAllowedDragonHealth();
        if (allowed < 0.0D) {
            session.markAllowedDragonHealth(dragon.getHealth());
            return;
        }

        double current = dragon.getHealth();
        if (current > allowed + 0.001D) {
            dragon.setHealth(Math.min(allowed, dragon.getMaxHealth()));
            plugin.getLogger().fine("Blocked unauthorized Ender Dragon healing in " + session.worldName() + ".");
            return;
        }

        if (current < allowed) {
            session.markAllowedDragonHealth(current);
        }
    }

    private void rememberAllowedDragonHealth(EndFightSession session, EnderDragon dragon) {
        session.markAllowedDragonHealth(dragon.getHealth());
    }

    private void recordCrystalLayout(String worldName, EndFightSession session) {
        List<String> positions = session.nodes().stream()
                .filter(PillarRitualNode::active)
                .map(node -> {
                    Location top = node.pillarTop();
                    return top.getBlockX() + "," + top.getBlockY() + "," + top.getBlockZ();
                })
                .toList();
        if (dragonEggService.recordCrystalLayout(worldName, positions)) {
            plugin.getLogger().info("Stored first End crystal layout with " + positions.size() + " pillar positions.");
        }
    }

    private void updatePhase(EnderDragon dragon, EndFightSession session) {
        double maxHealth = dragon.getMaxHealth();
        FightPhase previous = session.phase();
        FightPhase phase = session.updatePhase(dragon.getHealth(), maxHealth);
        if (phase != previous) {
            session.beginGuardianPhase(phase);
        }

        if (phase == FightPhase.TOTEMS && session.markPhaseSpawned(FightPhase.TOTEMS)) {
            unlockNodesForPhase(session, phase, dragon.getWorld());
            announce(dragon.getWorld(), message("fight.messages.phase-totems", "Die Endkristalle binden ihre Macht an den Drachen."));
        }
        if (phase == FightPhase.ESCALATION && session.markPhaseSpawned(FightPhase.ESCALATION)) {
            unlockNodesForPhase(session, phase, dragon.getWorld());
            announce(dragon.getWorld(), message("fight.messages.phase-escalation", "Das Ende wird instabil."));
        }
        if (phase == FightPhase.FINAL && session.markPhaseSpawned(FightPhase.FINAL)) {
            unlockNodesForPhase(session, FightPhase.FINAL, dragon.getWorld());
            promoteFinalAnchors(session);
            while (session.finalAnchorCount() < 2) {
                if (!spawnTemporaryFinalAnchor(session, dragon.getWorld())) {
                    break;
                }
            }
            session.setNextArenaEventTick(Math.min(session.nextArenaEventTick(), session.tick() + config.eventFinalCooldownTicks()));
            announce(dragon.getWorld(), message("fight.messages.phase-final", "Finale Anker binden den Drachen."));
            dragon.getWorld().playSound(center(dragon.getWorld()), Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0F, 0.6F);
        } else if (phase != previous) {
            syncBossBar(dragon, session);
        }
    }

    private void unlockNodesForPhase(EndFightSession session, FightPhase phase, World world) {
        for (PillarRitualNode node : session.nodes()) {
            if (node.unlockFor(phase)) {
                ParticleEffects.spawnShockwave(world, node.pillarTop(), 3.0D, Particle.END_ROD);
                world.playSound(node.pillarTop(), Sound.BLOCK_BEACON_ACTIVATE, 1.5F, 1.2F);
                updateCrystalIndicator(node);
            }
        }
    }

    private void playLockedFeedback(EnderCrystal crystal, Player player, PillarRitualNode node) {
        Location location = crystal.getLocation();
        World world = location.getWorld();
        if (world != null) {
            ParticleEffects.spawnShockwave(world, location, 1.8D, Particle.REVERSE_PORTAL);
            world.playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 0.9F, 1.7F);
        }
        if (player != null) {
            String message = plugin.getConfig().getString("fight.totems.locked.actionbar-message", "Dieses Totem ist noch versiegelt.");
            player.sendActionBar(Component.text(message + " (" + phaseName(node.requiredPhase()) + ")"));
        }
    }

    private void playCrystalSpamFeedback(EnderCrystal crystal, Player player) {
        Location location = crystal.getLocation();
        World world = location.getWorld();
        if (world != null) {
            ParticleEffects.spawnShockwave(world, location, 1.5D, Particle.REVERSE_PORTAL);
            world.playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8F, 1.4F);
        }
        if (player != null) {
            player.sendActionBar(Component.text("Der Kristall widersteht dem schnellen Schlag."));
        }
    }

    private void applyCrystalThorns(EndFightSession session, PillarRitualNode node, Player player, Location location) {
        if (player == null || node.type() != RitualNodeType.CRYSTAL_THORNS) {
            return;
        }
        if (!session.markThornsTriggered(node.id(), player.getUniqueId(), config.thornsCooldownTicks())) {
            return;
        }
        ParticleEffects.spawnFloat(location.getWorld(), Particle.DRAGON_BREATH, location, config.thornsBurstParticles(), 1.1D, 0.7D, 1.1D, 0.02D, 1.0F);
        ParticleEffects.spawnShockwave(location.getWorld(), location, 2.2D, Particle.DAMAGE_INDICATOR);
        location.getWorld().playSound(location, Sound.ENCHANT_THORNS_HIT, 1.2F, 0.7F);
        player.damage(fightDamage(session, config.thornsDamage()));
        Vector push = player.getLocation().toVector().subtract(location.toVector());
        if (push.lengthSquared() > 0.001D) {
            player.setVelocity(player.getVelocity().add(push.normalize().multiply(config.thornsKnockback()).setY(0.25D)));
        }
    }

    private void applyLifeSpireHealing(EndFightSession session, World world, EnderDragon dragon) {
        int lifeSpires = session.activeNodeCount(RitualNodeType.LIFE_SPIRE);
        if (lifeSpires <= 0 || activePlayers(world).isEmpty() || session.tick() % config.lifeSpireHealIntervalTicks() != 0L) {
            return;
        }

        double multiplier = session.finalPhaseActive() ? config.lifeSpireFinalPhaseMultiplier() : 1.0D;
        double intervalSeconds = config.lifeSpireHealIntervalTicks() / 20.0D;
        double heal = Math.min(
                config.lifeSpireHealAmount() * lifeSpires * multiplier,
                config.lifeSpireMaxHealPerSecond() * intervalSeconds
        );
        if (heal <= 0.0D || dragon.getHealth() >= dragon.getMaxHealth()) {
            return;
        }

        dragon.setHealth(Math.min(dragon.getMaxHealth(), dragon.getHealth() + heal));
        session.markAllowedDragonHealth(dragon.getHealth());
        for (PillarRitualNode node : session.nodes()) {
            if (node.unlocked() && node.type() == RitualNodeType.LIFE_SPIRE) {
                ParticleEffects.spawnBeam(world, node.pillarTop(), dragon.getLocation().add(0.0D, 2.0D, 0.0D), Particle.HAPPY_VILLAGER);
            }
        }
        world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 1.0F, 1.4F);
    }

    private void deactivateNode(EndFightSession session, PillarRitualNode node, Location location) {
        RitualNodeType type = node.type();
        removeIndicator(node);
        node.deactivate();
        if (location.getWorld() != null) {
            ParticleEffects.spawnShockwave(location.getWorld(), location, 2.5D, Particle.END_ROD);
            ParticleEffects.spawnShockwave(location.getWorld(), location, 4.0D, Particle.PORTAL);
            ParticleEffects.spawnDust(location.getWorld(), location, Color.PURPLE, 1.6F, 24, 1.5D, 0.8D, 1.5D);
            location.getWorld().playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 2.0F, 0.7F);
            announce(location.getWorld(), message("fight.messages.node-fallen", "%node% ist gefallen.")
                    .replace("%node%", nodeName(type)));
            buffNearbyParticipants(session, location);
        }

        if (session.finalPhaseActive() && session.finalAnchorCount() == 0 && !session.exposed() && location.getWorld() != null) {
            session.startExposedWindow(config.exposedTicks());
            announce(location.getWorld(), message("fight.messages.dragon-exposed", "Der Drache ist verwundbar."));
            collapseAnimation(location.getWorld());
            buffActiveParticipants(session, location.getWorld(), PotionEffectType.STRENGTH, 10 * 20, 0);
        }
    }

    private void destroyCrystalNode(EndFightSession session, PillarRitualNode node, EnderCrystal crystal, Location location) {
        if (!crystalsBeingDestroyed.add(crystal.getUniqueId())) {
            return;
        }

        try {
            World world = location.getWorld();
            crystal.remove();
            deactivateNode(session, node, location);
            if (world != null) {
                world.createExplosion(location, 6.0F, false, false);
            }
        } finally {
            crystalsBeingDestroyed.remove(crystal.getUniqueId());
        }
    }

    private void promoteFinalAnchors(EndFightSession session) {
        for (PillarRitualNode node : session.nodes()) {
            if (node.active()) {
                node.assignType(RitualNodeType.FINAL_ANCHOR);
                updateCrystalIndicator(node);
            }
        }
    }

    private boolean spawnTemporaryFinalAnchor(EndFightSession session, World world) {
        for (PillarRitualNode node : session.nodes()) {
            if (node.active()) {
                continue;
            }
            Location spawnLocation = node.pillarTop().add(0.0D, 1.0D, 0.0D);
            EnderCrystal crystal = world.spawn(spawnLocation, EnderCrystal.class, spawned -> {
                spawned.setShowingBottom(false);
                spawned.getPersistentDataContainer().set(temporaryCrystalKey, PersistentDataType.BYTE, (byte) 1);
            });
            node.bindCrystal(crystal.getUniqueId(), true);
            node.setHealth(nodeHealth(session, config.crystalHealth(session.activeParticipantCount(config))));
            node.assignType(RitualNodeType.FINAL_ANCHOR);
            ParticleEffects.spawnBeam(world, node.pillarTop(), crystal.getLocation(), Particle.END_ROD);
            syncCrystalIndicator(node, world);
            return true;
        }
        return false;
    }

    private void maybeRunArenaEvent(EndFightSession session, World world) {
        if (session.tick() < session.nextArenaEventTick() || activePlayers(world).isEmpty()) {
            return;
        }

        ArenaEventType type = chooseEvent(session);
        if (type == ArenaEventType.BREATH_STORM && hasPillarCamper(session)) {
            type = ArenaEventType.BREATH_STORM;
        }
        runArenaEvent(session, world, type);
        session.setNextArenaEventTick(session.tick() + nextCooldown(session));
    }

    private void maybeRunDragonAttack(EndFightSession session, World world, EnderDragon dragon) {
        if (processDragonAttackIntent(session, world, dragon)) {
            return;
        }
        List<Player> players = activePlayers(world);
        attackScheduler.nextAttack(session, config, settings, players.size())
                .ifPresent(type -> runDragonAttack(session, world, dragon, players, type));
    }

    private void runDragonAttack(EndFightSession session, World world, EnderDragon dragon, List<Player> players, DragonAttackType type) {
        if (players.isEmpty()) {
            return;
        }
        if (directDragonAttack(type)) {
            beginDragonAttackIntent(session, world, dragon, players, type);
            return;
        }
        switch (type) {
            case PILLAR_BEAM -> runPillarBeam(session, world, players.get(0));
            case ENDER_VOLLEY -> runEnderVolley(session, world, dragon, players);
            case VOID_RIFT -> runVoidRift(world);
            case VOID_GRASP -> runVoidGrasp(session, world);
            case PERCH_RITUAL -> runPerchRitual(session, world, dragon);
            default -> {
            }
        }
    }

    private void maybeRunGuardians(EndFightSession session, World world) {
        EndFightConfig.GuardianConfig guardians = config.guardians();
        cleanupMissingGuardians(session);
        if (!guardians.enabled()) {
            removeGuardians(session);
            return;
        }

        runGuardianAttacks(session, world);
        showGuardianAuras(session, world);
        List<Player> players = activePlayers(world);
        int maxGuardians = guardians.maxForPlayers(players.size());
        int phaseBudget = settings.guardianBudget(session.fightProfile(), session.phase(), players.size());
        if (maxGuardians <= 0
                || session.guardianUniqueIds().size() >= maxGuardians
                || session.guardianBudgetConsumed(session.phase()) >= phaseBudget
                || session.tick() < session.nextGuardianSpawnTick()) {
            return;
        }

        chooseGuardianKind(session).ifPresent(kind -> spawnGuardian(session, world, kind, players));
        session.setNextGuardianSpawnTick(session.tick() + guardians.spawnIntervalTicks());
    }

    private Optional<GuardianKind> chooseGuardianKind(EndFightSession session) {
        int endermanWeight;
        int shulkerWeight;
        switch (session.phase()) {
            case OPENING -> {
                endermanWeight = config.guardians().openingEndermanWeight();
                shulkerWeight = config.guardians().openingShulkerWeight();
            }
            case TOTEMS -> {
                endermanWeight = config.guardians().totemsEndermanWeight();
                shulkerWeight = config.guardians().totemsShulkerWeight();
            }
            case ESCALATION -> {
                endermanWeight = config.guardians().escalationEndermanWeight();
                shulkerWeight = config.guardians().escalationShulkerWeight();
            }
            case FINAL -> {
                endermanWeight = config.guardians().finalEndermanWeight();
                shulkerWeight = config.guardians().finalShulkerWeight();
            }
            default -> {
                endermanWeight = 0;
                shulkerWeight = 0;
            }
        }
        int total = endermanWeight + shulkerWeight;
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = (int) ((session.tick() / 20L) % total);
        return Optional.of(roll < endermanWeight ? GuardianKind.ENDERMAN : GuardianKind.SHULKER);
    }

    private void spawnGuardian(EndFightSession session, World world, GuardianKind kind, List<Player> players) {
        Location location = guardianSpawnLocation(session, world, kind, players);
        if (location == null) {
            return;
        }
        LivingEntity guardian = switch (kind) {
            case ENDERMAN -> world.spawn(location, Enderman.class, spawned -> prepareGuardian(spawned, "Void Interceptor"));
            case SHULKER -> world.spawn(location, Shulker.class, spawned -> prepareGuardian(spawned, "Shulker Sentry"));
        };
        session.markGuardianSpawned(guardian.getUniqueId());
        guardianTarget(session, world).ifPresent(target -> {
            if (guardian instanceof Mob mob) {
                mob.setTarget(target);
            }
        });
    }

    private void prepareGuardian(LivingEntity entity, String name) {
        entity.getPersistentDataContainer().set(guardianKey, PersistentDataType.BYTE, (byte) 1);
        entity.setPersistent(false);
        entity.setRemoveWhenFarAway(false);
        entity.customName(Component.text(name));
        entity.setCustomNameVisible(false);
        if (entity.getAttribute(Attribute.GENERIC_SCALE) != null) {
            entity.getAttribute(Attribute.GENERIC_SCALE).setBaseValue(settings.guardianVisual().scale());
        }
    }

    private void showGuardianAuras(EndFightSession session, World world) {
        if (session.tick() % settings.guardianVisual().particleIntervalTicks() != 0L) {
            return;
        }
        for (UUID guardianUniqueId : session.guardianUniqueIds()) {
            Entity entity = Bukkit.getEntity(guardianUniqueId);
            if (!(entity instanceof LivingEntity guardian) || !guardian.isValid() || guardian.getWorld() != world) {
                continue;
            }
            ParticleEffects.spawnSimple(world, Particle.REVERSE_PORTAL, guardian.getLocation().add(0.0D, 1.0D, 0.0D), 5, 0.45D, 0.8D, 0.45D, 0.01D);
            ParticleEffects.spawnDust(world, guardian.getLocation().add(0.0D, 0.8D, 0.0D), Color.PURPLE, 1.0F, 3, 0.35D, 0.6D, 0.35D);
        }
    }

    private Location guardianSpawnLocation(EndFightSession session, World world, GuardianKind kind, List<Player> players) {
        Optional<Player> focus = guardianTarget(session, world);
        if (kind == GuardianKind.ENDERMAN && focus.isPresent()) {
            Location base = focus.get().getLocation();
            return base.clone().add(6.0D, 0.0D, 0.0D);
        }
        Optional<Location> nodeLocation = session.nodes().stream()
                .filter(PillarRitualNode::active)
                .findFirst()
                .map(PillarRitualNode::pillarTop);
        if (nodeLocation.isPresent()) {
            return nodeLocation.get().clone().add(0.0D, kind == GuardianKind.SHULKER ? 1.0D : 0.0D, 0.0D);
        }
        if (!players.isEmpty()) {
            return players.get(0).getLocation().clone().add(8.0D, 0.0D, 0.0D);
        }
        return center(world);
    }

    private void runGuardianAttacks(EndFightSession session, World world) {
        Optional<Player> target = guardianTarget(session, world);
        if (target.isEmpty()) {
            return;
        }
        for (UUID guardianUniqueId : List.copyOf(session.guardianUniqueIds())) {
            Entity entity = Bukkit.getEntity(guardianUniqueId);
            if (!(entity instanceof LivingEntity guardian) || !guardian.isValid() || guardian.getWorld() != world) {
                session.removeGuardian(guardianUniqueId);
                continue;
            }
            if (guardian instanceof Mob mob) {
                mob.setTarget(target.get());
            }
            if (guardian instanceof Enderman && session.markGuardianAttack(guardianUniqueId, config.guardians().endermanAttackCooldownTicks())) {
                runVoidIntercept(session, guardian, target.get());
            } else if (guardian instanceof Shulker && session.markGuardianAttack(guardianUniqueId, config.guardians().shulkerAttackCooldownTicks())) {
                runAnchorShot(session, world, guardian, target.get());
            }
        }
    }

    private Optional<Player> guardianTarget(EndFightSession session, World world) {
        Optional<Player> focused = session.focusedPlayer()
                .map(Bukkit::getPlayer)
                .filter(player -> player.isOnline() && player.getWorld() == world && activePlayers(world).contains(player));
        if (focused.isPresent()) {
            return focused;
        }
        return activePlayers(world).stream()
                .filter(player -> nearActiveCrystal(player.getLocation(), 24.0D))
                .findFirst()
                .or(() -> activePlayers(world).stream().findFirst());
    }

    private void runVoidIntercept(EndFightSession session, LivingEntity guardian, Player target) {
        Location targetLocation = target.getLocation();
        Location intercept = targetLocation.clone().add(targetLocation.getDirection().normalize().multiply(-2.5D));
        guardian.teleport(intercept);
        ParticleEffects.spawnShockwave(target.getWorld(), intercept, 2.0D, Particle.REVERSE_PORTAL);
        target.damage(fightDamage(session, config.guardians().endermanVoidInterceptDamage()), guardian);
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, (int) config.guardians().endermanWeaknessTicks(), 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) config.guardians().endermanSlownessTicks(), 0));
        Vector push = target.getLocation().toVector().subtract(guardian.getLocation().toVector());
        if (push.lengthSquared() > 0.001D) {
            target.setVelocity(push.normalize().multiply(0.9D).setY(0.35D));
        }
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5F, 0.65F);
    }

    private void runAnchorShot(EndFightSession session, World world, LivingEntity guardian, Player target) {
        Location from = guardian.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        Location to = target.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        ParticleEffects.spawnBeam(world, from, to, Particle.END_ROD);
        ParticleEffects.spawnShockwave(world, target.getLocation(), 2.0D, Particle.END_ROD);
        target.damage(fightDamage(session, config.guardians().shulkerAnchorShotDamage()), guardian);
        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, (int) config.guardians().shulkerLevitationTicks(), 0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (validDelayedEntity(target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, (int) config.guardians().shulkerSlowFallingTicks(), 0));
            }
        }, config.guardians().shulkerLevitationTicks());
        nearestActiveCrystal(target.getLocation(), 32.0D).ifPresent(crystal -> {
            session.startCrystalWard(crystal.getUniqueId(), config.guardians().crystalWardTicks());
            ParticleEffects.spawnShockwave(crystal.getWorld(), crystal.getLocation(), 2.2D, Particle.PORTAL);
            ParticleEffects.spawnHelix(crystal.getWorld(), crystal.getLocation(), 1.3D, 2.8D, session.tick() / 8.0D, Particle.END_ROD);
        });
    }

    private void cleanupMissingGuardians(EndFightSession session) {
        for (UUID guardianUniqueId : List.copyOf(session.guardianUniqueIds())) {
            Entity entity = Bukkit.getEntity(guardianUniqueId);
            if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.isValid()) {
                session.removeGuardian(guardianUniqueId);
            }
        }
    }

    private void removeGuardians(EndFightSession session) {
        for (UUID guardianUniqueId : List.copyOf(session.guardianUniqueIds())) {
            Entity entity = Bukkit.getEntity(guardianUniqueId);
            if (entity != null) {
                entity.remove();
            }
            session.removeGuardian(guardianUniqueId);
        }
    }

    private void focusGuardians(EndFightSession session, Player player) {
        for (UUID guardianUniqueId : session.guardianUniqueIds()) {
            Entity entity = Bukkit.getEntity(guardianUniqueId);
            if (entity instanceof Mob mob && mob.isValid() && mob.getWorld() == player.getWorld()) {
                mob.setTarget(player);
            }
        }
    }

    private boolean processDragonAttackIntent(EndFightSession session, World world, EnderDragon dragon) {
        Optional<DragonAttackIntent> current = session.dragonAttackIntent();
        if (current.isEmpty()) {
            return false;
        }

        DragonAttackIntent intent = current.get();
        DragonAttackType intentType = intent.type();
        Optional<Player> currentTarget = targetForIntent(intent, world);
        Player target = currentTarget
                .filter(player -> closeEnoughForCurrentIntent(dragon, player, intentType))
                .orElse(null);
        if (target == null) {
            target = selectDragonAttackTarget(session, world, dragon, intent.type(), activePlayers(world)).orElse(null);
        }
        if (target == null) {
            session.clearDragonAttackIntent();
            return true;
        }

        if (!target.getUniqueId().equals(intent.targetUniqueId())) {
            intent = intent.withTarget(target.getUniqueId(), session.tick() + config.directAttackRetryTicks());
            session.replaceDragonAttackIntent(intent);
        }

        engageDragonAttack(dragon, target, intent.type());
        if (session.tick() < intent.nextImpactTick()) {
            return true;
        }

        if (dragonAttackInImpactRange(session, world, dragon, target, intent.type())) {
            session.consumeDragonAttackIntent();
            executeDragonAttackImpact(session, world, dragon, target, intent.type());
            session.markDragonAttack(intent.type(), session.tick() + attackScheduler.cooldown(session, config, settings));
            return true;
        }

        if (intent.timedOut(session.tick())) {
            session.consumeDragonAttackIntent();
            if (config.fallbackRitualOnTimeout()) {
                runVoidGrasp(session, world);
                session.markDragonAttack(DragonAttackType.VOID_GRASP, session.tick() + attackScheduler.cooldown(session, config, settings));
            } else {
                session.markDragonAttack(intent.type(), session.tick() + attackScheduler.cooldown(session, config, settings));
            }
            return true;
        }

        session.replaceDragonAttackIntent(intent.withNextImpactTick(session.tick() + config.directAttackRetryTicks()));
        return true;
    }

    private void beginDragonAttackIntent(EndFightSession session, World world, EnderDragon dragon, List<Player> players, DragonAttackType type) {
        Optional<Player> target = selectDragonAttackTarget(session, world, dragon, type, players);
        if (target.isEmpty()) {
            if (config.fallbackRitualOnTimeout()) {
                runVoidGrasp(session, world);
            }
            return;
        }

        session.startDragonAttackIntent(type, target.get().getUniqueId(), config.directAttackEngageTicks(), config.directAttackTimeoutTicks());
        engageDragonAttack(dragon, target.get(), type);
    }

    private void executeDragonAttackImpact(EndFightSession session, World world, EnderDragon dragon, Player target, DragonAttackType type) {
        switch (type) {
            case DRAGON_DIVE_MARK -> runDragonDive(world, dragon, target);
            case BREATH_STRAFE -> runBreathStrafe(session, world, dragon, target);
            case SKY_CRASH -> runSkyCrash(session, world, dragon, target);
            case WING_GUST -> runWingGust(world, dragon);
            case WING_RING -> runWingRing(session, world, dragon);
            case CRYSTAL_BARRAGE -> runCrystalBarrage(session, world, dragon);
            default -> runVoidGrasp(session, world);
        }
    }

    private void engageDragonAttack(EnderDragon dragon, Player target, DragonAttackType type) {
        if (!validDelayedEntity(dragon) || target == null || !target.isValid()) {
            return;
        }
        dragon.setTarget(target);
        dragon.setAggressive(true);
        dragon.lookAt(target);
        chaseDragonTarget(dragon, target);
        setDragonPhase(dragon, phaseForIntent(type));
    }

    private EnderDragon.Phase phaseForIntent(DragonAttackType type) {
        return switch (type) {
            case DRAGON_DIVE_MARK, SKY_CRASH -> EnderDragon.Phase.CHARGE_PLAYER;
            case BREATH_STRAFE, CRYSTAL_BARRAGE -> EnderDragon.Phase.STRAFING;
            case WING_GUST, WING_RING -> EnderDragon.Phase.ROAR_BEFORE_ATTACK;
            default -> EnderDragon.Phase.CIRCLING;
        };
    }

    private Optional<Player> targetForIntent(DragonAttackIntent intent, World world) {
        Entity entity = Bukkit.getEntity(intent.targetUniqueId());
        if (entity instanceof Player player && player.isValid() && activePlayers(world).contains(player)) {
            return Optional.of(player);
        }
        return Optional.empty();
    }

    private Optional<Player> selectDragonAttackTarget(EndFightSession session, World world, EnderDragon dragon, DragonAttackType type, List<Player> players) {
        if (dragon == null || !dragon.isValid()) {
            return Optional.empty();
        }
        List<Player> candidates = players.isEmpty() ? prioritizedBreathTargets(session, world) : new ArrayList<>(players);
        candidates.sort(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(dragon.getLocation())));
        for (Player player : candidates) {
            if (player.getLocation().distanceSquared(dragon.getLocation()) <= engageRange(type) * engageRange(type)) {
                return Optional.of(player);
            }
        }
        return candidates.stream().findFirst();
    }

    private boolean closeEnoughForCurrentIntent(EnderDragon dragon, Player target, DragonAttackType type) {
        if (dragon == null || !dragon.isValid() || target == null || !target.isValid()) {
            return false;
        }
        if (dragon.getWorld() != target.getWorld() || !nearArena(target.getLocation())) {
            return false;
        }
        double retargetRange = Math.max(impactRange(type), engageRange(type)) * 1.5D;
        return target.getLocation().distanceSquared(dragon.getLocation()) <= retargetRange * retargetRange;
    }

    private void chaseDragonTarget(EnderDragon dragon, Player target) {
        if (config.directAttackChaseSpeed() <= 0.0D) {
            return;
        }
        Vector direction = target.getLocation().toVector().subtract(dragon.getLocation().toVector());
        if (direction.lengthSquared() <= 0.001D) {
            return;
        }
        Vector velocity = direction.normalize().multiply(config.directAttackChaseSpeed());
        if (velocity.getY() < -0.65D) {
            velocity.setY(-0.65D);
        } else if (velocity.getY() > 0.85D) {
            velocity.setY(0.85D);
        }
        dragon.setVelocity(velocity);
    }

    private boolean dragonAttackInImpactRange(EndFightSession session, World world, EnderDragon dragon, Player target, DragonAttackType type) {
        if (type == DragonAttackType.WING_RING && session.finalPhaseActive()) {
            return target.getLocation().distanceSquared(center(world)) <= impactRange(type) * impactRange(type);
        }
        return target.getLocation().distanceSquared(dragon.getLocation()) <= impactRange(type) * impactRange(type);
    }

    private boolean directDragonAttack(DragonAttackType type) {
        return switch (type) {
            case DRAGON_DIVE_MARK, SKY_CRASH, BREATH_STRAFE, WING_GUST, WING_RING, CRYSTAL_BARRAGE -> true;
            default -> false;
        };
    }

    private double engageRange(DragonAttackType type) {
        return switch (type) {
            case DRAGON_DIVE_MARK -> config.diveEngageRange();
            case BREATH_STRAFE -> config.breathStrafeEngageRange();
            case SKY_CRASH -> config.skyCrashEngageRange();
            case WING_GUST -> config.wingGustEngageRange();
            case WING_RING -> config.wingRingEngageRange();
            case CRYSTAL_BARRAGE -> config.crystalBarrageEngageRange();
            default -> config.arenaRadius();
        };
    }

    private double impactRange(DragonAttackType type) {
        return switch (type) {
            case DRAGON_DIVE_MARK -> config.diveImpactRange();
            case BREATH_STRAFE -> config.breathStrafeImpactRange();
            case SKY_CRASH -> config.skyCrashImpactRange();
            case WING_GUST -> config.wingGustImpactRange();
            case WING_RING -> config.wingRingImpactRange();
            case CRYSTAL_BARRAGE -> config.crystalBarrageImpactRange();
            default -> config.arenaRadius();
        };
    }

    private void runDragonDive(World world, EnderDragon dragon, Player target) {
        setDragonPhase(dragon, EnderDragon.Phase.CHARGE_PLAYER);
        Location start = dragon.getLocation().clone();
        Location end = target.getLocation().clone();
        Vector direction = end.toVector().subtract(start.toVector());
        if (direction.lengthSquared() <= 0.001D) {
            return;
        }
        direction.normalize();
        List<Location> lane = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            Location point = start.clone().add(direction.clone().multiply(i * 2.0D));
            point.setY(target.getLocation().getY() + 0.2D);
            lane.add(point);
            ParticleEffects.spawnSimple(world, Particle.END_ROD, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        ParticleEffects.spawnBeam(world, start.add(0.0D, 1.0D, 0.0D), end.add(0.0D, 1.0D, 0.0D), Particle.PORTAL);
        world.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.5F, 1.25F);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!validDelayedEntity(dragon) || !validDelayedEntity(target) || !sessionsByWorld.containsKey(world.getName())) {
                return;
            }
            for (Location point : lane) {
                ParticleEffects.spawnSimple(world, Particle.CRIT, point, 2, 0.1D, 0.1D, 0.1D, 0.01D);
            }
            for (Player player : activePlayers(world)) {
                if (AoeGeometry.hitsLane(player.getLocation(), lane, config.diveLaneWidth(), config.laneAttackVerticalRange())) {
                    player.damage(fightDamage(world, config.diveDamage()));
                    player.setVelocity(direction.clone().multiply(config.diveKnockback()).setY(0.35D));
                }
            }
        }, config.attackWarningTicks());
    }

    private void runPillarBeam(EndFightSession session, World world, Player target) {
        Optional<PillarRitualNode> source = session.nodes().stream()
                .filter(PillarRitualNode::unlocked)
                .filter(node -> node.type() == RitualNodeType.PILLAR_BEAM || node.type() == RitualNodeType.FINAL_ANCHOR)
                .findFirst();
        if (source.isEmpty()) {
            return;
        }
        Location from = source.get().pillarTop();
        Location to = target.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        ParticleEffects.spawnBeam(world, from, to, Particle.PORTAL);
        warnCircle(target.getLocation().clone(), Particle.PORTAL);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!validDelayedLocation(to) || !validDelayedEntity(target)) {
                return;
            }
            ParticleEffects.spawnBeam(world, from, to, Particle.END_ROD);
            ParticleEffects.spawnShockwave(world, to, 3.0D, Particle.END_ROD);
            for (Player player : activePlayers(world)) {
                if (AoeGeometry.hitsPoint(player.getLocation(), to, 4.0D, config.pointAttackVerticalRange())) {
                    player.damage(fightDamage(session, config.pillarBeamDamage()));
                }
            }
        }, config.attackWarningTicks());
    }

    private void runEnderVolley(EndFightSession session, World world, EnderDragon dragon, List<Player> players) {
        setDragonPhase(dragon, EnderDragon.Phase.STRAFING);
        int count = Math.min(session.finalPhaseActive() ? config.enderVolleyMaxTargets() : Math.min(2, config.enderVolleyMaxTargets()), players.size());
        List<Location> sources = session.nodes().stream()
                .filter(PillarRitualNode::unlocked)
                .filter(node -> node.type() == RitualNodeType.ENDER_VOLLEY)
                .map(PillarRitualNode::pillarTop)
                .toList();
        for (int i = 0; i < count; i++) {
            Location target = players.get(i).getLocation().clone();
            Location source = sources.isEmpty()
                    ? dragon.getLocation().clone().add(0.0D, 2.0D, 0.0D)
                    : sources.get(i % sources.size());
            warnCircle(target, Particle.PORTAL);
            ParticleEffects.spawnBeam(world, source, target.clone().add(0.0D, 1.0D, 0.0D), Particle.PORTAL);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!validDelayedLocation(target) || !validDelayedEntity(dragon)) {
                    return;
                }
                ParticleEffects.spawnBeam(world, source, target.clone().add(0.0D, 1.0D, 0.0D), Particle.END_ROD);
                ParticleEffects.spawnShockwave(world, target, 3.5D, Particle.END_ROD);
                for (Player player : activePlayers(world)) {
                    if (AoeGeometry.hitsPoint(player.getLocation(), target, 4.0D, config.pointAttackVerticalRange())) {
                        player.damage(fightDamage(session, config.enderVolleyDamage()));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
                    }
                }
            }, config.attackWarningTicks());
        }
    }

    private void runWingGust(World world, EnderDragon dragon) {
        setDragonPhase(dragon, EnderDragon.Phase.ROAR_BEFORE_ATTACK);
        Location origin = dragon.getLocation().clone();
        Vector facing = origin.getDirection();
        if (facing.lengthSquared() <= 0.001D) {
            facing = center(world).toVector().subtract(origin.toVector());
        }
        facing.normalize();
        for (int i = 1; i <= 8; i++) {
            ParticleEffects.spawnShockwave(world, origin.clone().add(facing.clone().multiply(i * 3.0D)), 1.5D + i * 0.35D, Particle.CLOUD);
        }
        world.playSound(origin, Sound.ENTITY_ENDER_DRAGON_FLAP, 3.0F, 0.7F);
        Vector attackFacing = facing.clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!validDelayedLocation(origin) || !validDelayedEntity(dragon)) {
                return;
            }
            for (Player player : activePlayers(world)) {
                Vector toPlayer = player.getLocation().toVector().subtract(origin.toVector());
                if (toPlayer.lengthSquared() <= 0.001D) {
                    continue;
                }
                if (toPlayer.lengthSquared() <= config.wingGustRadius() * config.wingGustRadius()
                        && toPlayer.clone().normalize().dot(attackFacing) >= -0.2D) {
                    Vector push = toPlayer.clone().normalize();
                    push.setY(0.5D);
                    if (config.wingGustDamage() > 0.0D) {
                        player.damage(fightDamage(world, config.wingGustDamage()));
                    }
                    player.setVelocity(push.multiply(config.wingGustKnockback()));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
                }
            }
        }, config.attackWarningTicks());
    }

    private void runBreathStrafe(EndFightSession session, World world, EnderDragon dragon, Player target) {
        if (target == null || !target.isValid()) {
            return;
        }
        setDragonPhase(dragon, EnderDragon.Phase.STRAFING);
        Location start = dragon.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        Location end = target.getLocation().clone();
        List<Location> lane = lane(start, end, 28, 2.5D, target.getLocation().getY() + 0.2D);
        announce(world, message("fight.messages.attack-breath-strafe", "Der Drache setzt zum Atemflug an."));
        for (Location point : lane) {
            ParticleEffects.spawnSimple(world, Particle.DRAGON_BREATH, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!validDelayedEntity(dragon) || !validDelayedEntity(target)) {
                return;
            }
            setDragonPhase(dragon, EnderDragon.Phase.BREATH_ATTACK);
            for (Location point : lane) {
                ParticleEffects.spawnFloat(world, Particle.DRAGON_BREATH, point, 8, 1.2D, 0.15D, 1.2D, 0.02D, 1.0F);
            }
            for (Player player : activePlayers(world)) {
                if (AoeGeometry.hitsLane(player.getLocation(), lane, config.breathStrafeLaneWidth(), config.laneAttackVerticalRange())) {
                    player.damage(fightDamage(session, config.breathStrafeDamage() * phaseDamageMultiplier(session)));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 0));
                }
            }
            resetDragonPhase(dragon, EnderDragon.Phase.CIRCLING, 30L);
        }, config.attackWarningTicks());
    }

    private void runSkyCrash(EndFightSession session, World world, EnderDragon dragon, Player target) {
        if (target == null || !target.isValid()) {
            return;
        }
        setDragonPhase(dragon, EnderDragon.Phase.CHARGE_PLAYER);
        Location start = dragon.getLocation().clone().add(0.0D, 2.0D, 0.0D);
        Location end = target.getLocation().clone();
        List<Location> lane = lane(start, end, 34, 2.4D, target.getLocation().getY() + 0.2D);
        announce(world, message("fight.messages.attack-sky-crash", "Der Drache stuerzt vom Himmel."));
        for (Location point : lane) {
            ParticleEffects.spawnSimple(world, Particle.END_ROD, point, 2, 0.1D, 0.1D, 0.1D, 0.01D);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!validDelayedEntity(dragon) || !validDelayedEntity(target)) {
                return;
            }
            for (Location point : lane) {
                ParticleEffects.spawnSimple(world, Particle.CRIT, point, 3, 0.15D, 0.15D, 0.15D, 0.02D);
            }
            for (Player player : activePlayers(world)) {
                if (AoeGeometry.hitsLane(player.getLocation(), lane, config.skyCrashLaneWidth(), config.laneAttackVerticalRange())) {
                    player.damage(fightDamage(session, config.skyCrashDamage() * phaseDamageMultiplier(session)));
                    Vector push = player.getLocation().toVector().subtract(end.toVector());
                    if (push.lengthSquared() > 0.001D) {
                        player.setVelocity(push.normalize().multiply(config.skyCrashKnockback()).setY(0.55D));
                    }
                }
            }
            runSkyCrashAftershock(session, world, end);
            resetDragonPhase(dragon, EnderDragon.Phase.CIRCLING, 20L);
        }, config.attackWarningTicks());
    }

    private void runWingRing(EndFightSession session, World world, EnderDragon dragon) {
        setDragonPhase(dragon, EnderDragon.Phase.ROAR_BEFORE_ATTACK);
        Location origin = session.finalPhaseActive() ? center(world) : dragon.getLocation().clone();
        announce(world, message("fight.messages.attack-wing-ring", "Der Drache sammelt einen Fluegelschlag."));
        ParticleEffects.spawnShockwave(world, origin, config.wingRingRadius() * 0.5D, Particle.CLOUD);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!validDelayedLocation(origin) || !validDelayedEntity(dragon)) {
                return;
            }
            ParticleEffects.spawnShockwave(world, origin, config.wingRingRadius(), Particle.CLOUD);
            world.playSound(origin, Sound.ENTITY_ENDER_DRAGON_FLAP, 3.0F, 0.55F);
            for (Player player : activePlayers(world)) {
                if (AoeGeometry.hitsPoint(player.getLocation(), origin, config.wingRingRadius(), config.pointAttackVerticalRange())) {
                    player.damage(fightDamage(session, config.wingRingDamage()));
                    Vector push = player.getLocation().toVector().subtract(origin.toVector());
                    if (push.lengthSquared() > 0.001D) {
                        player.setVelocity(push.normalize().multiply(config.wingRingKnockback()).setY(0.45D));
                    }
                }
            }
            resetDragonPhase(dragon, EnderDragon.Phase.CIRCLING, 20L);
        }, config.attackWarningTicks());
    }

    private void runVoidGrasp(EndFightSession session, World world) {
        List<Player> targets = prioritizedBreathTargets(session, world).stream()
                .limit(config.voidGraspMaxTargets())
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        Location center = center(world);
        announce(world, message("fight.messages.attack-void-grasp", "Die Leere greift nach euch."));
        for (Player target : targets) {
            Location location = target.getLocation().clone();
            warnCircle(location, Particle.REVERSE_PORTAL);
            Vector pull = center.toVector().subtract(location.toVector());
            if (pull.lengthSquared() > 0.001D) {
                target.setVelocity(target.getVelocity().add(pull.normalize().multiply(config.voidGraspPull()).setY(0.25D)));
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!validDelayedLocation(location)) {
                    return;
                }
                ParticleEffects.spawnShockwave(world, location, config.voidGraspRadius(), Particle.REVERSE_PORTAL);
                for (Player player : activePlayers(world)) {
                    if (AoeGeometry.hitsPoint(player.getLocation(), location, config.voidGraspRadius(), config.pointAttackVerticalRange())) {
                        player.damage(fightDamage(session, config.voidGraspDamage() * phaseDamageMultiplier(session)));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                    }
                }
            }, config.attackWarningTicks());
        }
    }

    private void runCrystalBarrage(EndFightSession session, World world, EnderDragon dragon) {
        setDragonPhase(dragon, EnderDragon.Phase.STRAFING);
        int nodeBonus = session.activeNodeCount(RitualNodeType.ENDER_VOLLEY);
        int count = Math.min(config.crystalBarrageMaxTargets() + nodeBonus, Math.max(1, activePlayers(world).size()));
        List<Player> targets = prioritizedBreathTargets(session, world);
        if (targets.isEmpty()) {
            return;
        }
        announce(world, message("fight.messages.attack-crystal-barrage", "Der Drache entfesselt eine Kristallsalve."));
        Location source = dragon.getLocation().clone().add(0.0D, 2.0D, 0.0D);
        for (int i = 0; i < count; i++) {
            Player targetPlayer = targets.get(i % targets.size());
            Location target = targetPlayer.getLocation().clone();
            warnCircle(target, Particle.END_ROD);
            ParticleEffects.spawnBeam(world, source, target.clone().add(0.0D, 1.0D, 0.0D), Particle.PORTAL);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!validDelayedEntity(dragon) || !validDelayedLocation(target)) {
                    return;
                }
                ParticleEffects.spawnBeam(world, source, target.clone().add(0.0D, 1.0D, 0.0D), Particle.END_ROD);
                ParticleEffects.spawnShockwave(world, target, config.crystalBarrageRadius(), Particle.END_ROD);
                for (Player player : activePlayers(world)) {
                    if (AoeGeometry.hitsPoint(player.getLocation(), target, config.crystalBarrageRadius(), config.pointAttackVerticalRange())) {
                        player.damage(fightDamage(session, config.crystalBarrageDamage() * phaseDamageMultiplier(session)));
                    }
                }
            }, config.attackWarningTicks());
        }
        resetDragonPhase(dragon, EnderDragon.Phase.CIRCLING, config.attackWarningTicks() + 20L);
    }

    private void runPerchRitual(EndFightSession session, World world, EnderDragon dragon) {
        setDragonPhase(dragon, EnderDragon.Phase.LAND_ON_PORTAL);
        Location origin = center(world);
        announce(world, message("fight.messages.attack-perch-ritual", "Der Drache ruft ein Portalritual."));
        for (int i = 0; i < 3; i++) {
            ParticleEffects.spawnCircle(world, origin.clone().add(0.0D, 0.05D + i * 0.1D, 0.0D), 4.0D + i * 4.0D, Particle.DRAGON_BREATH, 48);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!validDelayedEntity(dragon) || !validDelayedLocation(origin)) {
                return;
            }
            setDragonPhase(dragon, EnderDragon.Phase.BREATH_ATTACK);
            ParticleEffects.spawnFloat(world, Particle.DRAGON_BREATH, origin, 160, config.perchRitualRadius(), 0.2D, config.perchRitualRadius(), 0.03D, 1.0F);
            for (Player player : activePlayers(world)) {
                if (AoeGeometry.hitsPoint(player.getLocation(), origin, config.perchRitualRadius(), config.pointAttackVerticalRange())) {
                    player.damage(fightDamage(session, config.perchRitualDamage() * phaseDamageMultiplier(session)));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
                }
            }
            resetDragonPhase(dragon, EnderDragon.Phase.LEAVE_PORTAL, 30L);
        }, config.attackWarningTicks());
    }

    private List<Location> lane(Location start, Location end, int points, double stepDistance, double y) {
        Vector direction = end.toVector().subtract(start.toVector());
        if (direction.lengthSquared() <= 0.001D) {
            return List.of();
        }
        direction.normalize();
        List<Location> lane = new ArrayList<>();
        for (int i = 0; i < Math.max(1, points); i++) {
            Location point = start.clone().add(direction.clone().multiply(i * stepDistance));
            point.setY(y);
            lane.add(point);
        }
        return lane;
    }

    private void runSkyCrashAftershock(EndFightSession session, World world, Location origin) {
        if (session.phase().ordinal() < FightPhase.ESCALATION.ordinal()) {
            return;
        }
        Location aftershockOrigin = session.finalPhaseActive() ? center(world) : origin.clone();
        ParticleEffects.spawnShockwave(world, aftershockOrigin, session.finalPhaseActive() ? 10.0D : 6.0D, Particle.PORTAL);
        for (Player player : activePlayers(world)) {
            double radius = session.finalPhaseActive() ? 10.0D : 6.0D;
            if (AoeGeometry.hitsPoint(player.getLocation(), aftershockOrigin, radius, config.pointAttackVerticalRange())) {
                player.damage(fightDamage(session, config.skyCrashAftershockDamage()));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
            }
        }
    }

    private double phaseDamageMultiplier(EndFightSession session) {
        return switch (session.phase()) {
            case OPENING -> 1.0D;
            case TOTEMS -> 1.08D;
            case ESCALATION -> 1.18D;
            case FINAL -> 1.30D;
        };
    }

    private double fightDamage(EndFightSession session, double baseDamage) {
        return settings.damage(session.fightProfile(), baseDamage);
    }

    private double fightDamage(World world, double baseDamage) {
        EndFightSession session = world == null ? null : sessionsByWorld.get(world.getName());
        return session == null ? baseDamage : fightDamage(session, baseDamage);
    }

    private double nodeHealth(EndFightSession session, double baseHealth) {
        return settings.nodeHealth(session.fightProfile(), baseHealth);
    }

    private void setDragonPhase(EnderDragon dragon, EnderDragon.Phase phase) {
        if (!config.phaseControlEnabled() || dragon == null || !dragon.isValid()) {
            return;
        }
        dragon.setPhase(phase);
    }

    private void resetDragonPhase(EnderDragon dragon, EnderDragon.Phase phase, long delayTicks) {
        if (!config.phaseControlEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (validDelayedEntity(dragon)) {
                dragon.setPhase(phase);
            }
        }, Math.max(1L, delayTicks));
    }

    private ArenaEventType chooseEvent(EndFightSession session) {
        int index = (int) ((session.tick() / 20L) % 3L);
        if (index == 0) {
            return ArenaEventType.VOID_RIFT;
        }
        if (index == 1) {
            return ArenaEventType.BREATH_STORM;
        }
        return ArenaEventType.SHOCKWAVE;
    }

    private long nextCooldown(EndFightSession session) {
        long base = session.finalPhaseActive() ? config.eventFinalCooldownTicks() : config.eventBaseCooldownTicks();
        long reduced = base - session.activeNodeCount(RitualNodeType.STORM_SPIRE) * config.eventStormReductionTicks();
        return settings.cooldown(session.fightProfile(), Math.max(config.eventMinimumCooldownTicks(), reduced));
    }

    private void runArenaEvent(EndFightSession session, World world, ArenaEventType type) {
        if (type == ArenaEventType.VOID_RIFT) {
            runVoidRift(world);
        } else if (type == ArenaEventType.BREATH_STORM) {
            runBreathStorm(session, world);
        } else {
            runShockwave(session, world);
        }
    }

    private void runVoidRift(World world) {
        List<Location> targets = activePlayers(world).stream()
                .limit(3)
                .map(Player::getLocation)
                .map(Location::clone)
                .toList();
        for (Location target : targets) {
            warnCircle(target, Particle.PORTAL);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!validDelayedLocation(target)) {
                    return;
                }
                ParticleEffects.spawnFloat(target.getWorld(), Particle.DRAGON_BREATH, target, 70, 3.0D, 0.1D, 3.0D, 0.03D, 1.0F);
                for (Player player : activePlayers(target.getWorld())) {
                    if (AoeGeometry.hitsPoint(player.getLocation(), target, 4.0D, config.pointAttackVerticalRange())) {
                        player.damage(fightDamage(target.getWorld(), config.voidRiftDamage()));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                    }
                }
            }, config.eventWarningTicks());
        }
    }

    private void runBreathStorm(EndFightSession session, World world) {
        List<Player> targets = prioritizedBreathTargets(session, world);
        int count = Math.min(session.finalPhaseActive() ? 4 : 2, targets.size());
        for (int i = 0; i < count; i++) {
            Location target = targets.get(i).getLocation().clone();
            warnCircle(target, Particle.DRAGON_BREATH);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!validDelayedLocation(target)) {
                    return;
                }
                ParticleEffects.spawnFloat(target.getWorld(), Particle.DRAGON_BREATH, target, 120, 4.0D, 0.2D, 4.0D, 0.04D, 1.0F);
                for (Player player : activePlayers(target.getWorld())) {
                    if (AoeGeometry.hitsPoint(player.getLocation(), target, 5.0D, config.pointAttackVerticalRange())) {
                        player.damage(fightDamage(session, 6.0D));
                    }
                }
            }, config.eventWarningTicks());
        }
    }

    private void runShockwave(EndFightSession session, World world) {
        Location origin = session.finalPhaseActive() ? center(world) : randomNodeLocation(session).orElse(center(world));
        ParticleEffects.spawnShockwave(world, origin, 3.0D, Particle.END_ROD);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!validDelayedLocation(origin)) {
                return;
            }
            world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 0.8F);
            for (Player player : activePlayers(world)) {
                if (AoeGeometry.hitsPoint(player.getLocation(), origin, 32.0D, config.pointAttackVerticalRange())) {
                    Vector push = player.getLocation().toVector().subtract(origin.toVector()).normalize().multiply(1.2D);
                    push.setY(0.45D);
                    player.setVelocity(push);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
                }
            }
        }, config.eventWarningTicks());
    }

    private void applyAnchorPull(EndFightSession session, World world) {
        int anchors = session.activeNodeCount(RitualNodeType.GRAVITY_ANCHOR) + session.finalAnchorCount();
        if (anchors <= 0) {
            return;
        }

        Location center = center(world);
        if (session.tick() % 40L == 0L) {
            for (PillarRitualNode node : session.nodes()) {
                if (node.unlocked() && (node.type() == RitualNodeType.GRAVITY_ANCHOR || node.type() == RitualNodeType.FINAL_ANCHOR)) {
                    ParticleEffects.spawnBeam(world, node.pillarTop(), center, Particle.REVERSE_PORTAL);
                    ParticleEffects.spawnShockwave(world, node.pillarTop(), 2.5D, Particle.PORTAL);
                }
            }
        }
        for (Player player : activePlayers(world)) {
            if (player.getLocation().distanceSquared(center) <= 48.0D * 48.0D) {
                Vector pull = center.toVector().subtract(player.getLocation().toVector());
                if (pull.lengthSquared() <= 0.001D) {
                    continue;
                }
                pull.normalize().multiply(0.08D * anchors);
                player.setVelocity(player.getVelocity().add(pull));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
            }
        }
    }

    private void updateHighGroundTracking(EndFightSession session, World world) {
        for (Player player : activePlayers(world)) {
            FightParticipant participant = session.participants().stream()
                    .filter(candidate -> candidate.uniqueId().equals(player.getUniqueId()))
                    .findFirst()
                    .orElse(null);
            if (participant == null) {
                continue;
            }
            if (player.getLocation().getY() >= config.pillarCampingY()) {
                participant.setHighGroundTicks(participant.highGroundTicks() + 20L);
            } else {
                participant.setHighGroundTicks(0L);
            }
        }
    }

    private void deactivateMissingCrystals(EndFightSession session) {
        for (PillarRitualNode node : session.nodes()) {
            if (!node.active()) {
                continue;
            }
            Optional<UUID> crystalUniqueId = node.crystalUniqueId();
            if (crystalUniqueId.isEmpty() || !(Bukkit.getEntity(crystalUniqueId.get()) instanceof EnderCrystal)) {
                deactivateNode(session, node, node.pillarTop());
            }
        }
    }

    private boolean hasPillarCamper(EndFightSession session) {
        return session.participants().stream().anyMatch(participant -> participant.highGroundTicks() >= config.pillarCampingThresholdTicks());
    }

    private List<Player> prioritizedBreathTargets(EndFightSession session, World world) {
        List<Player> players = new ArrayList<>(activePlayers(world));
        players.sort(Comparator.comparingLong((Player player) -> session.participants().stream()
                .filter(participant -> participant.uniqueId().equals(player.getUniqueId()))
                .findFirst()
                .map(FightParticipant::highGroundTicks)
                .orElse(0L)).reversed());
        return players;
    }

    private void showRitualAnimations(EndFightSession session, World world, EnderDragon dragon) {
        double phase = session.tick() / 12.0D;
        for (PillarRitualNode node : session.nodes()) {
            if (!node.active()) {
                continue;
            }
            Location top = node.pillarTop();
            if (node.locked()) {
                ParticleEffects.spawnSimple(world, Particle.REVERSE_PORTAL, top, 3, 0.4D, 0.4D, 0.4D, 0.01D);
                continue;
            }
            switch (node.type()) {
                case DRAGON_WARD -> ParticleEffects.spawnBeam(world, top, dragon.getLocation().add(0.0D, 2.0D, 0.0D), Particle.END_ROD);
                case LIFE_SPIRE -> ParticleEffects.spawnHelix(world, top, 1.2D, 4.0D, phase, Particle.HAPPY_VILLAGER);
                case CRYSTAL_THORNS -> ParticleEffects.spawnSimple(world, Particle.DAMAGE_INDICATOR, top, 3, 0.8D, 0.5D, 0.8D, 0.01D);
                case STORM_SPIRE -> ParticleEffects.spawnHelix(world, top, 1.6D, 5.0D, phase, Particle.PORTAL);
                case GRAVITY_ANCHOR -> ParticleEffects.spawnShockwave(world, top, 2.0D + (session.tick() % 60L) / 20.0D, Particle.REVERSE_PORTAL);
                case PILLAR_BEAM -> ParticleEffects.spawnBeam(world, top, center(world), Particle.PORTAL);
                case VOID_RIFT -> ParticleEffects.spawnCircle(world, top, 2.2D, Particle.REVERSE_PORTAL, 28);
                case ENDER_VOLLEY -> ParticleEffects.spawnHelix(world, top, 1.0D, 6.0D, -phase, Particle.END_ROD);
                case WING_GUST -> ParticleEffects.spawnShockwave(world, top, 1.5D, Particle.CLOUD);
                case FINAL_ANCHOR -> {
                    ParticleEffects.spawnHelix(world, top, 2.0D, 7.0D, phase, Particle.END_ROD);
                    ParticleEffects.spawnBeam(world, top, center(world), Particle.PORTAL);
                }
                case NONE -> ParticleEffects.spawnSimple(world, Particle.REVERSE_PORTAL, top, 3, 0.4D, 0.4D, 0.4D, 0.01D);
            }
        }
    }

    private void warnCircle(Location location, Particle particle) {
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 1.2F, 0.7F);
        ParticleEffects.spawnCircle(location.getWorld(), location, 3.0D, particle, 48);
    }

    private void syncCrystalIndicators(EndFightSession session, World world) {
        if (!config.crystalIndicatorEnabled()) {
            return;
        }
        for (PillarRitualNode node : session.nodes()) {
            if (node.active()) {
                syncCrystalIndicator(node, world);
            }
        }
    }

    private void syncCrystalIndicator(PillarRitualNode node, World world) {
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

    private void updateCrystalIndicator(PillarRitualNode node) {
        node.indicatorUniqueId()
                .map(Bukkit::getEntity)
                .filter(TextDisplay.class::isInstance)
                .map(TextDisplay.class::cast)
                .ifPresent(display -> display.text(Component.text(indicatorText(node))));
    }

    private String indicatorText(PillarRitualNode node) {
        String nodeName = nodeName(node.type());
        if (node.locked()) {
            return message("fight.messages.locked-indicator", "%node%\nVersiegelt - erwacht in Phase: %phase%")
                    .replace("%node%", nodeName)
                    .replace("%phase%", phaseName(node.requiredPhase()));
        }
        return message("fight.messages.crystal-indicator", "%node%\n%bar% %health%/%max_health%")
                .replace("%node%", nodeName)
                .replace("%bar%", healthBar(node.health(), node.maxHealth()))
                .replace("%health%", Long.toString(Math.round(node.health())))
                .replace("%max_health%", Long.toString(Math.round(node.maxHealth())));
    }

    private String healthBar(double health, double maxHealth) {
        int width = Math.max(4, config.crystalIndicatorBarWidth());
        double ratio = maxHealth <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, health / maxHealth));
        int filled = (int) Math.round(width * ratio);
        return "|".repeat(filled) + "-".repeat(width - filled);
    }

    private void removeIndicator(PillarRitualNode node) {
        node.indicatorUniqueId().map(Bukkit::getEntity).ifPresent(Entity::remove);
        node.clearIndicator();
    }

    private void buffNearbyParticipants(EndFightSession session, Location location) {
        for (Player player : activePlayers(location.getWorld())) {
            if (player.getLocation().distanceSquared(location) <= 40.0D * 40.0D) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 0));
            }
        }
    }

    private void buffActiveParticipants(EndFightSession session, World world, PotionEffectType type, int duration, int amplifier) {
        for (Player player : activePlayers(world)) {
            player.addPotionEffect(new PotionEffect(type, duration, amplifier));
        }
    }

    private List<Player> activePlayers(World world) {
        if (world == null) {
            return List.of();
        }
        return world.getPlayers().stream()
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .filter(player -> nearArena(player.getLocation()))
                .toList();
    }

    private boolean nearActiveCrystal(Location location, double radius) {
        return nearestActiveCrystal(location, radius).isPresent();
    }

    private Optional<EnderCrystal> nearestActiveCrystal(Location location, double radius) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        EndFightSession session = sessionsByWorld.get(location.getWorld().getName());
        if (session == null) {
            return Optional.empty();
        }
        double radiusSquared = radius * radius;
        EnderCrystal nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (PillarRitualNode node : session.nodes()) {
            Optional<EnderCrystal> crystal = node.crystalUniqueId()
                    .map(Bukkit::getEntity)
                    .filter(EnderCrystal.class::isInstance)
                    .map(EnderCrystal.class::cast);
            if (crystal.isEmpty() || !node.active()) {
                continue;
            }
            double distance = crystal.get().getLocation().distanceSquared(location);
            if (distance <= radiusSquared && distance < nearestDistance) {
                nearest = crystal.get();
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private boolean nearArena(Location location) {
        if (location.getWorld() == null || location.getWorld().getEnvironment() != World.Environment.THE_END) {
            return false;
        }
        double dx = location.getX() - config.arenaCenterX();
        double dz = location.getZ() - config.arenaCenterZ();
        return dx * dx + dz * dz <= config.arenaRadius() * config.arenaRadius();
    }

    private Optional<EnderDragon> dragon(EndFightSession session) {
        Entity entity = Bukkit.getEntity(session.dragonUniqueId());
        if (entity instanceof EnderDragon dragon) {
            return Optional.of(dragon);
        }
        return Optional.empty();
    }

    private Optional<Location> randomNodeLocation(EndFightSession session) {
        for (PillarRitualNode node : session.nodes()) {
            if (node.unlocked()) {
                return Optional.of(node.pillarTop());
            }
        }
        return Optional.empty();
    }

    private Location center(World world) {
        return new Location(world, config.arenaCenterX(), 65.0D, config.arenaCenterZ());
    }

    private void syncBossBar(EnderDragon dragon, EndFightSession session) {
        BossBar bossBar = bossBarsByWorld.computeIfAbsent(session.worldName(), worldName ->
                Bukkit.createBossBar(message("fight.messages.bossbar-opening", "Phase: %phase%"), BarColor.PURPLE, BarStyle.SEGMENTED_10));
        bossBar.setTitle(title(session));
        bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, dragon.getHealth() / dragon.getMaxHealth())));
        for (Player player : activePlayers(dragon.getWorld())) {
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
        for (Player player : List.copyOf(bossBar.getPlayers())) {
            if (!activePlayers(dragon.getWorld()).contains(player)) {
                bossBar.removePlayer(player);
            }
        }
    }

    private String title(EndFightSession session) {
        if (session.exposed()) {
            return message("fight.messages.bossbar-exposed", "Der Drache ist verwundbar");
        }
        String path = switch (session.phase()) {
            case OPENING -> "fight.messages.bossbar-opening";
            case TOTEMS -> "fight.messages.bossbar-totems";
            case ESCALATION -> "fight.messages.bossbar-escalation";
            case FINAL -> "fight.messages.bossbar-final";
        };
        return message(path, "Phase: %phase%").replace("%phase%", phaseName(session.phase()));
    }

    private void announce(World world, String message) {
        for (Player player : activePlayers(world)) {
            player.sendMessage(message);
        }
    }

    private String nodeName(RitualNodeType type) {
        return message("fight.node-names." + nodeKey(type), fallbackNodeName(type));
    }

    private String phaseName(FightPhase phase) {
        return message("fight.phases." + phaseKey(phase), switch (phase) {
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

    private String message(String path, String fallback) {
        return plugin.getConfig().getString(path, fallback);
    }

    private void scanExistingDragons() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() != World.Environment.THE_END) {
                    continue;
                }
                for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                    trackDragon(dragon);
                }
                ensureFirstRitualStarted(world);
            }
        });
    }

    private void cleanup(EndFightSession session) {
        FirstEndRitualSession ritual = ritualsByWorld.remove(session.worldName());
        if (ritual != null) {
            removeRitualEvokers(ritual);
            stopRitualAnimationTaskIfIdle();
        }
        if (!session.active()) {
            Entity entity = Bukkit.getEntity(session.dragonUniqueId());
            if (entity instanceof EnderDragon dragon && dragon.isValid()) {
                dragon.setInvisible(false);
                dragon.setInvulnerable(false);
                dragon.setSilent(false);
                dragon.setPhase(EnderDragon.Phase.CIRCLING);
                for (Player player : dragon.getWorld().getPlayers()) {
                    player.showEntity(plugin, dragon);
                }
            }
        }
        for (PillarRitualNode node : session.nodes()) {
            removeIndicator(node);
            if (node.temporaryCrystal() || !session.active()) {
                node.crystalUniqueId().map(Bukkit::getEntity).ifPresent(Entity::remove);
            }
        }
        removeGuardians(session);

        BossBar bossBar = bossBarsByWorld.remove(session.worldName());
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    private void removeRitualEvokers(FirstEndRitualSession ritual) {
        for (UUID evokerId : ritual.evokerIds()) {
            Entity entity = Bukkit.getEntity(evokerId);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private void cleanupOrphanedRitualEvokers() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() != World.Environment.THE_END) {
                    continue;
                }
                for (Entity entity : List.copyOf(world.getEntities())) {
                    if (entity.getPersistentDataContainer().has(ritualEvokerKey, PersistentDataType.BYTE)
                            || entity.getPersistentDataContainer().has(ritualCrystalKey, PersistentDataType.BYTE)) {
                        entity.remove();
                    }
                }
            }
        });
    }

    private boolean validDelayedLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return sessionsByWorld.containsKey(location.getWorld().getName()) && location.isWorldLoaded();
    }

    private boolean validDelayedEntity(Entity entity) {
        return entity != null
                && entity.isValid()
                && entity.getWorld() != null
                && sessionsByWorld.containsKey(entity.getWorld().getName());
    }

    private void collapseAnimation(World world) {
        Location center = center(world);
        for (int i = 1; i <= 5; i++) {
            ParticleEffects.spawnShockwave(world, center, i * 2.0D, Particle.END_ROD);
        }
        ParticleEffects.spawnDust(world, center, Color.PURPLE, 2.0F, 30, 2.0D, 1.0D, 2.0D);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 2.5F, 0.7F);
    }

    private enum GuardianKind {
        ENDERMAN,
        SHULKER
    }
}
