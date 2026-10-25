package smart.fly.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class PasswordUtilTest {

    private final PasswordUtil passwordUtil = new PasswordUtil();

    @Test
    void generateSalt_ReturnsBase64EncodedSixteenBytes() {
        String salt = passwordUtil.generateSalt();

        assertNotNull(salt);
        assertEquals(16, Base64.getDecoder().decode(salt).length);
    }

    @Test
    void generateSalt_ReturnsDifferentValuesOnEachCall() {
        assertNotEquals(passwordUtil.generateSalt(), passwordUtil.generateSalt());
    }

    @Test
    void hashPassword_IsDeterministicForSameSalt() {
        String salt = passwordUtil.generateSalt();

        assertEquals(passwordUtil.hashPassword("secret", salt), passwordUtil.hashPassword("secret", salt));
    }

    @Test
    void hashPassword_DiffersForDifferentSalts() {
        String hash1 = passwordUtil.hashPassword("secret", passwordUtil.generateSalt());
        String hash2 = passwordUtil.hashPassword("secret", passwordUtil.generateSalt());

        assertNotEquals(hash1, hash2);
    }

    @Test
    void hashPassword_ProducesSha256LengthDigest() {
        String hash = passwordUtil.hashPassword("secret", passwordUtil.generateSalt());

        assertEquals(32, Base64.getDecoder().decode(hash).length);
    }

    @Test
    void hashPassword_DoesNotReturnPlainText() {
        String hash = passwordUtil.hashPassword("secret", passwordUtil.generateSalt());

        assertNotEquals("secret", hash);
    }

    @Test
    void hashPassword_InvalidSalt_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> passwordUtil.hashPassword("secret", "not base64!"));
    }

    @Test
    void verifyPassword_CorrectPassword_ReturnsTrue() {
        String salt = passwordUtil.generateSalt();
        String hash = passwordUtil.hashPassword("secret", salt);

        assertTrue(passwordUtil.verifyPassword("secret", hash, salt));
    }

    @Test
    void verifyPassword_WrongPassword_ReturnsFalse() {
        String salt = passwordUtil.generateSalt();
        String hash = passwordUtil.hashPassword("secret", salt);

        assertFalse(passwordUtil.verifyPassword("Secret", hash, salt));
    }

    @Test
    void verifyPassword_WrongSalt_ReturnsFalse() {
        String hash = passwordUtil.hashPassword("secret", passwordUtil.generateSalt());

        assertFalse(passwordUtil.verifyPassword("secret", hash, passwordUtil.generateSalt()));
    }
}
