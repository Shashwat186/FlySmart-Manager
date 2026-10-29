package smart.fly.flight.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import smart.fly.airline.model.Airline;
import smart.fly.airport.model.Airport;
import smart.fly.dto.FlightSearchDTO;
import smart.fly.exception.FlightManagementException;
import smart.fly.flight.model.Flight;
import smart.fly.flightmanager.model.FlightManager;
import smart.fly.support.MySqlIntegrationTest;

class FlightRepositoryIT extends MySqlIntegrationTest {

    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 12, 1, 9, 30);

    @Autowired
    private FlightRepository flightRepository;

    private int fmId;
    private int otherFmId;
    private int airlineId;
    private int delId;
    private int bomId;

    @BeforeEach
    void setUp() {
        fmId = insertManager("Asha Manager", "asha@flysmart.test");
        otherFmId = insertManager("Ravi Manager", "ravi@flysmart.test");
        airlineId = insertAirline("Sky India", "SKY");
        delId = insertAirport("Indira Gandhi International", "DEL", "Delhi");
        bomId = insertAirport("Chhatrapati Shivaji International", "BOM", "Mumbai");
    }

    @Test
    void findById_MapsJoinedAirlineAndAirports() {
        int flightId = insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);

        Flight flight = flightRepository.findById(flightId);

        assertEquals("SK101", flight.getFlightNumber());
        assertEquals(fmId, flight.getFmId());
        assertEquals("Scheduled", flight.getStatus());
        assertEquals(Timestamp.valueOf(DEPARTURE), flight.getDepartureDateTime());
        assertEquals(Timestamp.valueOf(DEPARTURE.plusHours(2)), flight.getArrivalDateTime());
        assertEquals("SKY", flight.getAirline().getCode());
        assertEquals("DEL", flight.getDepartureAirport().getCode());
        assertEquals("BOM", flight.getArrivalAirport().getCode());
    }

    @Test
    void findById_UnknownId_ThrowsFlightManagementException() {
        assertThrows(FlightManagementException.class, () -> flightRepository.findById(999_999));
    }

    @Test
    void findByNumber_ReturnsFlight() {
        insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);

        Flight flight = flightRepository.findByNumber("SK101");

        assertEquals("SK101", flight.getFlightNumber());
        assertEquals(fmId, flight.getFmId());
    }

    @Test
    void findByNumber_Unknown_ThrowsFlightManagementException() {
        assertThrows(FlightManagementException.class, () -> flightRepository.findByNumber("NOPE"));
    }

    @Test
    void findAllForCustomers_ReturnsAllFlightsOrderedByDeparture() {
        insertFlight("SK102", airlineId, delId, bomId, DEPARTURE.plusDays(1), "Scheduled", otherFmId);
        insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);

        List<Flight> flights = flightRepository.findAllForCustomers();

        assertEquals(List.of("SK101", "SK102"), flights.stream().map(Flight::getFlightNumber).toList());
    }

    @Test
    void findByManagerId_ReturnsOnlyThatManagersFlightsOrderedByDeparture() {
        insertFlight("SK103", airlineId, delId, bomId, DEPARTURE.plusDays(2), "Scheduled", fmId);
        insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);
        insertFlight("SK999", airlineId, bomId, delId, DEPARTURE, "Scheduled", otherFmId);

        List<Flight> flights = flightRepository.findByManagerId(fmId);

        assertEquals(List.of("SK101", "SK103"), flights.stream().map(Flight::getFlightNumber).toList());
    }

    @Test
    void save_PersistsFlightForManager() {
        Flight flight = new Flight();
        flight.setFlightNumber("SK200");
        flight.setDepartureDateTime(Timestamp.valueOf(DEPARTURE));
        flight.setArrivalDateTime(Timestamp.valueOf(DEPARTURE.plusHours(3)));
        flight.setStatus("Scheduled");
        Airline airline = new Airline();
        airline.setAirlineId(airlineId);
        flight.setAirline(airline);
        Airport departure = new Airport();
        departure.setAirportId(delId);
        flight.setDepartureAirport(departure);
        Airport arrival = new Airport();
        arrival.setAirportId(bomId);
        flight.setArrivalAirport(arrival);
        FlightManager manager = new FlightManager();
        manager.setFmId(fmId);
        flight.setFlightManager(manager);

        flightRepository.save(flight);

        List<Flight> flights = flightRepository.findByManagerId(fmId);
        assertEquals(1, flights.size());
        assertEquals("SK200", flights.get(0).getFlightNumber());
        assertEquals("SKY", flights.get(0).getAirline().getCode());
        assertEquals(Timestamp.valueOf(DEPARTURE.plusHours(3)), flights.get(0).getArrivalDateTime());
    }

    @Test
    void update_PersistsChangedColumns() {
        int flightId = insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);
        Flight flight = flightRepository.findById(flightId);
        FlightManager newManager = new FlightManager();
        newManager.setFmId(otherFmId);
        flight.setFlightNumber("SK102");
        flight.setStatus("Delayed");
        flight.setDepartureAirport(flight.getArrivalAirport());
        flight.setDepartureDateTime(Timestamp.valueOf(DEPARTURE.plusHours(1)));
        flight.setFlightManager(newManager);

        flightRepository.update(flight);

        Flight updated = flightRepository.findById(flightId);
        assertEquals("SK102", updated.getFlightNumber());
        assertEquals("Delayed", updated.getStatus());
        assertEquals("BOM", updated.getDepartureAirport().getCode());
        assertEquals(Timestamp.valueOf(DEPARTURE.plusHours(1)), updated.getDepartureDateTime());
        assertEquals(otherFmId, updated.getFmId());
    }

    @Test
    void update_ToExistingFlightNumber_ThrowsDuplicateKeyException() {
        // ManagerController relies on this translation to show its "already exists" message
        insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);
        Flight second = flightRepository.findById(
                insertFlight("SK102", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId));
        FlightManager manager = new FlightManager();
        manager.setFmId(fmId);
        second.setFlightManager(manager);
        second.setFlightNumber("SK101");

        assertThrows(DuplicateKeyException.class, () -> flightRepository.update(second));
    }

    @Test
    void updateStatus_ChangesOnlyStatus() {
        int flightId = insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);

        flightRepository.updateStatus(flightId, "Cancelled");

        Flight flight = flightRepository.findById(flightId);
        assertEquals("Cancelled", flight.getStatus());
        assertEquals("SK101", flight.getFlightNumber());
    }

    @Test
    void delete_RemovesFlight() {
        int flightId = insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);

        flightRepository.delete(flightId);

        assertThrows(FlightManagementException.class, () -> flightRepository.findById(flightId));
    }

    @Test
    void dashboardCounts_AreScopedToManagersUserAndMatchStatusCaseInsensitively() {
        // The flights list page offers upper-case statuses while the queries compare
        // with 'Scheduled'/'Delayed'; MySQL's default collation makes them match.
        insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);
        insertFlight("SK102", airlineId, delId, bomId, DEPARTURE, "SCHEDULED", fmId);
        insertFlight("SK103", airlineId, delId, bomId, DEPARTURE, "Delayed", fmId);
        insertFlight("SK999", airlineId, delId, bomId, DEPARTURE, "Scheduled", otherFmId);
        int userId = userIdOfManager(fmId);

        assertEquals(3, flightRepository.getTotalFlightsByUserId(userId));
        assertEquals(2, flightRepository.getScheduledFlightsByUserId(userId));
        assertEquals(1, flightRepository.getDelayedFlightsByUserId(userId));
    }

    @Test
    void globalStatusCounts_AcceptDoubleQuotedLiterals() {
        // scheduledFlights()/delayedFlights() use Status="..." which only works in MySQL's default sql_mode
        insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);
        insertFlight("SK102", airlineId, delId, bomId, DEPARTURE, "Delayed", otherFmId);

        assertEquals(2, flightRepository.countTotalFlight());
        assertEquals(1, flightRepository.scheduledFlights());
        assertEquals(1, flightRepository.delayedFlights());
    }

    @Test
    void completedFlights_CountsCompletedFlights() {
        insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Completed", fmId);
        insertFlight("SK102", airlineId, delId, bomId, DEPARTURE, "Completed", fmId);
        insertFlight("SK103", airlineId, delId, bomId, DEPARTURE, "Delayed", fmId);

        assertEquals(2, flightRepository.completedFlights());
    }

    @Test
    void searchFlights_ReturnsRouteFlightsWithSeatsInClassFromDate() {
        int economy = insert("seat_class", "seat_class_id", Map.of("class_name", "Economy", "price", 0));
        int match = insertFlight("SK101", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);
        int soldOut = insertFlight("SK102", airlineId, delId, bomId, DEPARTURE, "Scheduled", fmId);
        int tooEarly = insertFlight("SK103", airlineId, delId, bomId, DEPARTURE.minusDays(5), "Scheduled", fmId);
        int cancelled = insertFlight("SK104", airlineId, delId, bomId, DEPARTURE, "Cancelled", fmId);
        insertFlight("SK105", airlineId, bomId, delId, DEPARTURE, "Scheduled", fmId);
        for (int flightId : List.of(match, tooEarly, cancelled)) {
            insertSeats(flightId, economy, 10, 4999.0);
        }
        insertSeats(soldOut, economy, 0, 4999.0);

        List<FlightSearchDTO> results = flightRepository.searchFlights(delId, bomId,
                Date.valueOf(LocalDate.of(2026, 11, 30)), economy);

        assertEquals(1, results.size());
        FlightSearchDTO result = results.get(0);
        assertEquals("SK101", result.getFlightNumber());
        assertEquals(4999.0, result.getPrice());
        assertEquals(120, result.getDuration());
    }

    private void insertSeats(int flightId, int seatClassId, int available, double price) {
        insert("flight_seats", "flight_seat_id", Map.of("flight_id", flightId, "seat_class_id", seatClassId,
                "available_seats", available, "total_seats", 10, "price", price));
    }
}
