package de.tecca.zyrox.protect.audit;

public record HashChainStatus(boolean valid, long checkedEvents, String message) {
}
