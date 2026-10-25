package smart.fly.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import smart.fly.action.log.ActionLog;

@ExtendWith(MockitoExtension.class)
class LoggingServiceTest {

    private static final String INSERT_SQL = "INSERT INTO action_log (user_id, action_performed, controller_name, timestamp) VALUES (?, ?, ?, ?)";
    private static final String SELECT_SQL = "SELECT * FROM action_log WHERE user_id = ? ORDER BY timestamp DESC";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ResultSet resultSet;

    @InjectMocks
    private LoggingService loggingService;

    @Test
    void logAction_InsertsLogWithCurrentTimestamp() {
        long before = System.currentTimeMillis();

        loggingService.logAction(7, "Airline Updated", "ManagerController");

        ArgumentCaptor<Timestamp> timestampCaptor = ArgumentCaptor.forClass(Timestamp.class);
        verify(jdbcTemplate).update(eq(INSERT_SQL), eq(7), eq("Airline Updated"), eq("ManagerController"),
                timestampCaptor.capture());
        long logged = timestampCaptor.getValue().getTime();
        assertTrue(logged >= before && logged <= System.currentTimeMillis());
    }

    @Test
    void getActionLogForOwner_ReturnsLogsFromQuery() {
        ActionLog log = new ActionLog();
        when(jdbcTemplate.query(eq(SELECT_SQL), any(RowMapper.class), eq(7))).thenReturn(List.of(log));

        List<ActionLog> result = loggingService.getActionLogForOwner(7);

        assertEquals(1, result.size());
        assertSame(log, result.get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getActionLogForOwner_RowMapperMapsAllColumns() throws SQLException {
        Timestamp timestamp = Timestamp.valueOf("2026-10-01 12:30:00");
        when(resultSet.getInt("log_id")).thenReturn(11);
        when(resultSet.getInt("user_id")).thenReturn(7);
        when(resultSet.getString("action_performed")).thenReturn("Airport Updated");
        when(resultSet.getString("controller_name")).thenReturn("ManagerController");
        when(resultSet.getTimestamp("timestamp")).thenReturn(timestamp);

        loggingService.getActionLogForOwner(7);

        ArgumentCaptor<RowMapper<ActionLog>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbcTemplate).query(eq(SELECT_SQL), mapperCaptor.capture(), eq(7));
        ActionLog mapped = mapperCaptor.getValue().mapRow(resultSet, 0);

        assertEquals(11, mapped.getLogId());
        assertEquals(7, mapped.getUserId());
        assertEquals("Airport Updated", mapped.getActionPerformed());
        assertEquals("ManagerController", mapped.getControllerName());
        assertEquals(timestamp, mapped.getTimestamp());
    }
}
