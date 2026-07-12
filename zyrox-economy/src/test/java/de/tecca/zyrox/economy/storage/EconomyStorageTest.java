package de.tecca.zyrox.economy.storage;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void legacyLedgerStillOpensForReview() throws Exception {
        File database = temporaryDirectory.resolve("economy.db").toFile();
        OfflinePlayer player = player("Legacy");

        try (EconomyStorage storage = new EconomyStorage(null, database)) {
            storage.open();
            storage.recordWithdrawal(player, 300, 0);

            assertTrue(storage.hasAnyLedgerBalances());
            assertEquals(300, storage.shopReserveBalance());
        }
    }

    private static OfflinePlayer player(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(name.getBytes());
        return (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class<?>[]{OfflinePlayer.class},
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
}
