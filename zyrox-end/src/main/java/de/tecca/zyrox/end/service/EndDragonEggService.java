package de.tecca.zyrox.end.service;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class EndDragonEggService {

    private static final int MIN_CRYSTAL_LAYOUT_POSITIONS = 10;

    private final JavaPlugin plugin;
    private final DragonEggStateStorage storage;
    private final LongSupplier clock;
    private final DragonEggTagIo tagIo;
    private FirstDragonEggState state;

    public EndDragonEggService(JavaPlugin plugin, DragonEggStateStorage storage) {
        this(plugin, storage, System::currentTimeMillis);
    }

    EndDragonEggService(JavaPlugin plugin, DragonEggStateStorage storage, LongSupplier clock) {
        this(plugin, storage, clock, null);
    }

    EndDragonEggService(JavaPlugin plugin, DragonEggStateStorage storage, LongSupplier clock, DragonEggTagIo tagIo) {
        this.plugin = plugin;
        this.storage = storage;
        this.clock = clock;
        this.tagIo = tagIo == null ? new NbtDragonEggTagIo() : tagIo;
        this.state = storage.load();
    }

    public synchronized boolean recordFirstDragonDeath(String worldName) {
        if (state.firstDragonKilled() || state.eggClaimed()) {
            return false;
        }

        long killedAt = clock.getAsLong();
        state.markFirstDragonKilled(worldName, killedAt);
        if (state.ritualStatus() != FirstEndRitualStatus.COMPLETED) {
            state.markRitualCompleted(worldName, killedAt);
        }
        storage.save(state);
        return true;
    }

    public synchronized Optional<DragonEggClaim> claimAndTagFirstEgg(
            ItemStack itemStack,
            UUID ownerUniqueId,
            String ownerName
    ) {
        return claimAndTagFirstEgg(
                itemStack,
                itemStack == null ? null : itemStack.getType(),
                itemStack == null ? 0 : itemStack.getAmount(),
                ownerUniqueId,
                ownerName
        );
    }

    synchronized Optional<DragonEggClaim> claimAndTagFirstEgg(
            ItemStack itemStack,
            Material material,
            int amount,
            UUID ownerUniqueId,
            String ownerName
    ) {
        if (!state.firstDragonKilled() || state.eggClaimed()
                || material != Material.DRAGON_EGG || amount != 1
                || ownerUniqueId == null || ownerName == null || ownerName.isBlank()) {
            return Optional.empty();
        }

        boolean createdPendingClaim = state.pendingEggClaim().isEmpty();
        DragonEggClaim claim = reserveFirstEggClaim(ownerUniqueId, ownerName).orElse(null);
        if (claim == null) {
            return Optional.empty();
        }
        try {
            tagIo.write(itemStack, claim);
            if (tagIo.read(itemStack).filter(read -> read.eggId().equals(claim.eggId())).isEmpty()) {
                rollbackNewPendingClaim(createdPendingClaim, claim.eggId());
                return Optional.empty();
            }
        } catch (RuntimeException exception) {
            rollbackNewPendingClaim(createdPendingClaim, claim.eggId());
            return Optional.empty();
        }
        if (!commitPendingEggClaim(claim.eggId())) {
            return Optional.empty();
        }
        return Optional.of(claim);
    }

    synchronized Optional<DragonEggClaim> reserveFirstEggClaim(UUID ownerUniqueId, String ownerName) {
        if (!state.firstDragonKilled() || state.eggClaimed()) {
            return Optional.empty();
        }
        if (state.pendingEggClaim().isPresent()) {
            return state.pendingEggClaim();
        }
        DragonEggClaim claim = new DragonEggClaim(UUID.randomUUID(), ownerUniqueId, ownerName, clock.getAsLong());
        state.reserveEggClaim(claim);
        storage.save(state);
        return Optional.of(claim);
    }

    synchronized boolean commitPendingEggClaim(UUID eggId) {
        if (!state.commitPendingEggClaim(eggId)) {
            return false;
        }
        state.trackEgg(itemTracking(null));
        storage.save(state);
        return true;
    }

    private void rollbackNewPendingClaim(boolean createdPendingClaim, UUID eggId) {
        if (createdPendingClaim && state.clearPendingEggClaim(eggId)) {
            storage.save(state);
        }
    }

    public synchronized DragonEggValidation validateAndCanonicalize(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.DRAGON_EGG || !taggedFirstDragonEgg(itemStack)) {
            return DragonEggValidation.UNTAGGED;
        }
        Optional<DragonEggClaim> embedded = readClaim(itemStack);
        Optional<DragonEggClaim> expected = state.claimedEgg().or(state::pendingEggClaim);
        DragonEggValidation validation = validateIdentity(expected, embedded, itemStack.getAmount());
        if (validation != DragonEggValidation.VALID) {
            return validation;
        }

        writeCanonicalTag(itemStack, expected.get());
        if (state.eggClaimPhase() == DragonEggClaimPhase.PENDING) {
            commitPendingEggClaim(expected.get().eggId());
        }
        return DragonEggValidation.VALID;
    }

    static DragonEggValidation validateIdentity(
            Optional<DragonEggClaim> expected,
            Optional<DragonEggClaim> embedded,
            int amount
    ) {
        if (amount != 1) {
            return DragonEggValidation.INVALID_STACK;
        }
        if (embedded.isEmpty()) {
            return DragonEggValidation.MALFORMED;
        }
        if (expected.isEmpty() || !expected.get().eggId().equals(embedded.get().eggId())) {
            return DragonEggValidation.CONFLICT;
        }
        return DragonEggValidation.VALID;
    }

    public synchronized boolean restoreCanonicalEgg(ItemStack itemStack) {
        Optional<DragonEggClaim> claim = state.claimedEgg();
        if (claim.isEmpty() || itemStack == null || itemStack.getType() != Material.DRAGON_EGG
                || itemStack.getAmount() != 1) {
            return false;
        }
        writeCanonicalTag(itemStack, claim.get());
        return true;
    }

    public synchronized boolean recordCrystalLayout(String worldName, List<String> positions) {
        if (positions == null || positions.size() < MIN_CRYSTAL_LAYOUT_POSITIONS
                || state.crystalLayoutPositions().size() >= MIN_CRYSTAL_LAYOUT_POSITIONS) {
            return false;
        }

        state.recordCrystalLayout(worldName, clock.getAsLong(), positions);
        storage.save(state);
        return true;
    }

    public synchronized boolean startFirstEndRitual(String worldName) {
        if (state.firstDragonKilled() || state.ritualStatus() == FirstEndRitualStatus.COMPLETED) {
            return false;
        }
        if (state.ritualStatus() == FirstEndRitualStatus.IN_PROGRESS
                && state.ritualWorldName().filter(existing -> !existing.equals(worldName)).isPresent()) {
            return false;
        }
        state.markRitualInProgress(worldName, clock.getAsLong());
        storage.save(state);
        return true;
    }

    public synchronized void completeFirstEndRitual(String worldName) {
        state.markRitualCompleted(worldName, clock.getAsLong());
        storage.save(state);
    }

    public synchronized FirstDragonEggState state() {
        return state;
    }

    public synchronized void reload() {
        state = storage.load();
    }

    public boolean taggedFirstDragonEgg(ItemStack itemStack) {
        if (itemStack == null || !canReadDragonEggNbt(itemStack.getType(), itemStack.getAmount())) {
            return false;
        }
        return NBT.get(itemStack, nbt -> {
            ReadableNBT zyrox = nbt.getCompound("zyrox");
            if (zyrox == null) {
                return false;
            }
            ReadableNBT end = zyrox.getCompound("end");
            return end != null && end.getBoolean("first_dragon_egg");
        });
    }

    private void writeCanonicalTag(ItemStack itemStack, DragonEggClaim claim) {
        NBT.modify(itemStack, nbt -> {
            ReadWriteNBT zyrox = nbt.getOrCreateCompound("zyrox");
            ReadWriteNBT end = zyrox.getOrCreateCompound("end");
            end.setBoolean("first_dragon_egg", true);
            end.setInteger("schema_version", 1);
            end.setString("egg_id", claim.eggId().toString());
            end.setString("owner_uuid", claim.ownerUniqueId().toString());
            end.setString("owner_name", claim.ownerName());
            end.setLong("claimed_at_epoch_ms", claim.claimedAtEpochMillis());
        });

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return;
        }

        String title = plugin == null
                ? "Erstes Drachenei"
                : plugin.getConfig().getString("dragon-egg.lore.title", "Erstes Drachenei");
        String ownerFormat = plugin == null
                ? "Besitzer: %player%"
                : plugin.getConfig().getString("dragon-egg.lore.owner-format", "Besitzer: %player%");
        String ownerLine = ownerFormat
                .replace("%player%", claim.ownerName());
        meta.displayName(Component.text(title));
        meta.lore(List.of(Component.text(title), Component.text(ownerLine)));
        itemStack.setItemMeta(meta);
    }

    public Optional<DragonEggClaim> readClaim(ItemStack itemStack) {
        if (itemStack == null || !canReadDragonEggNbt(itemStack.getType(), itemStack.getAmount())) {
            return Optional.empty();
        }
        return NBT.get(itemStack, nbt -> {
            ReadableNBT zyrox = nbt.getCompound("zyrox");
            ReadableNBT end = zyrox == null ? null : zyrox.getCompound("end");
            if (end == null || !end.getBoolean("first_dragon_egg")) {
                return Optional.<DragonEggClaim>empty();
            }
            UUID eggId = uuidOrNull(end.getString("egg_id"));
            UUID ownerId = uuidOrNull(end.getString("owner_uuid"));
            String ownerName = end.getString("owner_name");
            if (eggId == null || ownerId == null || ownerName == null || ownerName.isBlank()) {
                return Optional.<DragonEggClaim>empty();
            }
            return Optional.of(new DragonEggClaim(
                    eggId,
                    ownerId,
                    ownerName,
                    end.getLong("claimed_at_epoch_ms")
            ));
        });
    }

    public synchronized void trackItem(UUID entityUniqueId) {
        if (state.eggClaimed()) {
            state.trackEgg(itemTracking(entityUniqueId));
            storage.save(state);
        }
    }

    public synchronized void trackPlaced(Location location) {
        if (!state.eggClaimed() || location == null || location.getWorld() == null) {
            return;
        }
        World world = location.getWorld();
        state.trackEgg(new DragonEggTrackingState(
                DragonEggTrackingForm.PLACED,
                world.getUID(),
                world.getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                null,
                clock.getAsLong()
        ));
        storage.save(state);
    }

    public synchronized void trackFalling(Location source, UUID entityUniqueId) {
        if (!state.eggClaimed() || source == null || source.getWorld() == null) {
            return;
        }
        World world = source.getWorld();
        state.trackEgg(new DragonEggTrackingState(
                DragonEggTrackingForm.FALLING,
                world.getUID(),
                world.getName(),
                source.getBlockX(),
                source.getBlockY(),
                source.getBlockZ(),
                entityUniqueId,
                clock.getAsLong()
        ));
        storage.save(state);
    }

    public synchronized void trackLost() {
        if (state.eggClaimed()) {
            state.trackEgg(new DragonEggTrackingState(
                    DragonEggTrackingForm.LOST,
                    null,
                    null,
                    0,
                    0,
                    0,
                    null,
                    clock.getAsLong()
            ));
            storage.save(state);
        }
    }

    public synchronized boolean isTrackedBlock(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return state.eggTracking()
                .filter(tracking -> tracking.form() == DragonEggTrackingForm.PLACED)
                .filter(tracking -> sameWorld(tracking, location.getWorld()))
                .filter(tracking -> tracking.blockX() == location.getBlockX())
                .filter(tracking -> tracking.blockY() == location.getBlockY())
                .filter(tracking -> tracking.blockZ() == location.getBlockZ())
                .isPresent();
    }

    public synchronized boolean isTrackedFalling(UUID entityUniqueId) {
        return entityUniqueId != null && state.eggTracking()
                .filter(tracking -> tracking.form() == DragonEggTrackingForm.FALLING)
                .flatMap(DragonEggTrackingState::entityId)
                .filter(entityUniqueId::equals)
                .isPresent();
    }

    public synchronized boolean bindLegacyBlock(Location location) {
        if (!state.eggClaimed() || location == null || location.getBlock().getType() != Material.DRAGON_EGG) {
            return false;
        }
        trackPlaced(location);
        return true;
    }

    private DragonEggTrackingState itemTracking(UUID entityUniqueId) {
        return new DragonEggTrackingState(
                DragonEggTrackingForm.ITEM,
                null,
                null,
                0,
                0,
                0,
                entityUniqueId,
                clock.getAsLong()
        );
    }

    private boolean sameWorld(DragonEggTrackingState tracking, World world) {
        return tracking.worldId().map(world.getUID()::equals).orElseGet(() ->
                tracking.world().filter(world.getName()::equals).isPresent());
    }

    private UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static boolean canReadDragonEggNbt(Material material, int amount) {
        // Requiring the exact material also rejects every AIR variant without
        // invoking Material#isAir, which depends on a running Bukkit registry.
        return material == Material.DRAGON_EGG && amount > 0;
    }

    interface DragonEggTagIo {
        void write(ItemStack itemStack, DragonEggClaim claim);

        Optional<DragonEggClaim> read(ItemStack itemStack);
    }

    private final class NbtDragonEggTagIo implements DragonEggTagIo {

        @Override
        public void write(ItemStack itemStack, DragonEggClaim claim) {
            writeCanonicalTag(itemStack, claim);
        }

        @Override
        public Optional<DragonEggClaim> read(ItemStack itemStack) {
            return readClaim(itemStack);
        }
    }
}
