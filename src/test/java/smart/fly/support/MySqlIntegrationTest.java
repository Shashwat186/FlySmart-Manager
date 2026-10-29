package smart.fly.support;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base class for integration tests that run the real SQL against MySQL 8 in
 * Docker. One container is shared by every subclass (so the Spring context is
 * cached too), and each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@Transactional
public abstract class MySqlIntegrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        // Overrides the H2 settings from application-test.properties
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:db/test-schema.sql");
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected int insertUser(String name, String email, String role) {
        return insert("user", "user_id", Map.of("name", name, "email", email, "role", role,
                "password_hash", "hash", "password_salt", "salt"));
    }

    /** Creates a MANAGER user with its business owner and flight_manager rows; returns the fm_id. */
    protected int insertManager(String name, String email) {
        int ownerUserId = insertUser("Owner of " + name, "owner." + email, "OWNER");
        int boId = insert("business_owner", "bo_id", Map.of("user_id", ownerUserId));
        int userId = insertUser(name, email, "MANAGER");
        return insert("flight_manager", "fm_id", Map.of("bo_id", boId, "user_id", userId));
    }

    protected int userIdOfManager(int fmId) {
        return jdbcTemplate.queryForObject("SELECT user_id FROM flight_manager WHERE fm_id = ?", Integer.class, fmId);
    }

    protected int insertAirline(String name, String code) {
        return insert("airline", "airline_id", Map.of("name", name, "code", code, "contact_info", "ops@" + code));
    }

    protected int insertAirport(String name, String code, String location) {
        return insert("airport", "airport_id",
                Map.of("name", name, "code", code, "contact_info", "info@" + code, "location", location));
    }

    protected int insertFlight(String flightNumber, int airlineId, int departureAirportId, int arrivalAirportId,
            LocalDateTime departure, String status, int fmId) {
        return insert("flight", "flight_id", Map.of(
                "flight_number", flightNumber,
                "airline_id", airlineId,
                "departure_airport_id", departureAirportId,
                "arrival_airport_id", arrivalAirportId,
                "departure_date_time", Timestamp.valueOf(departure),
                "arrival_date_time", Timestamp.valueOf(departure.plusHours(2)),
                "status", status,
                "fm_id", fmId));
    }

    protected int insert(String table, String idColumn, Map<String, ?> values) {
        return new SimpleJdbcInsert(jdbcTemplate)
                .withTableName(table)
                .usingGeneratedKeyColumns(idColumn)
                .executeAndReturnKey(values)
                .intValue();
    }
}
