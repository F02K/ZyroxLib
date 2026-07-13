package de.tecca.zyrox.end.fight;

import org.bukkit.Location;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class EndFightSession {

    private final UUID dragonUniqueId;
    private final String worldName;
    private final FightProfile fightProfile;
    private final Map<UUID, PillarRitualNode> nodes = new LinkedHashMap<>();
    private final Map<UUID, FightParticipant> participants = new LinkedHashMap<>();
    private final Map<UUID, Long> thornsCooldowns = new LinkedHashMap<>();
    private final Map<UUID, Long> crystalGlobalCooldowns = new LinkedHashMap<>();
    private final Map<UUID, Long> crystalPlayerCooldowns = new LinkedHashMap<>();
    private final Map<UUID, Long> guardianFocusUntilTicks = new LinkedHashMap<>();
    private final Map<UUID, Long> guardianAttackCooldowns = new LinkedHashMap<>();
    private final Map<UUID, Long> crystalWardUntilTicks = new LinkedHashMap<>();
    private final Set<UUID> guardianUniqueIds = new HashSet<>();
    private final Set<FightPhase> spawnedPhases = EnumSet.noneOf(FightPhase.class);
    private FightPhase phase = FightPhase.OPENING;
    private long tick;
    private long nextArenaEventTick;
    private long nextDragonAttackTick;
    private long nextGuardianSpawnTick;
    private long nextReactiveGuardianSpawnTick;
    private DragonAttackType lastDragonAttackType;
    private DragonAttackType queuedDragonAttackType;
    private DragonAttackIntent dragonAttackIntent;
    private Location lastDragonAttackLocation;
    private double lastDragonAttackDistance = -1.0D;
    private long dragonAttackStalledTicks;
    private boolean dragonHealthApplied;
    private FightActivationState activationState;
    private FightPhase guardianBudgetPhase = FightPhase.OPENING;
    private int guardianBudgetConsumed;
    private double lastAllowedDragonHealth = -1.0D;
    private long exposedUntilTick = -1L;
    private boolean finalAnchorDestroyed;

    public EndFightSession(UUID dragonUniqueId, String worldName) {
        this(dragonUniqueId, worldName, FightProfile.FIRST, FightActivationState.ACTIVE);
    }

    public EndFightSession(UUID dragonUniqueId, String worldName, FightProfile fightProfile, FightActivationState activationState) {
        this.dragonUniqueId = dragonUniqueId;
        this.worldName = worldName;
        this.fightProfile = fightProfile;
        this.activationState = activationState;
    }

    public UUID dragonUniqueId() {
        return dragonUniqueId;
    }

    public String worldName() {
        return worldName;
    }

    public FightProfile fightProfile() {
        return fightProfile;
    }

    public FightActivationState activationState() {
        return activationState;
    }

    public boolean active() {
        return activationState == FightActivationState.ACTIVE;
    }

    public void setActivationState(FightActivationState activationState) {
        this.activationState = activationState;
    }

    public FightPhase phase() {
        return phase;
    }

    public long tick() {
        return tick;
    }

    public void setTick(long tick) {
        this.tick = Math.max(0L, tick);
    }

    public long nextArenaEventTick() {
        return nextArenaEventTick;
    }

    public void setNextArenaEventTick(long nextArenaEventTick) {
        this.nextArenaEventTick = nextArenaEventTick;
    }

    public long nextDragonAttackTick() {
        return nextDragonAttackTick;
    }

    public void setNextDragonAttackTick(long nextDragonAttackTick) {
        this.nextDragonAttackTick = nextDragonAttackTick;
    }

    public long nextGuardianSpawnTick() {
        return nextGuardianSpawnTick;
    }

    public void setNextGuardianSpawnTick(long nextGuardianSpawnTick) {
        this.nextGuardianSpawnTick = Math.max(0L, nextGuardianSpawnTick);
    }

    public long nextReactiveGuardianSpawnTick() {
        return nextReactiveGuardianSpawnTick;
    }

    public void setNextReactiveGuardianSpawnTick(long nextReactiveGuardianSpawnTick) {
        this.nextReactiveGuardianSpawnTick = Math.max(0L, nextReactiveGuardianSpawnTick);
    }

    public DragonAttackType lastDragonAttackType() {
        return lastDragonAttackType;
    }

    public void markDragonAttack(DragonAttackType type, long nextDragonAttackTick) {
        this.lastDragonAttackType = type;
        this.nextDragonAttackTick = nextDragonAttackTick;
    }

    public Optional<DragonAttackType> queuedDragonAttackType() {
        return Optional.ofNullable(queuedDragonAttackType);
    }

    public void queueDragonAttack(DragonAttackType type) {
        queuedDragonAttackType = type;
    }

    public Optional<DragonAttackType> consumeQueuedDragonAttack() {
        DragonAttackType queued = queuedDragonAttackType;
        queuedDragonAttackType = null;
        return Optional.ofNullable(queued);
    }

    public Optional<DragonAttackIntent> dragonAttackIntent() {
        return Optional.ofNullable(dragonAttackIntent);
    }

    public void startDragonAttackIntent(DragonAttackType type, UUID targetUniqueId, long engageTicks, long timeoutTicks) {
        dragonAttackIntent = new DragonAttackIntent(
                type,
                targetUniqueId,
                tick,
                tick + Math.max(1L, engageTicks),
                tick + Math.max(Math.max(1L, engageTicks), timeoutTicks)
        );
        clearDragonAttackStall();
    }

    public void replaceDragonAttackIntent(DragonAttackIntent intent) {
        dragonAttackIntent = intent;
    }

    public Optional<DragonAttackIntent> consumeDragonAttackIntent() {
        DragonAttackIntent intent = dragonAttackIntent;
        dragonAttackIntent = null;
        clearDragonAttackStall();
        return Optional.ofNullable(intent);
    }

    public void clearDragonAttackIntent() {
        dragonAttackIntent = null;
        clearDragonAttackStall();
    }

    public void recordDragonAttackProgress(Location dragonLocation, double targetDistance, double progressDistance, long intervalTicks) {
        if (dragonLocation == null || targetDistance < 0.0D) {
            clearDragonAttackStall();
            return;
        }
        boolean progressed = lastDragonAttackDistance < 0.0D
                || targetDistance <= lastDragonAttackDistance - progressDistance
                || movedEnough(dragonLocation, progressDistance);
        dragonAttackStalledTicks = progressed ? 0L : dragonAttackStalledTicks + Math.max(1L, intervalTicks);
        lastDragonAttackDistance = targetDistance;
        lastDragonAttackLocation = dragonLocation.clone();
    }

    public long dragonAttackStalledTicks() {
        return dragonAttackStalledTicks;
    }

    public void clearDragonAttackStall() {
        lastDragonAttackLocation = null;
        lastDragonAttackDistance = -1.0D;
        dragonAttackStalledTicks = 0L;
    }

    public boolean dragonHealthApplied() {
        return dragonHealthApplied;
    }

    public void markDragonHealthApplied() {
        dragonHealthApplied = true;
    }

    public double lastAllowedDragonHealth() {
        return lastAllowedDragonHealth;
    }

    public void markAllowedDragonHealth(double health) {
        lastAllowedDragonHealth = Math.max(0.0D, health);
    }

    public FightPhase updatePhase(double health, double maxHealth) {
        double ratio = maxHealth <= 0.0D ? 1.0D : health / maxHealth;
        if (ratio <= 0.25D) {
            phase = FightPhase.FINAL;
        } else if (ratio <= 0.50D) {
            phase = FightPhase.ESCALATION;
        } else if (ratio <= 0.75D) {
            phase = FightPhase.TOTEMS;
        } else {
            phase = FightPhase.OPENING;
        }
        return phase;
    }

    public boolean markPhaseSpawned(FightPhase phase) {
        return spawnedPhases.add(phase);
    }

    public boolean phaseSpawned(FightPhase phase) {
        return spawnedPhases.contains(phase);
    }

    public void addNode(PillarRitualNode node) {
        nodes.put(node.id(), node);
    }

    public Optional<PillarRitualNode> node(UUID id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public Optional<PillarRitualNode> nodeByCrystal(UUID crystalUniqueId) {
        for (PillarRitualNode node : nodes.values()) {
            if (node.crystalUniqueId().filter(crystalUniqueId::equals).isPresent()) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    public Collection<PillarRitualNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public int detectedPillarCount() {
        return nodes.size();
    }

    public int activeNodeCount() {
        int count = 0;
        for (PillarRitualNode node : nodes.values()) {
            if (node.active()) {
                count++;
            }
        }
        return count;
    }

    public int activeNodeCount(RitualNodeType type) {
        int count = 0;
        for (PillarRitualNode node : nodes.values()) {
            if (node.unlocked() && node.type() == type) {
                count++;
            }
        }
        return count;
    }

    public int lockedNodeCount() {
        int count = 0;
        for (PillarRitualNode node : nodes.values()) {
            if (node.locked()) {
                count++;
            }
        }
        return count;
    }

    public int temporaryCrystalCount() {
        int count = 0;
        for (PillarRitualNode node : nodes.values()) {
            if (node.active() && node.temporaryCrystal()) {
                count++;
            }
        }
        return count;
    }

    public int finalAnchorCount() {
        return activeNodeCount(RitualNodeType.FINAL_ANCHOR);
    }

    public Optional<PillarRitualNode> finalAnchor() {
        return nodes.values().stream()
                .filter(node -> node.type() == RitualNodeType.FINAL_ANCHOR)
                .findFirst();
    }

    public boolean markFinalAnchorDestroyed() {
        if (finalAnchorDestroyed) {
            return false;
        }
        finalAnchorDestroyed = true;
        return true;
    }

    public boolean finalAnchorDestroyed() {
        return finalAnchorDestroyed;
    }

    public boolean finalPhaseActive() {
        return phase == FightPhase.FINAL;
    }

    public void markActive(UUID playerUniqueId) {
        participants.computeIfAbsent(playerUniqueId, id -> new FightParticipant(id, tick)).markActive(tick);
    }

    public int activeParticipantCount(EndFightConfig config) {
        int count = 0;
        for (FightParticipant participant : participants.values()) {
            if (tick - participant.lastActiveTick() <= config.inactiveAfterTicks()) {
                count++;
            }
        }
        return count;
    }

    public Collection<FightParticipant> participants() {
        return Collections.unmodifiableCollection(participants.values());
    }

    public boolean markThornsTriggered(UUID nodeId, UUID playerUniqueId, long cooldownTicks) {
        long nextAllowedTick = thornsCooldowns.getOrDefault(thornsCooldownKey(nodeId, playerUniqueId), 0L);
        if (tick < nextAllowedTick) {
            return false;
        }
        thornsCooldowns.put(thornsCooldownKey(nodeId, playerUniqueId), tick + Math.max(1L, cooldownTicks));
        return true;
    }

    public boolean markCrystalHitAllowed(UUID crystalUniqueId, UUID playerUniqueId, long playerCooldownTicks, long globalCooldownTicks) {
        long nextGlobalTick = crystalGlobalCooldowns.getOrDefault(crystalUniqueId, 0L);
        if (tick < nextGlobalTick) {
            return false;
        }
        if (playerUniqueId != null) {
            UUID playerCooldownKey = crystalCooldownKey(crystalUniqueId, playerUniqueId);
            long nextPlayerTick = crystalPlayerCooldowns.getOrDefault(playerCooldownKey, 0L);
            if (tick < nextPlayerTick) {
                return false;
            }
            crystalPlayerCooldowns.put(playerCooldownKey, tick + Math.max(1L, playerCooldownTicks));
        }
        crystalGlobalCooldowns.put(crystalUniqueId, tick + Math.max(1L, globalCooldownTicks));
        return true;
    }

    public void markGuardianFocus(UUID playerUniqueId, long durationTicks) {
        if (playerUniqueId != null) {
            guardianFocusUntilTicks.put(playerUniqueId, tick + Math.max(1L, durationTicks));
        }
    }

    public Optional<UUID> focusedPlayer() {
        return guardianFocusUntilTicks.entrySet().stream()
                .filter(entry -> entry.getValue() >= tick)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    public boolean markGuardianAttack(UUID guardianUniqueId, long cooldownTicks) {
        long nextAllowedTick = guardianAttackCooldowns.getOrDefault(guardianUniqueId, 0L);
        if (tick < nextAllowedTick) {
            return false;
        }
        guardianAttackCooldowns.put(guardianUniqueId, tick + Math.max(1L, cooldownTicks));
        return true;
    }

    public void addGuardian(UUID guardianUniqueId) {
        guardianUniqueIds.add(guardianUniqueId);
    }

    public void markGuardianSpawned(UUID guardianUniqueId) {
        addGuardian(guardianUniqueId);
        guardianBudgetConsumed++;
    }

    public void removeGuardian(UUID guardianUniqueId) {
        guardianUniqueIds.remove(guardianUniqueId);
        guardianAttackCooldowns.remove(guardianUniqueId);
    }

    public Set<UUID> guardianUniqueIds() {
        return Collections.unmodifiableSet(guardianUniqueIds);
    }

    public void beginGuardianPhase(FightPhase phase) {
        beginGuardianPhase(phase, guardianUniqueIds.size());
    }

    public void beginGuardianPhase(FightPhase phase, int existingNormalGuardians) {
        guardianBudgetPhase = phase;
        guardianBudgetConsumed = Math.max(0, existingNormalGuardians);
    }

    public void resetGuardianBudget(FightPhase phase) {
        guardianBudgetPhase = phase;
        guardianBudgetConsumed = 0;
    }

    public int guardianBudgetConsumed(FightPhase phase) {
        return guardianBudgetPhase == phase ? guardianBudgetConsumed : guardianUniqueIds.size();
    }

    public void startCrystalWard(UUID crystalUniqueId, long durationTicks) {
        crystalWardUntilTicks.put(crystalUniqueId, tick + Math.max(1L, durationTicks));
    }

    public boolean crystalWarded(UUID crystalUniqueId) {
        return crystalWardUntilTicks.getOrDefault(crystalUniqueId, 0L) >= tick;
    }

    public void startExposedWindow(long durationTicks) {
        exposedUntilTick = tick + durationTicks;
    }

    public boolean exposed() {
        return exposedUntilTick >= tick;
    }

    public long exposedUntilTick() {
        return exposedUntilTick;
    }

    private UUID thornsCooldownKey(UUID nodeId, UUID playerUniqueId) {
        return new UUID(
                nodeId.getMostSignificantBits() ^ playerUniqueId.getMostSignificantBits(),
                nodeId.getLeastSignificantBits() ^ playerUniqueId.getLeastSignificantBits()
        );
    }

    private UUID crystalCooldownKey(UUID crystalUniqueId, UUID playerUniqueId) {
        return new UUID(
                crystalUniqueId.getMostSignificantBits() ^ playerUniqueId.getMostSignificantBits(),
                crystalUniqueId.getLeastSignificantBits() ^ playerUniqueId.getLeastSignificantBits()
        );
    }

    private boolean movedEnough(Location dragonLocation, double progressDistance) {
        if (lastDragonAttackLocation == null || lastDragonAttackLocation.getWorld() != dragonLocation.getWorld()) {
            return true;
        }
        double dx = lastDragonAttackLocation.getX() - dragonLocation.getX();
        double dy = lastDragonAttackLocation.getY() - dragonLocation.getY();
        double dz = lastDragonAttackLocation.getZ() - dragonLocation.getZ();
        return dx * dx + dy * dy + dz * dz >= progressDistance * progressDistance;
    }
}
