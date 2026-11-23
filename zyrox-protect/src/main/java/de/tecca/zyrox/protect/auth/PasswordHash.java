package de.tecca.zyrox.protect.auth;

public record PasswordHash(String algorithm, int iterations, String saltBase64, String hashBase64) {
}
