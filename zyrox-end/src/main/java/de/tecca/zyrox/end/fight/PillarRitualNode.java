package de.tecca.zyrox.end.fight;

import org.bukkit.Location;

import java.util.Optional;
import java.util.UUID;

public final class PillarRitualNode {

    private final UUID id;
    private final Location pillarTop;
    private UUID crystalUniqueId;
    private UUID indicatorUniqueId;
    private RitualNodeType type;
    private boolean active;
    private boolean unlocked;
    private boolean temporaryCrystal;
    private double maxHealth;
    private double health;
    private long lastDamagedAtTick;

    public PillarRitualNode(UUID id, Location pillarTop, UUID crystalUniqueId) {
        this.id = id;
        this.pillarTop = pillarTop.clone();
        this.crystalUniqueId = crystalUniqueId;
        this.type = RitualNodeType.NONE;
        this.active = crystalUniqueId != null;
        this.unlocked = false;
    }

    public UUID id() {
        return id;
    }

    public Location pillarTop() {
        return pillarTop.clone();
    }

    public Optional<UUID> crystalUniqueId() {
        return Optional.ofNullable(crystalUniqueId);
    }

    public void bindCrystal(UUID crystalUniqueId, boolean temporaryCrystal) {
        this.crystalUniqueId = crystalUniqueId;
        this.temporaryCrystal = temporaryCrystal;
        this.active = true;
        this.unlocked = false;
    }

    public void setHealth(double maxHealth) {
        this.maxHealth = Math.max(1.0D, maxHealth);
        this.health = this.maxHealth;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public double health() {
        return health;
    }

    public long lastDamagedAtTick() {
        return lastDamagedAtTick;
    }

    public boolean damage(double amount, long tick) {
        if (!active || locked()) {
            return false;
        }
        lastDamagedAtTick = tick;
        health = Math.max(0.0D, health - Math.max(0.0D, amount));
        return health <= 0.0D;
    }

    public RitualNodeType type() {
        return type;
    }

    public void assignType(RitualNodeType type) {
        this.type = type;
        this.unlocked = type.unlockedIn(FightPhase.OPENING);
    }

    public boolean active() {
        return active;
    }

    public boolean unlocked() {
        return active && unlocked;
    }

    public boolean locked() {
        return active && !unlocked;
    }

    public FightPhase requiredPhase() {
        return type.requiredPhase();
    }

    public boolean unlockFor(FightPhase phase) {
        if (!active || unlocked || !type.unlockedIn(phase)) {
            return false;
        }
        unlocked = true;
        return true;
    }

    public Optional<UUID> indicatorUniqueId() {
        return Optional.ofNullable(indicatorUniqueId);
    }

    public void bindIndicator(UUID indicatorUniqueId) {
        this.indicatorUniqueId = indicatorUniqueId;
    }

    public void clearIndicator() {
        indicatorUniqueId = null;
    }

    public void deactivate() {
        active = false;
        crystalUniqueId = null;
        indicatorUniqueId = null;
        unlocked = false;
        temporaryCrystal = false;
        health = 0.0D;
    }

    public boolean temporaryCrystal() {
        return temporaryCrystal;
    }
}
