package smart.fly.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import smart.fly.airline.model.Airline;
import smart.fly.airline.repository.AirlineRepository;
import smart.fly.airport.model.Airport;
import smart.fly.airport.repository.AirportRepository;
import smart.fly.businness.owner.model.BusinessOwner;
import smart.fly.dto.FlightFormDTO;
import smart.fly.exception.FlightManagementException;
import smart.fly.flight.model.Flight;
import smart.fly.flight.repository.FlightRepository;
import smart.fly.flight.seats.repository.FlightSeatsRepository;
import smart.fly.flightmanager.model.FlightManager;
import smart.fly.flightmanager.repository.FlightManagerRepository;
import smart.fly.seatclass.repository.SeatClassRepository;
import smart.fly.user.model.User;
import smart.fly.user.repository.UserRepository;
import smart.fly.util.PasswordUtil;

@ExtendWith(MockitoExtension.class)
class FlightManagementServiceTest {

    private static final String SEAT_INSERT_SQL = "INSERT INTO flight_seats (Flight_ID, Seat_Class_ID, Available_Seats, Total_Seats, price) VALUES (?, ?, ?, ?, ?)";

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private FlightManagerRepository flightManagerRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AirlineRepository airlineRepository;

    @Mock
    private AirportRepository airportRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordUtil passwordUtil;

    @Mock
    private FlightSeatsRepository flightSeatsRepository;

    @Mock
    private SeatClassRepository seatClassRepository;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @InjectMocks
    private FlightManagementService flightService;

    private Flight testFlight;
    private Airline airline;
    private Airport departureAirport;
    private Airport arrivalAirport;
    private FlightManager manager;
    private final Timestamp departure = Timestamp.valueOf("2026-11-01 10:00:00");
    private final Timestamp arrival = Timestamp.valueOf("2026-11-01 12:30:00");

    @BeforeEach
    void setUp() {
        airline = new Airline(1, "Fly Air", "FA", "contact@flyair.com");
        departureAirport = new Airport(10, "Indira Gandhi International", "DEL", "del@airport.com", "Delhi");
        arrivalAirport = new Airport(20, "Chhatrapati Shivaji International", "BOM", "bom@airport.com", "Mumbai");
        manager = new FlightManager(5);

        testFlight = new Flight();
        testFlight.setFlightId(100);
        testFlight.setFlightNumber("FA101");
        testFlight.setDepartureDateTime(departure);
        testFlight.setArrivalDateTime(arrival);
        testFlight.setStatus("SCHEDULED");
    }

    private void stubReferenceData() {
        when(airlineRepository.findById(1)).thenReturn(airline);
        when(airportRepository.findById(10)).thenReturn(departureAirport);
        when(airportRepository.findById(20)).thenReturn(arrivalAirport);
        when(flightManagerRepository.findById(5)).thenReturn(manager);
    }

    // ---- simple delegations ----

    @Test
    void getAllFlights_ReturnsAllFlights() {
        when(flightRepository.findAll()).thenReturn(List.of(testFlight));

        assertEquals(List.of(testFlight), flightService.getAllFlights());
    }

    @Test
    void getFlightsByManager_ReturnsManagersFlights() {
        when(flightRepository.findByManagerId(5)).thenReturn(List.of(testFlight));

        assertEquals(List.of(testFlight), flightService.getFlightsByManager(5));
    }

    @Test
    void getFlightById_ReturnsFlight() {
        when(flightRepository.findById(100)).thenReturn(testFlight);

        assertSame(testFlight, flightService.getFlightById(100));
    }

    @Test
    void getFlightById_NotFound_ReturnsNull() {
        when(flightRepository.findById(999)).thenReturn(null);

        assertNull(flightService.getFlightById(999));
    }

    // ---- addFlight ----

    @Test
    void addFlight_Success() {
        stubReferenceData();

        flightService.addFlight(testFlight, 1, 10, 20, 5, 1);

        assertSame(airline, testFlight.getAirline());
        assertSame(departureAirport, testFlight.getDepartureAirport());
        assertSame(arrivalAirport, testFlight.getArrivalAirport());
        assertSame(manager, testFlight.getFlightManager());
        verify(flightRepository).save(testFlight);
    }

