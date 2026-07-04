package de.tecca.zyrox.api.web;

@FunctionalInterface
public interface ZyroxDashboardViewRenderer {

    String render(ZyroxDashboardViewContext context) throws Exception;
}

