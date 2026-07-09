package de.tecca.zyrox.end.fight;

import java.util.UUID;

public record DragonAttackIntent(
        DragonAttackType type,
        UUID targetUniqueId,
        long startedAtTick,
        long nextImpactTick,
        long timeoutTick
) {

    public DragonAttackIntent withTarget(UUID targetUniqueId, long nextImpactTick) {
        return new DragonAttackIntent(type, targetUniqueId, startedAtTick, nextImpactTick, timeoutTick);
    }

    public DragonAttackIntent withNextImpactTick(long nextImpactTick) {
        return new DragonAttackIntent(type, targetUniqueId, startedAtTick, nextImpactTick, timeoutTick);
    }

    public boolean timedOut(long tick) {
        return tick >= timeoutTick;
    }
}