    @Test
    void addFlight_MissingIds_ThrowsWithoutTouchingRepositories() {
        FlightManagementException ex = assertThrows(FlightManagementException.class,
                () -> flightService.addFlight(testFlight, null, 10, 20, 5, 1));

        assertEquals("All required IDs must be provided", ex.getMessage());
        verifyNoInteractions(airlineRepository, airportRepository, flightManagerRepository, flightRepository);
    }

    @Test
    void addFlight_MissingManagerId_Throws() {
        assertThrows(FlightManagementException.class, () -> flightService.addFlight(testFlight, 1, 10, 20, null, 1));
    }

    @Test
    void addFlight_InvalidReferenceData_Throws() {
        when(airlineRepository.findById(1)).thenReturn(null);
        when(airportRepository.findById(10)).thenReturn(departureAirport);
        when(airportRepository.findById(20)).thenReturn(arrivalAirport);
        when(flightManagerRepository.findById(5)).thenReturn(manager);

        FlightManagementException ex = assertThrows(FlightManagementException.class,
                () -> flightService.addFlight(testFlight, 1, 10, 20, 5, 1));

        assertEquals("Invalid reference data provided", ex.getMessage());
        verify(flightRepository, never()).save(any());
    }

    @Test
    void addFlight_ArrivalBeforeDeparture_Throws() {
        stubReferenceData();
        testFlight.setArrivalDateTime(Timestamp.valueOf("2026-11-01 09:00:00"));

        FlightManagementException ex = assertThrows(FlightManagementException.class,
                () -> flightService.addFlight(testFlight, 1, 10, 20, 5, 1));

        assertEquals("Arrival time must be after departure time", ex.getMessage());
        verify(flightRepository, never()).save(any());
    }

    // ---- updateFlightFromDTO ----

    @Test
    void updateFlightFromDTO_Success() {
        stubReferenceData();
        when(flightRepository.findById(100)).thenReturn(testFlight);
        FlightFormDTO form = new FlightFormDTO("FA202", 1, 10, 20, "2026-12-01T08:15", "2026-12-01T10:45", 5);

        flightService.updateFlightFromDTO(100, form);

        assertEquals("FA202", testFlight.getFlightNumber());
        assertEquals(Timestamp.valueOf("2026-12-01 08:15:00"), testFlight.getDepartureDateTime());
        assertEquals(Timestamp.valueOf("2026-12-01 10:45:00"), testFlight.getArrivalDateTime());
        assertSame(airline, testFlight.getAirline());
        assertSame(departureAirport, testFlight.getDepartureAirport());
        assertSame(arrivalAirport, testFlight.getArrivalAirport());
        assertSame(manager, testFlight.getFlightManager());
        verify(flightRepository).update(testFlight);
    }

    @Test
    void updateFlightFromDTO_FlightNotFound_Throws() {
        when(flightRepository.findById(100)).thenReturn(null);
        FlightFormDTO form = new FlightFormDTO("FA202", 1, 10, 20, "2026-12-01T08:15", "2026-12-01T10:45", 5);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> flightService.updateFlightFromDTO(100, form));

