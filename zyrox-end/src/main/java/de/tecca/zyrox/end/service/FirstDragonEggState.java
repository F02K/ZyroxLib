package de.tecca.zyrox.end.service;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public final class FirstDragonEggState {

    private boolean firstDragonKilled;
    private String firstDragonWorldName;
    private long firstDragonKilledAtEpochMillis;
    private boolean eggClaimed;
    private DragonEggClaim pendingEggClaim;
    private UUID eggId;
    private UUID ownerUniqueId;
    private String ownerName;
    private long claimedAtEpochMillis;
    private String crystalLayoutWorldName;
    private long crystalLayoutScannedAtEpochMillis;
    private final List<String> crystalLayoutPositions = new ArrayList<>();
    private FirstEndRitualStatus ritualStatus = FirstEndRitualStatus.NOT_STARTED;
    private String ritualWorldName;
    private long ritualStartedAtEpochMillis;
    private long ritualCompletedAtEpochMillis;
    private DragonEggTrackingState eggTracking;

    public boolean firstDragonKilled() {
        return firstDragonKilled;
    }

    public void markFirstDragonKilled(String worldName, long killedAtEpochMillis) {
        firstDragonKilled = true;
        firstDragonWorldName = worldName;
        firstDragonKilledAtEpochMillis = killedAtEpochMillis;
    }

    public Optional<String> firstDragonWorldName() {
        return Optional.ofNullable(firstDragonWorldName);
    }

    public long firstDragonKilledAtEpochMillis() {
        return firstDragonKilledAtEpochMillis;
    }

    public boolean eggClaimed() {
        return eggClaimed;
    }

    public DragonEggClaimPhase eggClaimPhase() {
        if (eggClaimed) {
            return DragonEggClaimPhase.CLAIMED;
        }
        return pendingEggClaim == null ? DragonEggClaimPhase.UNCLAIMED : DragonEggClaimPhase.PENDING;
    }

    public Optional<DragonEggClaim> pendingEggClaim() {
        return Optional.ofNullable(pendingEggClaim);
    }

    public void reserveEggClaim(DragonEggClaim claim) {
        if (!eggClaimed) {
            pendingEggClaim = claim;
        }
    }

    public boolean commitPendingEggClaim(UUID expectedEggId) {
        if (eggClaimed || pendingEggClaim == null || !pendingEggClaim.eggId().equals(expectedEggId)) {
            return false;
        }
        markEggClaimed(pendingEggClaim);
        return true;
    }

    public void markEggClaimed(DragonEggClaim claim) {
        eggClaimed = true;
        pendingEggClaim = null;
        eggId = claim.eggId();
        ownerUniqueId = claim.ownerUniqueId();
        ownerName = claim.ownerName();
        claimedAtEpochMillis = claim.claimedAtEpochMillis();
    }

    public Optional<UUID> eggId() {
        return Optional.ofNullable(eggId);
    }

    public Optional<UUID> ownerUniqueId() {
        return Optional.ofNullable(ownerUniqueId);
    }

    public Optional<String> ownerName() {
        return Optional.ofNullable(ownerName);
    }

    public long claimedAtEpochMillis() {
        return claimedAtEpochMillis;
    }

    public Optional<DragonEggClaim> claimedEgg() {
        if (!eggClaimed || eggId == null || ownerUniqueId == null) {
            return Optional.empty();
        }
        return Optional.of(new DragonEggClaim(eggId, ownerUniqueId, ownerName, claimedAtEpochMillis));
    }

    public Optional<DragonEggTrackingState> eggTracking() {
        return Optional.ofNullable(eggTracking);
    }

    public void trackEgg(DragonEggTrackingState trackingState) {
        eggTracking = trackingState;
    }

    public boolean crystalLayoutRecorded() {
        return !crystalLayoutPositions.isEmpty();
    }

    public void recordCrystalLayout(String worldName, long scannedAtEpochMillis, List<String> positions) {
        crystalLayoutWorldName = worldName;
        crystalLayoutScannedAtEpochMillis = scannedAtEpochMillis;
        crystalLayoutPositions.clear();
        crystalLayoutPositions.addAll(positions);
    }

    public Optional<String> crystalLayoutWorldName() {
        return Optional.ofNullable(crystalLayoutWorldName);
    }

    public long crystalLayoutScannedAtEpochMillis() {
        return crystalLayoutScannedAtEpochMillis;
    }

    public List<String> crystalLayoutPositions() {
        return List.copyOf(crystalLayoutPositions);
    }

    public FirstEndRitualStatus ritualStatus() {
        return ritualStatus;
    }

    public void markRitualInProgress(String worldName, long startedAtEpochMillis) {
        ritualStatus = FirstEndRitualStatus.IN_PROGRESS;
        ritualWorldName = worldName;
        ritualStartedAtEpochMillis = startedAtEpochMillis;
        ritualCompletedAtEpochMillis = 0L;
    }

    public void markRitualCompleted(String worldName, long completedAtEpochMillis) {
        ritualStatus = FirstEndRitualStatus.COMPLETED;
        ritualWorldName = worldName;
        ritualCompletedAtEpochMillis = completedAtEpochMillis;
    }

    public Optional<String> ritualWorldName() {
        return Optional.ofNullable(ritualWorldName);
    }

    public long ritualStartedAtEpochMillis() {
        return ritualStartedAtEpochMillis;
    }

    public long ritualCompletedAtEpochMillis() {
        return ritualCompletedAtEpochMillis;
    }
}
