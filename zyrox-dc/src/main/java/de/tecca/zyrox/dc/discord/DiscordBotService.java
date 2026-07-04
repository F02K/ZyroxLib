package de.tecca.zyrox.dc.discord;

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
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DiscordBotService extends ListenerAdapter {

    public static final String VERIFY_BUTTON_ID = "zyroxdc_verify";
    public static final String VERIFY_MODAL_ID = "zyroxdc_verify_modal";
    public static final String USERNAME_INPUT_ID = "minecraft_username";

    private final JavaPlugin plugin;
    private final VerificationService verificationService;
    private JDA jda;
    private String guildId;

    public DiscordBotService(JavaPlugin plugin, VerificationService verificationService) {
        this.plugin = plugin;
        this.verificationService = verificationService;
    }

    public void start(String token, String guildId) throws InterruptedException {
        stop();

        this.guildId = guildId;
        jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MODERATION)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(this)
                .build();
        jda.awaitReady();
        syncExistingBansOnStart();
        plugin.getLogger().info("ZyroxDC connected to Discord.");
    }

    public void stop() {
        if (jda != null) {
            jda.shutdownNow();
            jda = null;
        }
    }

    public boolean isRunning() {
        return jda != null;
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
        if (!VERIFY_BUTTON_ID.equals(event.getComponentId())) {
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

    private boolean isBanSyncEnabled() {
        return plugin.getConfig().getBoolean("ban-sync.enabled", true);
    }

    private boolean isConfiguredGuild(Guild guild) {
        return guild != null && guild.getId().equals(guildId);
    }

    private String displayName(Member member) {
        String effectiveName = member.getEffectiveName();
        return effectiveName == null || effectiveName.isBlank() ? member.getUser().getName() : effectiveName;
    }
}
