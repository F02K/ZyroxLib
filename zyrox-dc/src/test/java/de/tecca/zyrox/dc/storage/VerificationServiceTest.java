package de.tecca.zyrox.dc.storage;

import de.tecca.zyrox.dc.minecraft.MinecraftAccountService;
import de.tecca.zyrox.dc.service.VerificationService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class VerificationServiceTest {

    private static final String GROUP_BAN_REASON = "Linked Minecraft account is banned.";
    private static final String GROUP_BAN_SOURCE = "ZyroxDC";

    @TempDir
    private Path tempDir;

    @Test
    void discordUnbanRestoresWhitelistedAccess() throws Exception {
        try (ServiceFixture fixture = fixture(true)) {
            fixture.link("discord-1", "Steve", "00000000-0000-0000-0000-000000000001");
            fixture.storage.upsertDiscordBan("discord-1", "Discord ban");
            MinecraftAccountService.LinkedMinecraftIdentity identity = identity("Steve", "00000000-0000-0000-0000-000000000001");
            when(fixture.minecraftAccounts.isBanned(identity)).thenReturn(false);
            when(fixture.minecraftAccounts.isWhitelisted(identity)).thenReturn(false);

            fixture.service.syncDiscordUnban("discord-1");

            assertTrue(fixture.storage.findDiscordBan("discord-1").isEmpty());
            verify(fixture.minecraftAccounts).setWhitelisted("Steve", true);
        }
    }

    @Test
    void startupBanSyncRestoresAccessForDiscordBansMissingFromFreshList() throws Exception {
        try (ServiceFixture fixture = fixture(true)) {
            fixture.link("discord-1", "Steve", "00000000-0000-0000-0000-000000000001");
            fixture.storage.upsertDiscordBan("discord-1", "Discord ban");
            MinecraftAccountService.LinkedMinecraftIdentity identity = identity("Steve", "00000000-0000-0000-0000-000000000001");
            when(fixture.minecraftAccounts.isBanned(identity)).thenReturn(false);
            when(fixture.minecraftAccounts.isWhitelisted(identity)).thenReturn(false);

            fixture.service.replaceDiscordBans(List.of());

            assertTrue(fixture.storage.findDiscordBan("discord-1").isEmpty());
            verify(fixture.minecraftAccounts).setWhitelisted("Steve", true);
        }
    }

    @Test
    void externalMinecraftBanStillPropagatesToLinkedAccounts() throws Exception {
        try (ServiceFixture fixture = fixture(true)) {
            fixture.link("discord-1", "Steve", "00000000-0000-0000-0000-000000000001");
            fixture.link("discord-1", "Alex", "00000000-0000-0000-0000-000000000002");
            MinecraftAccountService.LinkedMinecraftIdentity steve = identity("Steve", "00000000-0000-0000-0000-000000000001");
            MinecraftAccountService.LinkedMinecraftIdentity alex = identity("Alex", "00000000-0000-0000-0000-000000000002");
            when(fixture.minecraftAccounts.isBanned(steve)).thenReturn(true);
            when(fixture.minecraftAccounts.isZyroxDcPropagatedBan(steve, GROUP_BAN_REASON, GROUP_BAN_SOURCE)).thenReturn(false);
            when(fixture.minecraftAccounts.isBanned(alex)).thenReturn(false);
            when(fixture.minecraftAccounts.isWhitelisted(alex)).thenReturn(true);

            fixture.service.reconcileNativeMinecraftBans();

            verify(fixture.minecraftAccounts).ban(alex, GROUP_BAN_REASON, GROUP_BAN_SOURCE);
            verify(fixture.minecraftAccounts).setWhitelisted("Alex", false);
        }
    }

    @Test
    void nativeMinecraftUnbanClearsOnlyZyroxPropagatedBansAndRestoresWhitelist() throws Exception {
        try (ServiceFixture fixture = fixture(true)) {
            fixture.link("discord-1", "Steve", "00000000-0000-0000-0000-000000000001");
            fixture.link("discord-1", "Alex", "00000000-0000-0000-0000-000000000002");
            MinecraftAccountService.LinkedMinecraftIdentity steve = identity("Steve", "00000000-0000-0000-0000-000000000001");
            MinecraftAccountService.LinkedMinecraftIdentity alex = identity("Alex", "00000000-0000-0000-0000-000000000002");
            AtomicBoolean alexBanned = new AtomicBoolean(true);
            when(fixture.minecraftAccounts.isBanned(steve)).thenReturn(false);
            when(fixture.minecraftAccounts.isBanned(alex)).thenAnswer(ignored -> alexBanned.get());
            when(fixture.minecraftAccounts.isZyroxDcPropagatedBan(alex, GROUP_BAN_REASON, GROUP_BAN_SOURCE)).thenReturn(true);
            when(fixture.minecraftAccounts.pardonIfZyroxDcPropagatedBan(alex, GROUP_BAN_REASON, GROUP_BAN_SOURCE)).thenAnswer(ignored -> {
                alexBanned.set(false);
                return true;
            });
            when(fixture.minecraftAccounts.isWhitelisted(steve)).thenReturn(false);
            when(fixture.minecraftAccounts.isWhitelisted(alex)).thenReturn(false);

            fixture.service.reconcileNativeMinecraftBans();

            verify(fixture.minecraftAccounts).pardonIfZyroxDcPropagatedBan(alex, GROUP_BAN_REASON, GROUP_BAN_SOURCE);
            verify(fixture.minecraftAccounts).setWhitelisted("Steve", true);
            verify(fixture.minecraftAccounts).setWhitelisted("Alex", true);
        }
    }

    @Test
    void manualMinecraftBansAreNeverPardoned() throws Exception {
        try (ServiceFixture fixture = fixture(true)) {
            fixture.link("discord-1", "Steve", "00000000-0000-0000-0000-000000000001");
            fixture.link("discord-1", "Alex", "00000000-0000-0000-0000-000000000002");
            MinecraftAccountService.LinkedMinecraftIdentity steve = identity("Steve", "00000000-0000-0000-0000-000000000001");
            MinecraftAccountService.LinkedMinecraftIdentity alex = identity("Alex", "00000000-0000-0000-0000-000000000002");
            when(fixture.minecraftAccounts.isBanned(steve)).thenReturn(true);
            when(fixture.minecraftAccounts.isZyroxDcPropagatedBan(steve, GROUP_BAN_REASON, GROUP_BAN_SOURCE)).thenReturn(false);
            when(fixture.minecraftAccounts.isBanned(alex)).thenReturn(true);

            fixture.service.reconcileNativeMinecraftBans();

            verify(fixture.minecraftAccounts, never()).pardonIfZyroxDcPropagatedBan(steve, GROUP_BAN_REASON, GROUP_BAN_SOURCE);
            verify(fixture.minecraftAccounts, never()).pardonIfZyroxDcPropagatedBan(alex, GROUP_BAN_REASON, GROUP_BAN_SOURCE);
        }
    }

    @Test
    void minecraftUnbanDoesNotRestoreWhitelistWhileDiscordBanStillExists() throws Exception {
        try (ServiceFixture fixture = fixture(true)) {
            fixture.link("discord-1", "Alex", "00000000-0000-0000-0000-000000000002");
            fixture.storage.upsertDiscordBan("discord-1", "Discord ban");
            MinecraftAccountService.LinkedMinecraftIdentity alex = identity("Alex", "00000000-0000-0000-0000-000000000002");
            AtomicBoolean alexBanned = new AtomicBoolean(true);
            when(fixture.minecraftAccounts.isBanned(alex)).thenAnswer(ignored -> alexBanned.get());
            when(fixture.minecraftAccounts.isZyroxDcPropagatedBan(alex, GROUP_BAN_REASON, GROUP_BAN_SOURCE)).thenReturn(true);
            when(fixture.minecraftAccounts.pardonIfZyroxDcPropagatedBan(alex, GROUP_BAN_REASON, GROUP_BAN_SOURCE)).thenAnswer(ignored -> {
                alexBanned.set(false);
                return true;
            });

            fixture.service.reconcileNativeMinecraftBans();

            verify(fixture.minecraftAccounts).pardonIfZyroxDcPropagatedBan(alex, GROUP_BAN_REASON, GROUP_BAN_SOURCE);
            verify(fixture.minecraftAccounts, never()).setWhitelisted(anyString(), anyBoolean());
        }
    }

    @Test
    void autoWhitelistDisabledDoesNotRestoreAccess() throws Exception {
        try (ServiceFixture fixture = fixture(false)) {
            fixture.link("discord-1", "Steve", "00000000-0000-0000-0000-000000000001");
            fixture.storage.upsertDiscordBan("discord-1", "Discord ban");

            fixture.service.syncDiscordUnban("discord-1");

            verify(fixture.minecraftAccounts, never()).setWhitelisted(anyString(), anyBoolean());
        }
    }

    private ServiceFixture fixture(boolean autoWhitelist) throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("ban-sync.enabled", true);
        config.set("ban-sync.unwhitelist-on-discord-ban", true);
        config.set("behavior.auto-whitelist", autoWhitelist);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("VerificationServiceTest"));

        ZyroxDcStorage storage = new ZyroxDcStorage(plugin, tempDir.resolve(UUID.randomUUID() + ".db").toFile());
        storage.open();
        MinecraftAccountService minecraftAccounts = mock(MinecraftAccountService.class);
        VerificationService service = new VerificationService(plugin, storage, minecraftAccounts);
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        return new ServiceFixture(storage, minecraftAccounts, service, bukkit);
    }

    private MinecraftAccountService.LinkedMinecraftIdentity identity(String username, String uuid) {
        return new MinecraftAccountService.LinkedMinecraftIdentity(username, uuid);
    }

    private record ServiceFixture(
            ZyroxDcStorage storage,
            MinecraftAccountService minecraftAccounts,
            VerificationService service,
            MockedStatic<Bukkit> bukkit
    ) implements AutoCloseable {

        void link(String discordId, String minecraftUsername, String minecraftUuid) throws Exception {
            storage.upsertLink(discordId, "DiscordUser", minecraftUsername, minecraftUuid);
        }

        @Override
        public void close() throws Exception {
            bukkit.close();
            storage.close();
        }
    }
}
