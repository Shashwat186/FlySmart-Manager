package smart.fly.airport.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;

import smart.fly.airport.model.Airport;
import smart.fly.support.MySqlIntegrationTest;

class AirportRepositoryIT extends MySqlIntegrationTest {

    @Autowired
    private AirportRepository airportRepository;

    @Test
    void save_ThenFindByCode_ReturnsAllColumns() {
        Airport airport = new Airport();
        airport.setName("Indira Gandhi International");
        airport.setCode("DEL");
        airport.setContactInfo("info@del.test");
        airport.setLocation("Delhi");

        airportRepository.save(airport);

        Airport saved = airportRepository.findByCode("DEL");
        assertTrue(saved.getAirportId() > 0);
        assertEquals("Indira Gandhi International", saved.getName());
        assertEquals("info@del.test", saved.getContactInfo());
        assertEquals("Delhi", saved.getLocation());
    }

    @Test
    void findAll_OrdersByName() {
        insertAirport("Kempegowda International", "BLR", "Bengaluru");
        insertAirport("Chhatrapati Shivaji International", "BOM", "Mumbai");

        assertEquals(List.of("BOM", "BLR"), airportRepository.findAll().stream().map(Airport::getCode).toList());
        assertEquals(2, airportRepository.countAirport());
    }

    @Test
    void findByLocation_MatchesSubstring() {
        insertAirport("Indira Gandhi International", "DEL", "New Delhi");
        insertAirport("Chhatrapati Shivaji International", "BOM", "Mumbai");

        List<Airport> airports = airportRepository.findByLocation("delhi");

        assertEquals(List.of("DEL"), airports.stream().map(Airport::getCode).toList());
    }

    @Test
    void findAllForSearch_OmitsContactInfo() {
        insertAirport("Indira Gandhi International", "DEL", "Delhi");

        Airport airport = airportRepository.findAllForSearch().get(0);

        assertEquals("DEL", airport.getCode());
        assertEquals("Delhi", airport.getLocation());
        assertNull(airport.getContactInfo());
    }

    @Test
    void update_ToExistingCode_ThrowsDuplicateKeyException() {
        insertAirport("Indira Gandhi International", "DEL", "Delhi");
        Airport other = airportRepository.findById(insertAirport("Chhatrapati Shivaji International", "BOM", "Mumbai"));
        other.setCode("DEL");

        assertThrows(DuplicateKeyException.class, () -> airportRepository.update(other));
    }

    @Test
    void delete_RemovesAirport() {
        int id = insertAirport("Indira Gandhi International", "DEL", "Delhi");

        airportRepository.delete(id);

        assertThrows(EmptyResultDataAccessException.class, () -> airportRepository.findById(id));
    }
}
