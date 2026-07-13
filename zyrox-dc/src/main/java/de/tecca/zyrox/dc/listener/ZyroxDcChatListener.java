package de.tecca.zyrox.dc.listener;

import de.tecca.zyrox.dc.discord.DiscordBotService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZyroxDcChatListener implements Listener {

    private final JavaPlugin plugin;
    private final DiscordBotService discordBot;

    public ZyroxDcChatListener(JavaPlugin plugin, DiscordBotService discordBot) {
        this.plugin = plugin;
        this.discordBot = discordBot;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat-sync.enabled", false)) {
            return;
        }
        if (discordBot == null || !discordBot.isRunning()) {
            return;
        }

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        discordBot.sendMinecraftChatMessage(event.getPlayer().getName(), message);
    }
}
