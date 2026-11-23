package de.tecca.zyrox.protect.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void verifiesOnlyMatchingPassword() {
        PasswordHasher hasher = new PasswordHasher();
        PasswordHash hash = hasher.hash("very-secure-password".toCharArray());

        assertTrue(hasher.verify("very-secure-password".toCharArray(), hash));
        assertFalse(hasher.verify("wrong-password".toCharArray(), hash));
    }
}
