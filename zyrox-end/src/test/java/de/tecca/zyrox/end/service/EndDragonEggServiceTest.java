package de.tecca.zyrox.end.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EndDragonEggServiceTest {

    @TempDir
    private Path tempDir;

    @Test
    void noClaimBeforeFirstDragonDeath() {
        EndDragonEggService service = serviceWithClock(1000L);

        Optional<DragonEggClaim> claim = service.reserveFirstEggClaim(UUID.randomUUID(), "Tecca");

        assertTrue(claim.isEmpty());
        assertFalse(service.state().eggClaimed());
    }

    @Test
    void firstDragonDeathArmsClaimOnce() {
        UUID ownerUniqueId = UUID.randomUUID();
        EndDragonEggService service = serviceWithClock(2000L);

        assertTrue(service.recordFirstDragonDeath("world_the_end"));
        assertFalse(service.recordFirstDragonDeath("world_the_end"));

        DragonEggClaim claim = service.reserveFirstEggClaim(ownerUniqueId, "Tecca").orElseThrow();
        assertTrue(service.commitPendingEggClaim(claim.eggId()));

        assertTrue(service.state().eggClaimed());
        assertEquals(ownerUniqueId, service.state().ownerUniqueId().orElseThrow());
        assertEquals("Tecca", service.state().ownerName().orElseThrow());
    }

    @Test
    void firstClaimLocksFutureClaims() {
        EndDragonEggService service = serviceWithClock(3000L);
        service.recordFirstDragonDeath("world_the_end");

        Optional<DragonEggClaim> firstClaim = service.reserveFirstEggClaim(UUID.randomUUID(), "First");
        assertTrue(service.commitPendingEggClaim(firstClaim.orElseThrow().eggId()));
        Optional<DragonEggClaim> secondClaim = service.reserveFirstEggClaim(UUID.randomUUID(), "Second");

        assertTrue(firstClaim.isPresent());
        assertTrue(secondClaim.isEmpty());
        assertEquals("First", service.state().ownerName().orElseThrow());
    }

    @Test
    void firstPickupClaimTagsAndBlocksLaterUntaggedEggs() {
        RecordingTagIo tagIo = new RecordingTagIo();
        EndDragonEggService service = serviceWithClock(3500L, tagIo);
        service.recordFirstDragonDeath("world_the_end");
        UUID ownerUniqueId = UUID.randomUUID();

        DragonEggClaim claim = service.claimAndTagFirstEgg(
                null,
                Material.DRAGON_EGG,
                1,
                ownerUniqueId,
                "First"
        ).orElseThrow();
        Optional<DragonEggClaim> secondClaim = service.claimAndTagFirstEgg(
                null,
                Material.DRAGON_EGG,
                1,
                UUID.randomUUID(),
                "Second"
        );

        assertEquals(DragonEggClaimPhase.CLAIMED, service.state().eggClaimPhase());
        assertEquals(ownerUniqueId, service.state().ownerUniqueId().orElseThrow());
        assertEquals(claim, tagIo.claim);
        assertEquals(1, tagIo.writeCount);
        assertTrue(secondClaim.isEmpty());
        assertEquals(1, tagIo.writeCount);
    }

    @Test
    void claimRejectsInvalidOwnerAndInvalidStacks() {
        RecordingTagIo tagIo = new RecordingTagIo();
        EndDragonEggService service = serviceWithClock(3600L, tagIo);
        service.recordFirstDragonDeath("world_the_end");
        UUID ownerUniqueId = UUID.randomUUID();

        assertTrue(service.claimAndTagFirstEgg(null, Material.DRAGON_EGG, 1, null, "Owner").isEmpty());
        assertTrue(service.claimAndTagFirstEgg(null, Material.DRAGON_EGG, 1, ownerUniqueId, "").isEmpty());
        assertTrue(service.claimAndTagFirstEgg(null, Material.DRAGON_EGG, 2, ownerUniqueId, "Owner").isEmpty());
        assertTrue(service.claimAndTagFirstEgg(null, Material.STONE, 1, ownerUniqueId, "Owner").isEmpty());
        assertEquals(DragonEggClaimPhase.UNCLAIMED, service.state().eggClaimPhase());
    }

    @Test
    void failedTagReadbackRollsBackNewPendingClaim() {
        RecordingTagIo tagIo = new RecordingTagIo();
        tagIo.readBack = false;
        File file = stateFile();
        EndDragonEggService service = new EndDragonEggService(null, storage(file), () -> 3700L, tagIo);
        service.recordFirstDragonDeath("world_the_end");

        Optional<DragonEggClaim> claim = service.claimAndTagFirstEgg(
                null,
                Material.DRAGON_EGG,
                1,
                UUID.randomUUID(),
                "Owner"
        );
        EndDragonEggService reloaded = new EndDragonEggService(null, storage(file), () -> 3800L, new RecordingTagIo());

        assertTrue(claim.isEmpty());
        assertEquals(DragonEggClaimPhase.UNCLAIMED, service.state().eggClaimPhase());
        assertEquals(DragonEggClaimPhase.UNCLAIMED, reloaded.state().eggClaimPhase());
    }

    @Test
    void existingPendingClaimIsReusedRatherThanOverwritten() {
        RecordingTagIo tagIo = new RecordingTagIo();
        EndDragonEggService service = serviceWithClock(3900L, tagIo);
        service.recordFirstDragonDeath("world_the_end");
        UUID firstOwner = UUID.randomUUID();
        DragonEggClaim pending = service.reserveFirstEggClaim(firstOwner, "First").orElseThrow();

        DragonEggClaim claim = service.claimAndTagFirstEgg(
                null,
                Material.DRAGON_EGG,
                1,
                UUID.randomUUID(),
                "Second"
        ).orElseThrow();

        assertEquals(pending, claim);
        assertEquals(firstOwner, service.state().ownerUniqueId().orElseThrow());
        assertEquals("First", service.state().ownerName().orElseThrow());
    }

    @Test
    void persistedClaimBlocksClaimAfterReload() {
        File file = stateFile();
        AtomicLong clock = new AtomicLong(4000L);
        EndDragonEggService service = new EndDragonEggService(null, storage(file), clock::get);
        service.recordFirstDragonDeath("world_the_end");
        DragonEggClaim claim = service.reserveFirstEggClaim(UUID.randomUUID(), "First").orElseThrow();
        service.commitPendingEggClaim(claim.eggId());

        EndDragonEggService reloaded = new EndDragonEggService(null, storage(file), clock::incrementAndGet);
        Optional<DragonEggClaim> secondClaim = reloaded.reserveFirstEggClaim(UUID.randomUUID(), "Second");

        assertTrue(secondClaim.isEmpty());
        assertTrue(reloaded.state().eggClaimed());
        assertEquals("First", reloaded.state().ownerName().orElseThrow());
    }

    @Test
    void ritualStatusPersistsAcrossReload() {
        File file = stateFile();
        AtomicLong clock = new AtomicLong(5000L);
        EndDragonEggService service = new EndDragonEggService(null, storage(file), clock::getAndIncrement);

        assertTrue(service.startFirstEndRitual("world_the_end"));
        assertEquals(FirstEndRitualStatus.IN_PROGRESS, service.state().ritualStatus());
        service.completeFirstEndRitual("world_the_end");

        EndDragonEggService reloaded = new EndDragonEggService(null, storage(file), clock::getAndIncrement);
        assertEquals(FirstEndRitualStatus.COMPLETED, reloaded.state().ritualStatus());
        assertEquals("world_the_end", reloaded.state().ritualWorldName().orElseThrow());
        assertFalse(reloaded.startFirstEndRitual("world_the_end"));
    }

    @Test
    void interruptedRitualCanRestartOnlyInItsOriginalWorld() {
        EndDragonEggService service = serviceWithClock(6000L);

        assertTrue(service.startFirstEndRitual("world_the_end"));
        assertTrue(service.startFirstEndRitual("world_the_end"));
        assertFalse(service.startFirstEndRitual("another_end"));
    }

    @Test
    void legacyFirstDragonDeathMigratesRitualToCompleted() throws Exception {
        File file = stateFile();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("first-dragon.killed", true);
        yaml.set("first-dragon.world", "world_the_end");
        yaml.set("first-dragon.killed-at-epoch-ms", 7000L);
        yaml.save(file);

        EndDragonEggService service = new EndDragonEggService(null, storage(file), () -> 8000L);

        assertEquals(FirstEndRitualStatus.COMPLETED, service.state().ritualStatus());
        assertEquals("world_the_end", service.state().ritualWorldName().orElseThrow());
        assertFalse(service.startFirstEndRitual("world_the_end"));
    }

    @Test
    void pendingClaimSurvivesRestartAndKeepsItsOriginalOwner() {
        File file = stateFile();
        AtomicLong clock = new AtomicLong(9000L);
        UUID firstOwner = UUID.randomUUID();
        EndDragonEggService service = new EndDragonEggService(null, storage(file), clock::getAndIncrement);
        service.recordFirstDragonDeath("world_the_end");

        DragonEggClaim pending = service.reserveFirstEggClaim(firstOwner, "First").orElseThrow();
        assertEquals(DragonEggClaimPhase.PENDING, service.state().eggClaimPhase());

        EndDragonEggService reloaded = new EndDragonEggService(null, storage(file), clock::getAndIncrement);
        DragonEggClaim retried = reloaded.reserveFirstEggClaim(UUID.randomUUID(), "Second").orElseThrow();

        assertEquals(pending, retried);
        assertEquals(firstOwner, retried.ownerUniqueId());
        assertEquals("First", retried.ownerName());
    }

    @Test
    void onlyTheReservedEggIdCanCommit() {
        EndDragonEggService service = serviceWithClock(10000L);
        service.recordFirstDragonDeath("world_the_end");
        DragonEggClaim pending = service.reserveFirstEggClaim(UUID.randomUUID(), "First").orElseThrow();

        assertFalse(service.commitPendingEggClaim(UUID.randomUUID()));
        assertEquals(DragonEggClaimPhase.PENDING, service.state().eggClaimPhase());
        assertTrue(service.commitPendingEggClaim(pending.eggId()));
        assertEquals(DragonEggClaimPhase.CLAIMED, service.state().eggClaimPhase());
        assertFalse(service.commitPendingEggClaim(pending.eggId()));
    }

    @Test
    void onlyTheReservedEggIdCanBeRolledBack() {
        FirstDragonEggState state = new FirstDragonEggState();
        DragonEggClaim pending = new DragonEggClaim(UUID.randomUUID(), UUID.randomUUID(), "First", 1L);
        state.reserveEggClaim(pending);

        assertFalse(state.clearPendingEggClaim(UUID.randomUUID()));
        assertEquals(DragonEggClaimPhase.PENDING, state.eggClaimPhase());
        assertTrue(state.clearPendingEggClaim(pending.eggId()));
        assertEquals(DragonEggClaimPhase.UNCLAIMED, state.eggClaimPhase());
    }

    @Test
    void committedClaimAndLostTrackingRemainReservedAfterReload() {
        File file = stateFile();
        EndDragonEggService service = new EndDragonEggService(null, storage(file), () -> 11000L);
        service.recordFirstDragonDeath("world_the_end");
        DragonEggClaim claim = service.reserveFirstEggClaim(UUID.randomUUID(), "First").orElseThrow();
        service.commitPendingEggClaim(claim.eggId());
        service.trackLost();

        EndDragonEggService reloaded = new EndDragonEggService(null, storage(file), () -> 12000L);

        assertEquals(DragonEggClaimPhase.CLAIMED, reloaded.state().eggClaimPhase());
        assertEquals(claim.eggId(), reloaded.state().eggId().orElseThrow());
        assertEquals(DragonEggTrackingForm.LOST, reloaded.state().eggTracking().orElseThrow().form());
        assertTrue(reloaded.reserveFirstEggClaim(UUID.randomUUID(), "Replacement").isEmpty());
    }

    @Test
    void legacyClaimLoadsAsClaimedItemTracking() throws Exception {
        File file = stateFile();
        UUID eggId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("first-dragon.killed", true);
        yaml.set("egg.claimed", true);
        yaml.set("egg.id", eggId.toString());
        yaml.set("egg.owner-uuid", ownerId.toString());
        yaml.set("egg.owner-name", "Legacy");
        yaml.set("egg.claimed-at-epoch-ms", 13000L);
        yaml.save(file);

        EndDragonEggService service = new EndDragonEggService(null, storage(file), () -> 14000L);

        assertEquals(DragonEggClaimPhase.CLAIMED, service.state().eggClaimPhase());
        assertEquals(eggId, service.state().eggId().orElseThrow());
        assertEquals(DragonEggTrackingForm.ITEM, service.state().eggTracking().orElseThrow().form());
    }

    @Test
    void identityValidationRejectsMalformedConflictingAndStackedEggs() {
        DragonEggClaim expected = new DragonEggClaim(UUID.randomUUID(), UUID.randomUUID(), "Owner", 1L);
        DragonEggClaim conflicting = new DragonEggClaim(UUID.randomUUID(), UUID.randomUUID(), "Other", 2L);

        assertEquals(
                DragonEggValidation.MALFORMED,
                EndDragonEggService.validateIdentity(Optional.of(expected), Optional.empty(), 1)
        );
        assertEquals(
                DragonEggValidation.CONFLICT,
                EndDragonEggService.validateIdentity(Optional.of(expected), Optional.of(conflicting), 1)
        );
        assertEquals(
                DragonEggValidation.INVALID_STACK,
                EndDragonEggService.validateIdentity(Optional.of(expected), Optional.of(expected), 2)
        );
        assertEquals(
                DragonEggValidation.VALID,
                EndDragonEggService.validateIdentity(Optional.of(expected), Optional.of(expected), 1)
        );
    }

    @Test
    void tagLookupRejectsEmptyAndNonEggStacksBeforeCallingNbtApi() {
        EndDragonEggService service = serviceWithClock(15000L);

        assertFalse(service.taggedFirstDragonEgg(null));
        assertTrue(service.readClaim(null).isEmpty());
        assertFalse(EndDragonEggService.canReadDragonEggNbt(null, 1));
        assertFalse(EndDragonEggService.canReadDragonEggNbt(Material.AIR, 1));
        assertFalse(EndDragonEggService.canReadDragonEggNbt(Material.STONE, 1));
        assertFalse(EndDragonEggService.canReadDragonEggNbt(Material.DRAGON_EGG, 0));
        assertTrue(EndDragonEggService.canReadDragonEggNbt(Material.DRAGON_EGG, 1));
    }

    @Test
    void incompleteCrystalLayoutIsNotStored() {
        EndDragonEggService service = serviceWithClock(16000L);

        assertFalse(service.recordCrystalLayout("world_the_end", positions(7)));

        assertTrue(service.state().crystalLayoutPositions().isEmpty());
    }

    @Test
    void legacyIncompleteCrystalLayoutCanBeReplacedByCompleteLayout() throws Exception {
        File file = stateFile();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("crystal-layout.world", "world_the_end");
        yaml.set("crystal-layout.scanned-at-epoch-ms", 1L);
        yaml.set("crystal-layout.positions", positions(7));
        yaml.save(file);
        EndDragonEggService service = new EndDragonEggService(null, storage(file), () -> 17000L);
        List<String> complete = positions(10);

        assertTrue(service.recordCrystalLayout("world_the_end", complete));

        assertEquals(complete, service.state().crystalLayoutPositions());
        assertEquals(17000L, service.state().crystalLayoutScannedAtEpochMillis());
    }

    @Test
    void completeCrystalLayoutIsNotOverwritten() {
        EndDragonEggService service = serviceWithClock(18000L);
        List<String> first = positions(10);
        List<String> replacement = positions(11);

        assertTrue(service.recordCrystalLayout("world_the_end", first));
        assertFalse(service.recordCrystalLayout("world_the_end", replacement));

        assertEquals(first, service.state().crystalLayoutPositions());
    }

    private EndDragonEggService serviceWithClock(long epochMillis) {
        return new EndDragonEggService(null, storage(stateFile()), () -> epochMillis);
    }

    private EndDragonEggService serviceWithClock(long epochMillis, EndDragonEggService.DragonEggTagIo tagIo) {
        return new EndDragonEggService(null, storage(stateFile()), () -> epochMillis, tagIo);
    }

    private DragonEggStateStorage storage(File file) {
        return new DragonEggStateStorage(file, Logger.getLogger("ZyroxEndTest"));
    }

    private File stateFile() {
        return tempDir.resolve("state.yml").toFile();
    }

    private List<String> positions(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> index + ",80," + -index)
                .toList();
    }

    private static final class RecordingTagIo implements EndDragonEggService.DragonEggTagIo {

        private DragonEggClaim claim;
        private int writeCount;
        private boolean readBack = true;

        @Override
        public void write(org.bukkit.inventory.ItemStack itemStack, DragonEggClaim claim) {
            this.claim = claim;
            writeCount++;
        }

        @Override
        public Optional<DragonEggClaim> read(org.bukkit.inventory.ItemStack itemStack) {
            if (!readBack) {
                return Optional.empty();
            }
            return Optional.ofNullable(claim);
        }
    }
}
