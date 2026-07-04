package de.tecca.zyrox.dc.web;

import de.tecca.zyrox.dc.discord.DiscordBotService;
import de.tecca.zyrox.dc.discord.DiscordMemberSnapshot;
import de.tecca.zyrox.dc.minecraft.MinecraftAccountService;
import de.tecca.zyrox.dc.storage.LinkedAccount;
import de.tecca.zyrox.dc.storage.ZyroxDcStorage;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ZyroxDcDashboardRenderer {

    private final ZyroxDcStorage storage;
    private final MinecraftAccountService minecraftAccounts;
    private final DiscordBotService discordBot;

    public ZyroxDcDashboardRenderer(ZyroxDcStorage storage, MinecraftAccountService minecraftAccounts, DiscordBotService discordBot) {
        this.storage = storage;
        this.minecraftAccounts = minecraftAccounts;
        this.discordBot = discordBot;
    }

    public String render() throws SQLException {
        List<LinkedAccount> linkedAccounts = storage.findAllLinks();
        Set<String> linkedDiscordIds = new HashSet<>();
        for (LinkedAccount account : linkedAccounts) {
            linkedDiscordIds.add(account.discordId());
        }

        List<String> duplicateKeys = storage.duplicateMinecraftKeys();

        StringBuilder html = new StringBuilder();
        html.append("""
                <style>
                    .zyroxdc-table { width: 100%; border-collapse: collapse; margin-top: 14px; }
                    .zyroxdc-table th, .zyroxdc-table td { border-bottom: 1px solid #303846; padding: 9px; text-align: left; vertical-align: top; }
                    .zyroxdc-pill { display: inline-block; border-radius: 999px; padding: 3px 8px; font-size: 12px; font-weight: 700; }
                    .zyroxdc-ok { background: #22543d; color: #c6f6d5; }
                    .zyroxdc-warn { background: #744210; color: #fefcbf; }
                    .zyroxdc-bad { background: #742a2a; color: #fed7d7; }
                </style>
                """);

        html.append("<h3>Linked Accounts</h3>");
        html.append("<table class=\"zyroxdc-table\"><thead><tr><th>Discord</th><th>Minecraft</th><th>UUID</th><th>Whitelist</th><th>Flags</th></tr></thead><tbody>");
        for (LinkedAccount account : linkedAccounts) {
            boolean whitelisted = minecraftAccounts.isWhitelisted(new MinecraftAccountService.LinkedMinecraftIdentity(account.minecraftUsername(), account.minecraftUuid()));
            String duplicateKey = account.minecraftUuid() == null || account.minecraftUuid().isBlank()
                    ? account.minecraftUsername().toLowerCase()
                    : account.minecraftUuid();
            boolean duplicate = duplicateKeys.contains(duplicateKey);
            html.append("<tr>")
                    .append("<td>").append(escape(account.discordName())).append("<br><span class=\"muted\">").append(escape(account.discordId())).append("</span></td>")
                    .append("<td>").append(escape(account.minecraftUsername())).append("</td>")
                    .append("<td>").append(escape(account.minecraftUuid())).append("</td>")
                    .append("<td>").append(status(whitelisted ? "Whitelisted" : "Not whitelisted", whitelisted ? "ok" : "bad")).append("</td>")
                    .append("<td>").append(duplicate ? status("Duplicate MC account", "warn") : "").append("</td>")
                    .append("</tr>");
        }
        if (linkedAccounts.isEmpty()) {
            html.append("<tr><td colspan=\"5\" class=\"muted\">No linked accounts yet.</td></tr>");
        }
        html.append("</tbody></table>");

        html.append("<h3 style=\"margin-top:22px\">Unlinked Discord Members</h3>");
        html.append("<table class=\"zyroxdc-table\"><thead><tr><th>Discord</th><th>Status</th></tr></thead><tbody>");
        if (discordBot.isRunning()) {
            int count = 0;
            for (DiscordMemberSnapshot member : discordBot.humanMembers()) {
                if (linkedDiscordIds.contains(member.id())) {
                    continue;
                }
                count++;
                html.append("<tr><td>").append(escape(member.displayName())).append("<br><span class=\"muted\">")
                        .append(escape(member.id())).append("</span></td><td>")
                        .append(status("Not linked", "warn")).append("</td></tr>");
            }
            if (count == 0) {
                html.append("<tr><td colspan=\"2\" class=\"muted\">Every cached non-bot Discord member is linked.</td></tr>");
            }
        } else {
            html.append("<tr><td colspan=\"2\" class=\"muted\">Discord bot is not connected.</td></tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String status(String label, String type) {
        return "<span class=\"zyroxdc-pill zyroxdc-" + type + "\">" + escape(label) + "</span>";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