        assertEquals("Flight not found", ex.getMessage());
        verify(flightRepository, never()).update(any());
    }

    @Test
    void updateFlightFromDTO_AirlineNotFound_Throws() {
        when(flightRepository.findById(100)).thenReturn(testFlight);
        when(airlineRepository.findById(1)).thenReturn(null);
        FlightFormDTO form = new FlightFormDTO("FA202", 1, 10, 20, "2026-12-01T08:15", "2026-12-01T10:45", 5);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> flightService.updateFlightFromDTO(100, form));

        assertEquals("Airline not found", ex.getMessage());
        verify(flightRepository, never()).update(any());
    }

    @Test
    void updateFlightFromDTO_ArrivalAirportNotFound_Throws() {
        when(flightRepository.findById(100)).thenReturn(testFlight);
        when(airlineRepository.findById(1)).thenReturn(airline);
        when(airportRepository.findById(10)).thenReturn(departureAirport);
        when(airportRepository.findById(20)).thenReturn(null);
        FlightFormDTO form = new FlightFormDTO("FA202", 1, 10, 20, "2026-12-01T08:15", "2026-12-01T10:45", 5);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> flightService.updateFlightFromDTO(100, form));

        assertEquals("Arrival airport not found", ex.getMessage());
    }

    @Test
    void updateFlightFromDTO_ManagerNotFound_Throws() {
        when(flightRepository.findById(100)).thenReturn(testFlight);
        when(airlineRepository.findById(1)).thenReturn(airline);
        when(airportRepository.findById(10)).thenReturn(departureAirport);
        when(airportRepository.findById(20)).thenReturn(arrivalAirport);
        when(flightManagerRepository.findById(5)).thenReturn(null);
        FlightFormDTO form = new FlightFormDTO("FA202", 1, 10, 20, "2026-12-01T08:15", "2026-12-01T10:45", 5);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> flightService.updateFlightFromDTO(100, form));

        assertEquals("Manager not found", ex.getMessage());
        verify(flightRepository, never()).update(any());
    }

    // ---- updateFlight ----

    @Test
    void updateFlight_ExecutesUpdateWithAllFields() {
        flightService.updateFlight(100, "FA101", 1, 10, 20, departure, arrival, 5, "DELAYED");

        verify(jdbcTemplate).update(startsWith("UPDATE flight SET"), eq("FA101"), eq(1), eq(10), eq(20), eq(departure),
                eq(arrival), eq(5), eq("DELAYED"), eq(100));
    }

    // ---- deleteFlight ----

    @Test
    void deleteFlight_Success() {
        when(flightRepository.findById(100)).thenReturn(testFlight);

        flightService.deleteFlight(100);

        verify(flightRepository).delete(100);
    }

    @Test
    void deleteFlight_NotFound_Throws() {
        when(flightRepository.findById(100)).thenReturn(null);

        FlightManagementException ex = assertThrows(FlightManagementException.class,
                () -> flightService.deleteFlight(100));

        assertEquals("Failed to delete flight: Flight not found", ex.getMessage());
        verify(flightRepository, never()).delete(anyInt());
    }

    @Test
    void deleteFlight_RepositoryError_WrapsException() {
        when(flightRepository.findById(100)).thenReturn(testFlight);
        doThrow(new DataIntegrityViolationException("FK constraint")).when(flightRepository).delete(100);

        FlightManagementException ex = assertThrows(FlightManagementException.class,
                () -> flightService.deleteFlight(100));

        assertEquals("Failed to delete flight: FK constraint", ex.getMessage());
    }

    // ---- updateFlightStatus ----

    @Test
    void updateFlightStatus_UpdatesStatusAndPersists() {
        when(flightRepository.findById(100)).thenReturn(testFlight);

        flightService.updateFlightStatus(100, "DELAYED");

        assertEquals("DELAYED", testFlight.getStatus());
        verify(flightRepository).update(testFlight);
    }

    // ---- createFlightManager ----

    @Test
    void createFlightManager_Success() {
        User user = new User();
        user.setEmail("new.manager@example.com");
        User savedUser = new User();
        savedUser.setUserId(42);
        BusinessOwner owner = new BusinessOwner();
        FlightManager savedManager = new FlightManager(9);

        when(userRepository.emailExists("new.manager@example.com")).thenReturn(false);
        when(passwordUtil.generateSalt()).thenReturn("salt");
        when(passwordUtil.hashPassword("pass123", "salt")).thenReturn("hash");
        when(userRepository.save(user)).thenReturn(savedUser);
        when(flightManagerRepository.save(any(FlightManager.class))).thenReturn(savedManager);

        FlightManager result = flightService.createFlightManager(user, "pass123", owner);

        assertSame(savedManager, result);
        assertEquals("hash", user.getPasswordHash());
        assertEquals("salt", user.getPasswordSalt());
        assertEquals("Manager", user.getRole());

        ArgumentCaptor<FlightManager> captor = ArgumentCaptor.forClass(FlightManager.class);
        verify(flightManagerRepository).save(captor.capture());
        assertSame(savedUser, captor.getValue().getUser());
        assertSame(owner, captor.getValue().getBusinessOwner());
    }

    @Test
    void createFlightManager_EmailExists_Throws() {
        User user = new User();
        user.setEmail("taken@example.com");
        when(userRepository.emailExists("taken@example.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> flightService.createFlightManager(user, "pass123", new BusinessOwner()));

        assertEquals("Email already exists", ex.getMessage());
        verify(userRepository, never()).save(any());
        verify(flightManagerRepository, never()).save(any());
        verifyNoInteractions(passwordUtil);
    }

    // ---- addFlightWithSeats ----

    private void stubFlightInsert(Number generatedId) throws Exception {
        when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
                .thenReturn(preparedStatement);
        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            creator.createPreparedStatement(connection);
            if (generatedId != null) {
                KeyHolder keyHolder = invocation.getArgument(1);
                keyHolder.getKeyList().add(Map.of("GENERATED_KEY", generatedId));
            }
            return 1;
        });
    }

    @Test
    void addFlightWithSeats_Success() throws Exception {
        stubReferenceData();
        stubFlightInsert(42);
        when(jdbcTemplate.update(SEAT_INSERT_SQL, 42, 1, 150, 150, 0.0)).thenReturn(1);
        when(jdbcTemplate.update(SEAT_INSERT_SQL, 42, 2, 30, 30, 0.0)).thenReturn(1);
        when(jdbcTemplate.update(SEAT_INSERT_SQL, 42, 3, 10, 10, 0.0)).thenReturn(1);

        flightService.addFlightWithSeats(testFlight, 1, 10, 20, 5, 1, 150, 30, 10);

        assertEquals(42, testFlight.getFlightId());
        assertSame(airline, testFlight.getAirline());
        verify(preparedStatement).setString(1, "FA101");
        verify(preparedStatement).setInt(2, 1);
        verify(preparedStatement).setInt(3, 10);
        verify(preparedStatement).setInt(4, 20);
        verify(preparedStatement).setTimestamp(5, departure);
        verify(preparedStatement).setTimestamp(6, arrival);
        verify(preparedStatement).setString(7, "SCHEDULED");
        verify(preparedStatement).setInt(8, 5);
        verify(jdbcTemplate).update(SEAT_INSERT_SQL, 42, 1, 150, 150, 0.0);
        verify(jdbcTemplate).update(SEAT_INSERT_SQL, 42, 2, 30, 30, 0.0);
        verify(jdbcTemplate).update(SEAT_INSERT_SQL, 42, 3, 10, 10, 0.0);
    }

    @Test
    void addFlightWithSeats_NoGeneratedKey_Throws() throws Exception {
        stubReferenceData();
        stubFlightInsert(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> flightService.addFlightWithSeats(testFlight, 1, 10, 20, 5, 1, 150, 30, 10));

        assertEquals("Failed to retrieve generated Flight ID.", ex.getMessage());
        verify(jdbcTemplate, never()).update(eq(SEAT_INSERT_SQL), any(Object[].class));
    }

    @Test
    void addFlightWithSeats_SeatInsertFails_Throws() throws Exception {
        stubReferenceData();
        stubFlightInsert(42);
        when(jdbcTemplate.update(SEAT_INSERT_SQL, 42, 1, 150, 150, 0.0)).thenReturn(0);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> flightService.addFlightWithSeats(testFlight, 1, 10, 20, 5, 1, 150, 30, 10));

        assertEquals("Failed to insert Economy class seats", ex.getMessage());
        verify(jdbcTemplate, never()).update(SEAT_INSERT_SQL, 42, 2, 30, 30, 0.0);
    }
}
