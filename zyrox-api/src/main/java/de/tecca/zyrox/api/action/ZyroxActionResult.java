package de.tecca.zyrox.api.action;

/**
 * Result returned after an action was executed.
 */
public record ZyroxActionResult(boolean success, String message) {

    public static ZyroxActionResult success(String message) {
        return new ZyroxActionResult(true, message);
    }

    public static ZyroxActionResult failure(String message) {
        return new ZyroxActionResult(false, message);
    }
}

