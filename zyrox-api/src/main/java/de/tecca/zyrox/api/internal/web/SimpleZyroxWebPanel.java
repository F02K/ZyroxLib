package de.tecca.zyrox.api.internal.web;

import de.tecca.zyrox.api.ZyroxApi;
import de.tecca.zyrox.api.web.ZyroxWebPanel;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public final class SimpleZyroxWebPanel implements ZyroxWebPanel {

    private final JavaPlugin plugin;
    private final String bindAddress;
    private final int port;
    private final String publicUrl;
    private final Duration tokenLifetime;
    private final WebSessionStore sessionStore;
    private DashboardHttpServer httpServer;

    public SimpleZyroxWebPanel(
            JavaPlugin plugin,
            String bindAddress,
            int port,
            String publicUrl,
            Duration tokenLifetime,
            WebSessionStore sessionStore
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        this.port = port;
        this.publicUrl = stripTrailingSlash(Objects.requireNonNull(publicUrl, "publicUrl"));
        this.tokenLifetime = Objects.requireNonNull(tokenLifetime, "tokenLifetime");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore");
    }

    public void start(ZyroxApi api) throws IOException {
        if (httpServer != null) {
            return;
        }

        httpServer = new DashboardHttpServer(plugin, api, sessionStore, bindAddress, port, usesSecureCookies());
        httpServer.start();
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }

    @Override
    public URI createSessionLink(Player player) {
        Objects.requireNonNull(player, "player");
        String token = sessionStore.createPendingToken(player, tokenLifetime);
        return URI.create(publicUrl + "/session/" + token);
    }

    @Override
    public boolean isRunning() {
        return httpServer != null && httpServer.isRunning();
    }

    @Override
    public int port() {
        return port;
    }

    private static String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean usesSecureCookies() {
        return publicUrl.regionMatches(true, 0, "https://", 0, "https://".length());
    }
}
