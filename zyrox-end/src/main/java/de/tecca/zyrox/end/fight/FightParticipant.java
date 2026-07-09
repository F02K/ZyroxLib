package de.tecca.zyrox.end.fight;

import java.util.UUID;

public final class FightParticipant {

    private final UUID uniqueId;
    private long lastActiveTick;
    private long highGroundTicks;

    public FightParticipant(UUID uniqueId, long activeTick) {
        this.uniqueId = uniqueId;
        this.lastActiveTick = activeTick;
    }

    public UUID uniqueId() {
        return uniqueId;
    }

    public long lastActiveTick() {
        return lastActiveTick;
    }

    public void markActive(long tick) {
        lastActiveTick = tick;
        highGroundTicks = 0L;
    }

    public long highGroundTicks() {
        return highGroundTicks;
    }

    public void setHighGroundTicks(long highGroundTicks) {
        this.highGroundTicks = Math.max(0L, highGroundTicks);
    }
}
