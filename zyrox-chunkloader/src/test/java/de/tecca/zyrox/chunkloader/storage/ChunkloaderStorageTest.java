package de.tecca.zyrox.chunkloader.storage;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkloaderStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndLoadsByLocationWithChunkCoordinates() throws Exception {
        File database = temporaryDirectory.resolve("chunkloaders.db").toFile();
        Player owner = player("Owner");

        try (ChunkloaderStorage storage = storage(database)) {
            storage.open();
            ChunkloaderRecord loader = storage.createLoader(new TestLocation("world", -17, 64, 31).location(), owner);
            ChunkloaderRecord loaded = storage.findByLocation(new TestLocation("world", -17, 64, 31).location()).orElseThrow();

            assertEquals(loader.id(), loaded.id());
            assertEquals(-2, loaded.chunkX());
            assertEquals(1, loaded.chunkZ());
            assertEquals(1, loaded.priority());
        }
    }

    @Test
    void prioritiesCompactAfterDelete() throws Exception {
        File database = temporaryDirectory.resolve("chunkloaders.db").toFile();
        Player owner = player("Owner");

        try (ChunkloaderStorage storage = storage(database)) {
            storage.open();
            ChunkloaderRecord first = storage.createLoader(new TestLocation("world", 0, 64, 0).location(), owner);
            ChunkloaderRecord second = storage.createLoader(new TestLocation("world", 16, 64, 0).location(), owner);
            ChunkloaderRecord third = storage.createLoader(new TestLocation("world", 32, 64, 0).location(), owner);

            assertTrue(storage.deleteLoader(first.id()));

            assertEquals(1, storage.findById(second.id()).orElseThrow().priority());
            assertEquals(2, storage.findById(third.id()).orElseThrow().priority());
        }
    }

    @Test
    void priorityMoveSwapsAdjacentLoaders() throws Exception {
        File database = temporaryDirectory.resolve("chunkloaders.db").toFile();
        Player owner = player("Owner");

        try (ChunkloaderStorage storage = storage(database)) {
            storage.open();
            ChunkloaderRecord first = storage.createLoader(new TestLocation("world", 0, 64, 0).location(), owner);
            ChunkloaderRecord second = storage.createLoader(new TestLocation("world", 16, 64, 0).location(), owner);

            storage.movePriority(second.id(), -1);

            assertEquals(2, storage.findById(first.id()).orElseThrow().priority());
            assertEquals(1, storage.findById(second.id()).orElseThrow().priority());
        }
    }

    @Test
    void payCycleConsumesReserveAndSetsDeadline() throws Exception {
        File database = temporaryDirectory.resolve("chunkloaders.db").toFile();
        Player owner = player("Owner");
        Instant paidUntil = Instant.parse("2026-07-15T13:00:00Z");

        try (ChunkloaderStorage storage = storage(database)) {
            storage.open();
            ChunkloaderRecord loader = storage.createLoader(new TestLocation("world", 0, 64, 0).location(), owner);
            storage.addReserve(loader.id(), 5);

            assertTrue(storage.payCycle(loader.id(), 2, paidUntil));
            ChunkloaderRecord updated = storage.findById(loader.id()).orElseThrow();

            assertEquals(3, updated.reserveBlocks());
            assertEquals(paidUntil.toString(), updated.paidUntil());
            assertFalse(storage.payCycle(loader.id(), 4, paidUntil.plusSeconds(1)));
        }
    }

    @Test
    void paidTimeIsPausedAcrossStoredShutdown() throws Exception {
        File database = temporaryDirectory.resolve("chunkloaders.db").toFile();
        MutableClock clock = new MutableClock(Instant.parse("2026-07-15T12:00:00Z"));
        Player owner = player("Owner");

        try (ChunkloaderStorage storage = new ChunkloaderStorage(null, database, clock)) {
            storage.open();
            ChunkloaderRecord loader = storage.createLoader(new TestLocation("world", 0, 64, 0).location(), owner);
            storage.addReserve(loader.id(), 5);
            storage.payCycle(loader.id(), 1, Instant.parse("2026-07-15T13:00:00Z"));
            storage.markShutdownTime();
            clock.setInstant(Instant.parse("2026-07-15T14:00:00Z"));

            assertEquals(1, storage.pausePaidTimeSinceLastShutdown());

            ChunkloaderRecord updated = storage.findById(loader.id()).orElseThrow();
            assertEquals("2026-07-15T15:00:00Z", updated.paidUntil());
        }
    }

    private static ChunkloaderStorage storage(File database) {
        return new ChunkloaderStorage(null, database, Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneOffset.UTC));
    }

    private static Player player(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(name.getBytes());
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "toString" -> name;
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
