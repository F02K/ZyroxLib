package de.tecca.zyrox.dc.discord;

import de.tecca.zyrox.dc.chat.ChatSyncFormatter;
import de.tecca.zyrox.dc.market.DiscordMarketplaceHandler;
import de.tecca.zyrox.dc.market.DiscordMarketplaceShop;
import de.tecca.zyrox.dc.service.VerificationService;
import de.tecca.zyrox.dc.storage.DiscordBan;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class DiscordBotService extends ListenerAdapter {

    public static final String VERIFY_BUTTON_ID = "zyroxdc_verify";
    public static final String VERIFY_MODAL_ID = "zyroxdc_verify_modal";
    public static final String USERNAME_INPUT_ID = "minecraft_username";
    static final int MARKET_RESULT_LIMIT = 25;

    private static final Duration MARKET_SESSION_TTL = Duration.ofMinutes(10);

    private final JavaPlugin plugin;
    private final VerificationService verificationService;
    private final Map<String, MarketCommandSession> marketSessions = new ConcurrentHashMap<>();
    private final AtomicLong marketSessionIds = new AtomicLong();
    private JDA jda;
    private String guildId;
    private DiscordMarketplaceHandler marketplaceHandler;

    public DiscordBotService(JavaPlugin plugin, VerificationService verificationService) {
        this.plugin = plugin;
        this.verificationService = verificationService;
    }

    public void start(String token, String guildId) throws InterruptedException {
        stop();

        this.guildId = guildId;
        jda = JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MODERATION,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT
                )
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(this)
                .build();
        jda.awaitReady();
        syncExistingBansOnStart();
        upsertMarketCommand();
        plugin.getLogger().info("ZyroxDC connected to Discord.");
    }

    public void stop() {
        if (jda != null) {
            jda.shutdownNow();
            jda = null;
        }
        marketSessions.clear();
    }

    public boolean isRunning() {
        return jda != null;
    }

    public void setMarketplaceHandler(DiscordMarketplaceHandler marketplaceHandler) {
        this.marketplaceHandler = marketplaceHandler;
        upsertMarketCommand();
    }

    public void sendVerificationMessage(String channelId, String title, String description, String buttonLabel) {
        TextChannel channel = requireTextChannel(channelId);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(new Color(88, 166, 255))
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build())
                .addComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(
                        net.dv8tion.jda.api.components.buttons.Button.primary(VERIFY_BUTTON_ID, buttonLabel)
                ))
                .queue();
    }

    public void sendMinecraftChatMessage(String playerName, String message) {
        if (!isChatSyncEnabled()) {
            return;
        }

        String channelId = plugin.getConfig().getString("chat-sync.channel-id", "");
        if (channelId == null || channelId.isBlank() || "0".equals(channelId)) {
            return;
        }

        TextChannel channel = jda == null ? null : jda.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }

        String text = ChatSyncFormatter.format(
                plugin.getConfig().getString("chat-sync.minecraft-to-discord-format", "**{player}**: {message}"),
                Map.of(
                        "player", playerName,
                        "message", message
                ),
                plugin.getConfig().getInt("chat-sync.max-message-length", 256)
        );
        if (!text.isBlank()) {
            channel.sendMessage(text).queue();
        }
    }

    public void sendMarketplaceShopPost(String eventType, DiscordMarketplaceShop shop) {
        if (!isMarketplaceEnabled() || shop == null) {
            return;
        }

        String channelId = plugin.getConfig().getString("marketplace.channel-id", "");
        if (channelId == null || channelId.isBlank() || "0".equals(channelId) || jda == null) {
            return;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return;
        }

        String title = "CREATED".equals(eventType)
                ? "New shop #" + shop.id()
                : "Shop updated #" + shop.id();
        channel.sendMessageEmbeds(marketplaceEmbed(title, shop, new Color(72, 187, 120)).build()).queue();
    }

    public void sendMarketplaceRevenueReminder(String discordId, DiscordMarketplaceShop shop) {
        if (!isMarketplaceEnabled() || discordId == null || discordId.isBlank() || shop == null || jda == null) {
            return;
        }

        jda.retrieveUserById(discordId).queue(user -> user.openPrivateChannel()
                .flatMap(channel -> channel.sendMessageEmbeds(marketplaceEmbed(
                        "Shop revenue waiting #" + shop.id(),
                        shop,
                        new Color(246, 173, 85)
                ).setDescription("Your shop has " + shop.revenueDiamonds() + " diamond(s) waiting to be collected.").build()))
                .queue(null, failure -> plugin.getLogger().warning("Could not DM marketplace reminder to Discord user "
                        + discordId + ": " + failure.getMessage())), failure -> plugin.getLogger().warning(
                "Could not resolve Discord user for marketplace reminder " + discordId + ": " + failure.getMessage()));
    }

    public List<DiscordMemberSnapshot> humanMembers() {
        Guild guild = requireGuild();
        return guild.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .map(member -> new DiscordMemberSnapshot(member.getId(), displayName(member)))
                .sorted((left, right) -> left.displayName().compareToIgnoreCase(right.displayName()))
                .toList();
    }

    public void syncVerifiedRole(String discordId, String roleId) {
        if (roleId == null || roleId.isBlank()) {
            return;
        }

        Guild guild = requireGuild();
        Role role = guild.getRoleById(roleId);
        Member member = guild.getMemberById(discordId);
        if (role == null || member == null) {
            return;
        }

        guild.addRoleToMember(member, role).queue();
    }

    public String displayName(String discordId) {
        Guild guild = jda == null ? null : jda.getGuildById(guildId);
        if (guild == null) {
            return discordId;
        }

        Member member = guild.getMemberById(discordId);
        return member == null ? discordId : displayName(member);
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        if (!isBanSyncEnabled() || !isConfiguredGuild(event.getGuild())) {
            return;
        }

        event.getGuild().retrieveBan(event.getUser()).queue(
                ban -> verificationService.syncDiscordBan(event.getUser().getId(), ban.getReason()),
                failure -> verificationService.syncDiscordBan(event.getUser().getId(), "")
        );
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        if (!isBanSyncEnabled() || !isConfiguredGuild(event.getGuild())) {
            return;
        }

        verificationService.syncDiscordUnban(event.getUser().getId());
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (componentId != null && componentId.startsWith(MarketCommandRenderer.COMPONENT_PREFIX)) {
            handleMarketButtonInteraction(event);
            return;
        }

        if (!VERIFY_BUTTON_ID.equals(componentId)) {
            return;
        }

        var input = net.dv8tion.jda.api.components.textinput.TextInput
                .create(USERNAME_INPUT_ID, net.dv8tion.jda.api.components.textinput.TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("Your Minecraft username")
                .build();

        var modal = net.dv8tion.jda.api.modals.Modal
                .create(VERIFY_MODAL_ID, "Verify Minecraft Account")
                .addComponents(net.dv8tion.jda.api.components.label.Label.of("Minecraft Username", input))
                .build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!VERIFY_MODAL_ID.equals(event.getModalId())) {
            return;
        }

        var value = event.getValue(USERNAME_INPUT_ID);
        String minecraftUsername = value == null ? "" : value.getAsString();
        event.deferReply(true).queue(hook -> verificationService.verifyFromDiscord(
                event.getUser().getId(),
                event.getUser().getName(),
                minecraftUsername,
                message -> hook.sendMessage(message).queue()
        ));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"market".equals(event.getName())) {
            return;
        }
        if (!isMarketplaceEnabled() || !plugin.getConfig().getBoolean("marketplace.market-command-enabled", true) || marketplaceHandler == null) {
            replyMarket(event, MarketCommandRenderer.unavailable());
            return;
        }

        String item = event.getOption("item") == null ? "" : event.getOption("item").getAsString();
        String owner = event.getOption("owner") == null ? "" : event.getOption("owner").getAsString();
        boolean inStockOnly = event.getOption("in_stock") == null || event.getOption("in_stock").getAsBoolean();
        MarketCommandFilters filters = new MarketCommandFilters(item, owner, inStockOnly);
        try {
            cleanupMarketSessions();
            List<DiscordMarketplaceShop> shops = searchMarket(filters);
            if (shops.isEmpty()) {
                replyMarket(event, MarketCommandRenderer.noResults(filters));
                return;
            }

            String sessionId = nextMarketSessionId();
            MarketCommandSession session = new MarketCommandSession(event.getUser().getId(), filters, shops, 0, Instant.now());
            marketSessions.put(sessionId, session);
            replyMarket(event, MarketCommandRenderer.page(sessionId, session, itemIconBaseUrl()));
        } catch (Exception exception) {
            replyMarket(event, MarketCommandRenderer.error("Could not search marketplace: " + exception.getMessage()));
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!isChatSyncEnabled() || !event.isFromGuild() || !isConfiguredGuild(event.getGuild())) {
            return;
        }
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        String channelId = plugin.getConfig().getString("chat-sync.channel-id", "");
        if (channelId == null || channelId.isBlank() || !event.getChannel().getId().equals(channelId)) {
            return;
        }

        String message = event.getMessage().getContentDisplay();
        String displayName = event.getMember() == null ? event.getAuthor().getName() : displayName(event.getMember());
        String minecraftMessage = ChatSyncFormatter.format(
                plugin.getConfig().getString("chat-sync.discord-to-minecraft-format", "&9[Discord]&r {display-name}: &f{message}"),
                Map.of(
                        "display-name", displayName,
                        "username", event.getAuthor().getName(),
                        "message", message
                ),
                plugin.getConfig().getInt("chat-sync.max-message-length", 256)
        );
        if (minecraftMessage.isBlank()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(formatMinecraftMessage(minecraftMessage)));
    }

    private Guild requireGuild() {
        if (jda == null) {
            throw new IllegalStateException("Discord bot is not connected.");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalStateException("Configured Discord guild was not found.");
        }
        return guild;
    }

    private TextChannel requireTextChannel(String channelId) {
        Objects.requireNonNull(channelId, "channelId");
        TextChannel channel = jda == null ? null : jda.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalStateException("Configured verification channel was not found.");
        }
        return channel;
    }

    private void syncExistingBansOnStart() {
        if (!isBanSyncEnabled() || !plugin.getConfig().getBoolean("ban-sync.sync-existing-bans-on-start", true)) {
            return;
        }

        Guild guild = requireGuild();
        List<Guild.Ban> bans = Collections.synchronizedList(new ArrayList<>());
        guild.retrieveBanList().forEachRemainingAsync(ban -> {
            bans.add(ban);
            return true;
        }).thenRun(() -> {
            Instant now = Instant.now();
            verificationService.replaceDiscordBans(bans.stream()
                    .map(ban -> new DiscordBan(ban.getUser().getId(), ban.getReason(), now, now))
                    .toList());
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Could not sync existing Discord bans: " + throwable.getMessage());
            return null;
        });
    }

    private void upsertMarketCommand() {
        if (jda == null || !isMarketplaceEnabled() || !plugin.getConfig().getBoolean("marketplace.market-command-enabled", true)) {
            return;
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return;
        }
        guild.upsertCommand(Commands.slash("market", "Search Zyrox player shops.")
                .addOptions(
                        new OptionData(OptionType.STRING, "item", "Filter by item name, material, or enchantment.", false),
                        new OptionData(OptionType.STRING, "owner", "Filter by shop owner name.", false),
                        new OptionData(OptionType.BOOLEAN, "in_stock", "Only show shops with stock. Defaults to true.", false)
                )).queue();
    }

    private void handleMarketButtonInteraction(ButtonInteractionEvent event) {
        cleanupMarketSessions();

        String payload = event.getComponentId().substring(MarketCommandRenderer.COMPONENT_PREFIX.length());
        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            editMarket(event, MarketCommandRenderer.expired());
            return;
        }

        String sessionId = parts[0];
        String action = parts[1];
        MarketCommandSession session = marketSessions.get(sessionId);
        if (session == null || session.isExpired(Instant.now(), MARKET_SESSION_TTL)) {
            marketSessions.remove(sessionId);
            editMarket(event, MarketCommandRenderer.expired());
            return;
        }

        if (!session.requesterId().equals(event.getUser().getId())) {
            replyMarket(event, MarketCommandRenderer.forbidden());
            return;
        }

        try {
            if (MarketCommandRenderer.ACTION_PREVIOUS.equals(action)) {
                session = session.withPage(session.page() - 1);
            } else if (MarketCommandRenderer.ACTION_NEXT.equals(action)) {
                session = session.withPage(session.page() + 1);
            } else if (MarketCommandRenderer.ACTION_REFRESH.equals(action)) {
                List<DiscordMarketplaceShop> shops = searchMarket(session.filters());
                if (shops.isEmpty()) {
                    marketSessions.remove(sessionId);
                    editMarket(event, MarketCommandRenderer.noResults(session.filters()));
                    return;
                }
                session = session.withResults(shops, Instant.now());
            } else {
                editMarket(event, MarketCommandRenderer.expired());
                return;
            }

            marketSessions.put(sessionId, session);
            editMarket(event, MarketCommandRenderer.page(sessionId, session, itemIconBaseUrl()));
        } catch (Exception exception) {
            editMarket(event, MarketCommandRenderer.error("Could not refresh marketplace: " + exception.getMessage()));
        }
    }

    private List<DiscordMarketplaceShop> searchMarket(MarketCommandFilters filters) throws Exception {
        if (marketplaceHandler == null) {
            return List.of();
        }
        return marketplaceHandler.search(filters.item(), filters.owner(), filters.inStockOnly()).stream()
                .limit(MARKET_RESULT_LIMIT)
                .toList();
    }

    private void cleanupMarketSessions() {
        Instant now = Instant.now();
        marketSessions.entrySet().removeIf(entry -> entry.getValue().isExpired(now, MARKET_SESSION_TTL));
    }

    private String nextMarketSessionId() {
        return Long.toUnsignedString(marketSessionIds.incrementAndGet(), 36);
    }

    private String itemIconBaseUrl() {
        return plugin.getConfig().getString("marketplace.item-icon-base-url", "");
    }

    private void replyMarket(SlashCommandInteractionEvent event, MarketCommandResponse response) {
        event.replyEmbeds(response.embed())
                .addComponents(response.components())
                .setEphemeral(true)
                .queue();
    }

    private void replyMarket(ButtonInteractionEvent event, MarketCommandResponse response) {
        event.replyEmbeds(response.embed())
                .addComponents(response.components())
                .setEphemeral(true)
                .queue();
    }

    private void editMarket(ButtonInteractionEvent event, MarketCommandResponse response) {
        event.editMessageEmbeds(response.embed())
                .setComponents(response.components())
                .queue();
    }

    private boolean isBanSyncEnabled() {
        return plugin.getConfig().getBoolean("ban-sync.enabled", true);
    }

    private boolean isChatSyncEnabled() {
        return plugin.getConfig().getBoolean("chat-sync.enabled", false);
    }

    private boolean isMarketplaceEnabled() {
        return plugin.getConfig().getBoolean("marketplace.enabled", false);
    }

    private boolean isConfiguredGuild(Guild guild) {
        return guild != null && guild.getId().equals(guildId);
    }

    private String displayName(Member member) {
        String effectiveName = member.getEffectiveName();
        return effectiveName == null || effectiveName.isBlank() ? member.getUser().getName() : effectiveName;
    }

    @SuppressWarnings("deprecation")
    private String formatMinecraftMessage(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }

    private EmbedBuilder marketplaceEmbed(String title, DiscordMarketplaceShop shop, Color color) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setTimestamp(Instant.now())
                .addField("Item", shop.bundleAmount() + "x " + shop.itemName(), true)
                .addField("Price", shop.priceDiamonds() + " diamond(s)", true)
                .addField("Stock", shop.stockAmount() + " item(s)", true)
                .addField("Owner", shop.ownerName(), true)
                .addField("Location", shop.worldName() + " " + shop.x() + " " + shop.y() + " " + shop.z(), true);
        if (shop.enchantments() != null && !shop.enchantments().isBlank()) {
            embed.addField("Enchantments", shop.enchantments(), false);
        }
        return embed;
    }

}
