package smart.fly.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import smart.fly.action.log.ActionLog;
import smart.fly.support.MySqlIntegrationTest;

class LoggingServiceIT extends MySqlIntegrationTest {

    @Autowired
    private LoggingService loggingService;

    @Test
    void logAction_IsReturnedForThatUser() {
        loggingService.logAction(7, "Airline Updated", "ManagerController");
        loggingService.logAction(8, "Airport Updated", "ManagerController");

        List<ActionLog> logs = loggingService.getActionLogForOwner(7);

        assertEquals(1, logs.size());
        assertEquals("Airline Updated", logs.get(0).getActionPerformed());
        assertEquals("ManagerController", logs.get(0).getControllerName());
        assertNotNull(logs.get(0).getTimestamp());
    }

    @Test
    void getActionLogForOwner_ReturnsNewestFirst() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 12, 0);
        insertLog(7, "First", now.minusHours(2));
        insertLog(7, "Latest", now);
        insertLog(7, "Middle", now.minusHours(1));

        List<ActionLog> logs = loggingService.getActionLogForOwner(7);

        assertEquals(List.of("Latest", "Middle", "First"),
                logs.stream().map(ActionLog::getActionPerformed).toList());
    }

    private void insertLog(int userId, String action, LocalDateTime at) {
        insert("action_log", "log_id", Map.of("user_id", userId, "action_performed", action,
                "controller_name", "ManagerController", "timestamp", Timestamp.valueOf(at)));
    }
}
