package de.tecca.zyrox.end.fight;

public enum RitualNodeType {
    NONE(FightPhase.OPENING, "End Crystal"),
    DRAGON_WARD(FightPhase.OPENING, "Dragon Ward"),
    LIFE_SPIRE(FightPhase.OPENING, "Life Spire"),
    CRYSTAL_THORNS(FightPhase.OPENING, "Crystal Thorns"),
    STORM_SPIRE(FightPhase.TOTEMS, "Storm Spire"),
    GRAVITY_ANCHOR(FightPhase.TOTEMS, "Gravity Anchor"),
    PILLAR_BEAM(FightPhase.TOTEMS, "Pillar Beam"),
    VOID_RIFT(FightPhase.ESCALATION, "Void Rift"),
    ENDER_VOLLEY(FightPhase.ESCALATION, "Ender Volley"),
    WING_GUST(FightPhase.ESCALATION, "Wing Gust"),
    FINAL_ANCHOR(FightPhase.FINAL, "Final Anchor");

    private final FightPhase requiredPhase;
    private final String displayName;

    RitualNodeType(FightPhase requiredPhase, String displayName) {
        this.requiredPhase = requiredPhase;
        this.displayName = displayName;
    }

    public FightPhase requiredPhase() {
        return requiredPhase;
    }

    public String displayName() {
        return displayName;
    }

    public boolean unlockedIn(FightPhase phase) {
        return phase.ordinal() >= requiredPhase.ordinal();
    }
}
