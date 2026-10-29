package smart.fly.user.repository;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import smart.fly.support.MySqlIntegrationTest;
import smart.fly.user.model.User;

class UserRepositoryIT extends MySqlIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void save_ReturnsGeneratedIdAndIsFoundByEmail() {
        User user = new User();
        user.setName("Asha");
        user.setEmail("asha@flysmart.test");
        user.setPhone("9999999999");
        user.setRole("MANAGER");
        user.setPasswordHash("hash");
        user.setPasswordSalt("salt");

        User saved = userRepository.save(user);

        assertTrue(saved.getUserId() > 0);
        User found = userRepository.findByEmail("asha@flysmart.test");
        assertEquals(saved.getUserId(), found.getUserId());
        assertEquals("MANAGER", found.getRole());
        assertEquals("hash", found.getPasswordHash());
        assertEquals("salt", found.getPasswordSalt());
        assertNull(found.getProfilePhoto());
    }

    @Test
    void findByEmail_Unknown_ReturnsNull() {
        assertNull(userRepository.findByEmail("nobody@flysmart.test"));
    }

    @Test
    void emailExists_ReflectsStoredUsers() {
        insertUser("Asha", "asha@flysmart.test", "MANAGER");

        assertTrue(userRepository.emailExists("asha@flysmart.test"));
        assertFalse(userRepository.emailExists("nobody@flysmart.test"));
    }

    @Test
    void changePassword_UpdatesHashAndSalt() {
        int userId = insertUser("Asha", "asha@flysmart.test", "MANAGER");

        userRepository.changePassword(userId, "newHash", "newSalt");

        User user = userRepository.findById(userId);
        assertEquals("newHash", user.getPasswordHash());
        assertEquals("newSalt", user.getPasswordSalt());
    }

    @Test
    void countByRole_CountsOnlyThatRole() {
        insertUser("Asha", "asha@flysmart.test", "MANAGER");
        insertUser("Ravi", "ravi@flysmart.test", "MANAGER");
        insertUser("Owner", "owner@flysmart.test", "OWNER");

        assertEquals(2, userRepository.countByRole("MANAGER"));
    }
}
