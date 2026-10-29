package smart.fly.airline.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;

import smart.fly.airline.model.Airline;
import smart.fly.support.MySqlIntegrationTest;

class AirlineRepositoryIT extends MySqlIntegrationTest {

    @Autowired
    private AirlineRepository airlineRepository;

    @Test
    void save_ThenFindByCode_ReturnsAllColumns() {
        Airline airline = new Airline();
        airline.setName("Sky India");
        airline.setCode("SKY");
        airline.setContactInfo("ops@sky.test");

        airlineRepository.save(airline);

        Airline saved = airlineRepository.findByCode("SKY");
        assertTrue(saved.getAirlineId() > 0);
        assertEquals("Sky India", saved.getName());
        assertEquals("ops@sky.test", saved.getContactInfo());
    }

    @Test
    void findAll_OrdersByName() {
        insertAirline("Zen Air", "ZEN");
        insertAirline("Aero Blue", "ABL");

        List<Airline> airlines = airlineRepository.findAll();

        assertEquals(List.of("Aero Blue", "Zen Air"), airlines.stream().map(Airline::getName).toList());
        assertEquals(2, airlineRepository.countAirline());
    }

    @Test
    void update_PersistsChanges() {
        int id = insertAirline("Sky India", "SKY");
        Airline airline = airlineRepository.findById(id);
        airline.setName("Sky India Express");
        airline.setCode("SKX");

        airlineRepository.update(airline);

        Airline updated = airlineRepository.findById(id);
        assertEquals("Sky India Express", updated.getName());
        assertEquals("SKX", updated.getCode());
    }

    @Test
    void update_ToExistingCode_ThrowsDuplicateKeyException() {
        // ManagerController.updateAirline catches this to show "code already exists"
        insertAirline("Sky India", "SKY");
        Airline other = airlineRepository.findById(insertAirline("Zen Air", "ZEN"));
        other.setCode("SKY");

        assertThrows(DuplicateKeyException.class, () -> airlineRepository.update(other));
    }

    @Test
    void delete_RemovesAirline() {
        int id = insertAirline("Sky India", "SKY");

        airlineRepository.delete(id);

        assertThrows(EmptyResultDataAccessException.class, () -> airlineRepository.findById(id));
    }
}
