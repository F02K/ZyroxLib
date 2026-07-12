package de.tecca.zyrox.economy.web;

import org.bukkit.Material;

public final class ZyroxEconomyDashboardRenderer {

    private final Material material;

    public ZyroxEconomyDashboardRenderer(Material material) {
        this.material = material;
    }

    public String render() {
        return """
                <style>
                    .economy-note { border: 1px solid #303846; border-radius: 8px; padding: 12px; margin-top: 14px; }
                </style>
                <h3>ZyroxEconomy</h3>
                <div class="economy-note">
                    <strong>Physical diamond payments</strong>
                    <p class="muted">Players pay with the %s in their inventory. ZyroxEconomy no longer keeps player accounts or exposes an external economy bridge.</p>
                </div>
                """.formatted(escape(material.name()));
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
