package de.tecca.zyrox.protect.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void appendsAndVerifiesHashChain() throws Exception {
        try (AuditStorage storage = openStorage()) {
            storage.append(AuditRecord.system("AUTH", "CRITICAL", "PASSWORD_SET", "Password changed.", Map.of()));
            storage.append(AuditRecord.system("COMMAND", "WARNING", "COMMAND_EXECUTED", "Ban command.", Map.of("command", "/ban Player")));

            HashChainStatus status = storage.verifyHashChain();

            assertTrue(status.valid());
            assertEquals(2, status.checkedEvents());
            assertEquals(2, storage.countEvents());
        }
    }

    @Test
    void filtersByActorAndCategory() throws Exception {
        try (AuditStorage storage = openStorage()) {
            storage.append(new AuditRecord(
                    "COMMAND",
                    "WARNING",
                    "PLAYER",
                    Optional.empty(),
                    "AdminOne",
                    "PLAYER",
                    "",
                    "TargetOne",
                    "COMMAND_EXECUTED",
                    "Command.",
                    Map.of(),
                    ""
            ));
            storage.append(AuditRecord.system("PACKET", "INFO", "PACKET_RECEIVED", "Packet.", Map.of()));

            List<AuditEvent> events = storage.query(new AuditQuery("COMMAND", "", "Admin", "", "", 25, 0));

            assertEquals(1, events.size());
            assertEquals("COMMAND", events.getFirst().category());
            assertEquals("AdminOne", events.getFirst().actorName());
        }
    }

    @Test
    void queryLimitIsCappedAtFiveHundred() {
        AuditQuery query = new AuditQuery("", "", "", "", "", 999, -5);

        assertEquals(500, query.limit());
        assertEquals(0, query.offset());
    }

    private AuditStorage openStorage() throws Exception {
        AuditStorage storage = new AuditStorage(null, new File(tempDir.toFile(), "audit.db"));
        storage.open();
        return storage;
    }
}
