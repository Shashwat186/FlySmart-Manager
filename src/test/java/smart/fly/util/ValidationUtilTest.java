package smart.fly.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidationUtilTest {

    @ParameterizedTest
    @ValueSource(strings = { "user@example.com", "first.last@mail.co.in", "a_b-c@domain.org", "user123@sub.example.info" })
    void validateEmail_ValidEmail_DoesNotThrow(String email) {
        assertDoesNotThrow(() -> ValidationUtil.validateEmail(email));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "plainaddress", "user@", "@example.com", "user@example", "user name@example.com",
            "user@example.toolongtld" })
    void validateEmail_InvalidEmail_Throws(String email) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ValidationUtil.validateEmail(email));

        assertEquals("Invalid email format", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "1234567890", "+911234567890", "123456789012345" })
    void validatePhone_ValidPhone_DoesNotThrow(String phone) {
        assertDoesNotThrow(() -> ValidationUtil.validatePhone(phone));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "12345", "1234567890123456", "123-456-7890", "+91 1234567890", "abcdefghij" })
    void validatePhone_InvalidPhone_Throws(String phone) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ValidationUtil.validatePhone(phone));

        assertEquals("Invalid phone number", ex.getMessage());
    }
}
