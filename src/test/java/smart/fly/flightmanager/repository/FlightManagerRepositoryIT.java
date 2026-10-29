package smart.fly.flightmanager.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import smart.fly.businness.owner.model.BusinessOwner;
import smart.fly.flightmanager.model.FlightManager;
import smart.fly.support.MySqlIntegrationTest;
import smart.fly.user.model.User;

class FlightManagerRepositoryIT extends MySqlIntegrationTest {

    @Autowired
    private FlightManagerRepository flightManagerRepository;

    @Test
    void save_ReturnsGeneratedId() {
        int ownerUserId = insertUser("Owner", "owner@flysmart.test", "OWNER");
        int boId = insert("business_owner", "bo_id", Map.of("user_id", ownerUserId));
        int userId = insertUser("Asha", "asha@flysmart.test", "MANAGER");
        FlightManager manager = new FlightManager();
        BusinessOwner owner = new BusinessOwner();
        owner.setBoId(boId);
        manager.setBusinessOwner(owner);
        User user = new User();
        user.setUserId(userId);
        manager.setUser(user);

        FlightManager saved = flightManagerRepository.save(manager);

        assertTrue(saved.getFmId() > 0);
        assertEquals(saved.getFmId(), flightManagerRepository.findByUserId(userId).getFmId());
    }

    @Test
    void findByUserId_WithoutFlightManagerRow_ReturnsNull() {
        int userId = insertUser("Not Yet Assigned", "new@flysmart.test", "MANAGER");

        assertNull(flightManagerRepository.findByUserId(userId));
    }

    @Test
    void findAll_ReturnsOnlyManagersOrderedByNameWithUserDetails() {
        insertManager("Ravi", "ravi@flysmart.test");
        insertManager("Asha", "asha@flysmart.test");

        List<FlightManager> managers = flightManagerRepository.findAll();

        assertEquals(List.of("Asha", "Ravi"), managers.stream().map(m -> m.getUser().getName()).toList());
        assertEquals("asha@flysmart.test", managers.get(0).getUser().getEmail());
    }

    @Test
    void changePassword_UpdatesManagersUserRowThroughJoin() {
        // UPDATE ... JOIN is MySQL-specific syntax that H2 cannot run
        int fmId = insertManager("Asha", "asha@flysmart.test");

        flightManagerRepository.changePassword(fmId, "newHash", "newSalt");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT password_hash, password_salt FROM user WHERE user_id = ?", userIdOfManager(fmId));
        assertEquals("newHash", row.get("password_hash"));
        assertEquals("newSalt", row.get("password_salt"));
    }
}
