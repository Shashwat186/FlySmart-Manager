package smart.fly.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAuthException_ReturnsUnauthorizedWithMessage() {
        ResponseEntity<String> response = handler.handleAuthException(new AuthenticationException("Bad credentials"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Bad credentials", response.getBody());
    }

    @Test
    void handleBusinessExceptions_ReturnsBadRequestWithMessage() {
        ResponseEntity<String> response = handler
                .handleBusinessExceptions(new FlightManagementException("Flight not found"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Flight not found", response.getBody());
    }

    @Test
    void handleGenericException_ReturnsInternalServerErrorWithoutLeakingDetails() {
        ResponseEntity<String> response = handler.handleGenericException(new IllegalStateException("db password=x"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", response.getBody());
    }
}
