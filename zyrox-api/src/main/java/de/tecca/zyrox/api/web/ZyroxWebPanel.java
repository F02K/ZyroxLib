package de.tecca.zyrox.api.web;

import org.bukkit.entity.Player;

import java.net.URI;

public interface ZyroxWebPanel {

    URI createSessionLink(Player player);

    boolean isRunning();

    int port();
}

