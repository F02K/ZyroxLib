package de.tecca.zyrox.api.web;

import de.tecca.zyrox.api.ZyroxApi;

import java.util.UUID;

public interface ZyroxDashboardViewContext {

    ZyroxApi api();

    UUID actorUniqueId();

    String actorName();
}

