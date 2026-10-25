package smart.fly.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import smart.fly.util.PasswordUtil;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String DELETE_BY_EMAIL_SQL = "DELETE FROM password_reset_tokens WHERE email = ?";
    private static final String INSERT_SQL = "INSERT INTO password_reset_tokens (email, token, expiry_date) VALUES (?, ?, ?)";
    private static final String UPDATE_PASSWORD_SQL = "UPDATE user SET password_hash = ?, password_salt = ? WHERE email = ?";
    private static final String VALIDATE_SQL = "SELECT email, expiry_date FROM password_reset_tokens WHERE token = ?";
    private static final String EMAIL_SQL = "SELECT email FROM password_reset_tokens WHERE token = ?";
    private static final String DELETE_BY_TOKEN_SQL = "DELETE FROM password_reset_tokens WHERE token = ?";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private UserService userService;

    @Mock
    private PasswordUtil passwordUtil;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private final String testEmail = "manager@example.com";
    private final String testToken = "token-123";

    @Test
    void createResetToken_ReplacesExistingTokenWithNewOne() {
        long before = System.currentTimeMillis();

        passwordResetService.createResetToken(testEmail);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Timestamp> expiryCaptor = ArgumentCaptor.forClass(Timestamp.class);
        InOrder inOrder = inOrder(jdbcTemplate);
        inOrder.verify(jdbcTemplate).update(DELETE_BY_EMAIL_SQL, testEmail);
        inOrder.verify(jdbcTemplate).update(eq(INSERT_SQL), eq(testEmail), tokenCaptor.capture(),
                expiryCaptor.capture());

        assertDoesNotThrow(() -> UUID.fromString(tokenCaptor.getValue()));
        long expiry = expiryCaptor.getValue().getTime();
        assertTrue(expiry >= before + 3600000 && expiry <= System.currentTimeMillis() + 3600000);
    }

    @Test
    void createResetToken_GeneratesUniqueTokens() {
        passwordResetService.createResetToken(testEmail);
        passwordResetService.createResetToken(testEmail);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(eq(INSERT_SQL), eq(testEmail), tokenCaptor.capture(), any());
        assertNotEquals(tokenCaptor.getAllValues().get(0), tokenCaptor.getAllValues().get(1));
    }

    @Test
    void updatePassword_Success() {
        when(passwordUtil.generateSalt()).thenReturn("salt");
        when(passwordUtil.hashPassword("newPass", "salt")).thenReturn("hash");
        when(jdbcTemplate.update(UPDATE_PASSWORD_SQL, "hash", "salt", testEmail)).thenReturn(1);

        assertTrue(passwordResetService.updatePassword(testEmail, "newPass"));
    }

    @Test
    void updatePassword_NoMatchingUser_ReturnsFalse() {
        when(passwordUtil.generateSalt()).thenReturn("salt");
        when(passwordUtil.hashPassword("newPass", "salt")).thenReturn("hash");
        when(jdbcTemplate.update(UPDATE_PASSWORD_SQL, "hash", "salt", testEmail)).thenReturn(0);

        assertFalse(passwordResetService.updatePassword(testEmail, "newPass"));
    }

    @Test
    void updatePassword_DatabaseError_ReturnsFalse() {
        when(passwordUtil.generateSalt()).thenReturn("salt");
        when(passwordUtil.hashPassword("newPass", "salt")).thenReturn("hash");
        when(jdbcTemplate.update(UPDATE_PASSWORD_SQL, "hash", "salt", testEmail))
                .thenThrow(new DataAccessResourceFailureException("Database down"));

        assertFalse(passwordResetService.updatePassword(testEmail, "newPass"));
    }

    @Test
    void validateToken_UnexpiredTimestamp_ReturnsTrue() {
        when(jdbcTemplate.queryForMap(VALIDATE_SQL, testToken)).thenReturn(Map.of("email", testEmail, "expiry_date",
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(30))));

        assertTrue(passwordResetService.validateToken(testToken));
    }

    @Test
    void validateToken_ExpiredTimestamp_ReturnsFalse() {
        when(jdbcTemplate.queryForMap(VALIDATE_SQL, testToken)).thenReturn(Map.of("email", testEmail, "expiry_date",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1))));

        assertFalse(passwordResetService.validateToken(testToken));
    }

    @Test
    void validateToken_UnexpiredLocalDateTime_ReturnsTrue() {
        when(jdbcTemplate.queryForMap(VALIDATE_SQL, testToken))
                .thenReturn(Map.of("email", testEmail, "expiry_date", LocalDateTime.now().plusMinutes(30)));

        assertTrue(passwordResetService.validateToken(testToken));
    }

    @Test
    void validateToken_ExpiredLocalDateTime_ReturnsFalse() {
        when(jdbcTemplate.queryForMap(VALIDATE_SQL, testToken))
                .thenReturn(Map.of("email", testEmail, "expiry_date", LocalDateTime.now().minusMinutes(1)));

        assertFalse(passwordResetService.validateToken(testToken));
    }

    @Test
    void validateToken_UnexpectedExpiryType_ReturnsFalse() {
        when(jdbcTemplate.queryForMap(VALIDATE_SQL, testToken))
                .thenReturn(Map.of("email", testEmail, "expiry_date", "2099-01-01"));

        assertFalse(passwordResetService.validateToken(testToken));
    }

    @Test
    void validateToken_TokenNotFound_ReturnsFalse() {
        when(jdbcTemplate.queryForMap(VALIDATE_SQL, testToken)).thenThrow(new EmptyResultDataAccessException(1));

        assertFalse(passwordResetService.validateToken(testToken));
    }

    @Test
    void getEmailFromToken_TokenExists_ReturnsEmail() {
        when(jdbcTemplate.queryForObject(EMAIL_SQL, String.class, testToken)).thenReturn(testEmail);

        assertEquals(testEmail, passwordResetService.getEmailFromToken(testToken));
    }

    @Test
    void getEmailFromToken_TokenNotFound_ReturnsNull() {
        when(jdbcTemplate.queryForObject(EMAIL_SQL, String.class, testToken))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertNull(passwordResetService.getEmailFromToken(testToken));
    }

    @Test
    void deleteToken_DeletesByToken() {
        passwordResetService.deleteToken(testToken);

        verify(jdbcTemplate).update(DELETE_BY_TOKEN_SQL, testToken);
    }
}
