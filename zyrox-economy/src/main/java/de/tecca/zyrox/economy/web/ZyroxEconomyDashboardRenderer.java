package de.tecca.zyrox.economy.web;

import de.tecca.zyrox.economy.service.EconomyFormatter;
import de.tecca.zyrox.economy.storage.EconomyAccount;
import de.tecca.zyrox.economy.storage.EconomyLedgerSummary;
import de.tecca.zyrox.economy.storage.EconomyStorage;
import org.bukkit.Material;

import java.sql.SQLException;
import java.util.List;

public final class ZyroxEconomyDashboardRenderer {

    private final EconomyStorage storage;
    private final Material material;

    public ZyroxEconomyDashboardRenderer(EconomyStorage storage, Material material) {
        this.storage = storage;
        this.material = material;
    }

    public String render() {
        try {
            EconomyLedgerSummary summary = storage.summary();
            List<EconomyAccount> accounts = storage.richestAccounts(20);
            StringBuilder html = new StringBuilder();
            html.append("""
                    <style>
                        .economy-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; margin: 14px 0; }
                        .economy-stat { border: 1px solid #303846; border-radius: 8px; padding: 10px; }
                        .economy-stat strong { display: block; font-size: 20px; margin-top: 5px; }
                        .economy-table { width: 100%; border-collapse: collapse; margin-top: 14px; }
                        .economy-table th, .economy-table td { border-bottom: 1px solid #303846; padding: 9px; text-align: left; }
                    </style>
                    """);
            html.append("<h3>ZyroxEconomy</h3>");
            html.append("<p class=\"muted\">Vault balance is inventory ")
                    .append(escape(material.name()))
                    .append(" only. Whole Vault amounts map 1:1 to diamonds.</p>");
            html.append("<div class=\"economy-grid\">")
                    .append(stat("Shop Reserve", summary.shopReserveCents()))
                    .append(stat("Bank Change", summary.totalChangeBalanceCents()))
                    .append(stat("Cashout Bank", summary.totalPayoutBalanceCents()))
                    .append(countStat("Accounts", summary.accountCount()))
                    .append("</div>");
            html.append("<table class=\"economy-table\"><thead><tr><th>Player</th><th>Change</th><th>Cashout</th></tr></thead><tbody>");
            for (EconomyAccount account : accounts) {
                html.append("<tr><td>").append(escape(account.playerName())).append("</td>")
                        .append("<td>").append(EconomyFormatter.diamonds(account.changeBalanceCents())).append("</td>")
                        .append("<td>").append(EconomyFormatter.diamonds(account.payoutBalanceCents())).append("</td></tr>");
            }
            if (accounts.isEmpty()) {
                html.append("<tr><td colspan=\"3\" class=\"muted\">No pending economy balances.</td></tr>");
            }
            html.append("</tbody></table>");
            return html.toString();
        } catch (SQLException exception) {
            return "<h3>ZyroxEconomy</h3><p class=\"muted\">Could not load economy ledger: "
                    + escape(exception.getMessage()) + "</p>";
        }
    }

    private String stat(String label, long cents) {
        return "<div class=\"economy-stat\"><span class=\"muted\">" + escape(label) + "</span><strong>"
                + EconomyFormatter.diamonds(cents) + "</strong></div>";
    }

    private String countStat(String label, long count) {
        return "<div class=\"economy-stat\"><span class=\"muted\">" + escape(label) + "</span><strong>"
                + count + "</strong></div>";
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
