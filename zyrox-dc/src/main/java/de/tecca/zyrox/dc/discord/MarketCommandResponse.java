package de.tecca.zyrox.dc.discord;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;

record MarketCommandResponse(MessageEmbed embed, List<ActionRow> components) {

    MarketCommandResponse {
        components = components == null ? List.of() : List.copyOf(components);
    }
}
