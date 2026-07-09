package de.tecca.zyrox.deepdark.web;

import de.tecca.zyrox.deepdark.portal.DeepDarkPortal;
import de.tecca.zyrox.deepdark.portal.DeepDarkPortalService;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DeepDarkDashboardRenderer {

    private final JavaPlugin plugin;
    private final DeepDarkPortalService portalService;

    public DeepDarkDashboardRenderer(JavaPlugin plugin, DeepDarkPortalService portalService) {
        this.plugin = plugin;
        this.portalService = portalService;
    }

    public String render() {
        StringBuilder html = new StringBuilder();
        html.append("""
                <style>
                    .deepdark-table { width: 100%; border-collapse: collapse; margin-top: 14px; }
                    .deepdark-table th, .deepdark-table td { border-bottom: 1px solid #303846; padding: 9px; text-align: left; vertical-align: top; }
                    .deepdark-pill { display: inline-block; border-radius: 999px; padding: 3px 8px; font-size: 12px; font-weight: 700; background: #253145; color: #cfe8ff; }
                </style>
                """);
        html.append("<h3>DeepDark Portals</h3>");
        html.append("<p class=\"muted\">World: ").append(escape(plugin.getConfig().getString("world.name", "zyrox_deepdark"))).append("</p>");
        html.append("<table class=\"deepdark-table\"><thead><tr><th>ID</th><th>World</th><th>Origin</th><th>Axis</th><th>Linked</th><th>Last Used</th></tr></thead><tbody>");
        for (DeepDarkPortal portal : portalService.allPortals()) {
            html.append("<tr><td>").append(escape(portal.id())).append("</td>")
                    .append("<td>").append(escape(portal.worldName())).append("</td>")
                    .append("<td>").append(portal.originX()).append(", ").append(portal.originY()).append(", ").append(portal.originZ()).append("</td>")
                    .append("<td><span class=\"deepdark-pill\">").append(portal.axis().name()).append("</span></td>")
                    .append("<td>").append(escape(portal.linkedPortalId())).append("</td>")
                    .append("<td>").append(escape(formatTime(portal.lastUsedAt()))).append("</td></tr>");
        }
        if (portalService.allPortals().isEmpty()) {
            html.append("<tr><td colspan=\"6\" class=\"muted\">No portals registered yet.</td></tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String formatTime(long epochMillis) {
        if (epochMillis <= 0) {
            return "Never";
        }
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
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
