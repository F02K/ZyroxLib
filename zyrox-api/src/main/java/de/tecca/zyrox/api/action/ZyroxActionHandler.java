package de.tecca.zyrox.api.action;

@FunctionalInterface
public interface ZyroxActionHandler {

    ZyroxActionResult execute(ZyroxActionContext context) throws Exception;
}

