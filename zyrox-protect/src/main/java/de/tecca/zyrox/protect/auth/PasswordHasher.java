package de.tecca.zyrox.protect.auth;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 32;
    private static final int KEY_BITS = 256;

    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordHash hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = pbkdf(password, salt, ITERATIONS);
        return new PasswordHash(
                ALGORITHM,
                ITERATIONS,
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash)
        );
    }

    public boolean verify(char[] password, PasswordHash stored) {
        byte[] salt = Base64.getDecoder().decode(stored.saltBase64());
        byte[] expected = Base64.getDecoder().decode(stored.hashBase64());
        byte[] actual = pbkdf(password, salt, stored.iterations());
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] pbkdf(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash password.", exception);
        }
    }
}
