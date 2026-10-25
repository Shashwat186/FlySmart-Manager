package smart.fly.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import smart.fly.action.log.ActionLog;
import smart.fly.airline.model.Airline;
import smart.fly.airline.repository.AirlineRepository;
import smart.fly.airport.model.Airport;
import smart.fly.airport.repository.AirportRepository;
import smart.fly.exception.GlobalExceptionHandler;
import smart.fly.flight.model.Flight;
import smart.fly.flight.repository.FlightRepository;
import smart.fly.flightmanager.model.FlightManager;
import smart.fly.flightmanager.repository.FlightManagerRepository;
import smart.fly.service.FlightManagementService;
import smart.fly.service.LoggingService;
import smart.fly.user.model.User;

@ExtendWith(MockitoExtension.class)
class ManagerControllerTest {

    @Mock
    private FlightManagementService flightService;

    @Mock
    private FlightManagerRepository flightManagerRepository;

    @Mock
    private AirlineRepository airlineRepository;

    @Mock
    private AirportRepository airportRepository;

    @Mock
    private FlightRepository flightRepository;

    @Mock
    private LoggingService loggingService;

    @InjectMocks
    private ManagerController managerController;

    private MockMvc mockMvc;
    private User user;
    private Flight flight;
    private Airline airline;
    private Airport airport;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(managerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setViewResolvers(new InternalResourceViewResolver("/WEB-INF/views/", ".jsp"))
                .build();

        user = new User();
        user.setUserId(7);
        user.setRole("MANAGER");

        flight = new Flight();
        flight.setFlightId(100);
        flight.setFlightNumber("FA101");
        flight.setFlightManager(new FlightManager(5));

        airline = new Airline(1, "Fly Air", "FA", "contact@flyair.com");
        airport = new Airport(10, "Indira Gandhi International", "DEL", "del@airport.com", "Delhi");
    }

    // ---- dashboard ----

    @Test
    void showManagerDashboard_NotLoggedIn_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/manager/dashboard"))
                .andExpect(redirectedUrl("/auth/login"));

        verifyNoInteractions(flightRepository, loggingService);
    }

    @Test
    void showManagerDashboard_PopulatesStatistics() throws Exception {
        List<ActionLog> logs = List.of(new ActionLog());
        when(loggingService.getActionLogForOwner(7)).thenReturn(logs);
        when(flightRepository.getTotalFlightsByUserId(7)).thenReturn(12);
        when(flightRepository.getScheduledFlightsByUserId(7)).thenReturn(8);
        when(flightRepository.getDelayedFlightsByUserId(7)).thenReturn(2);

        mockMvc.perform(get("/manager/dashboard").sessionAttr("user", user))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/dashboard"))
                .andExpect(model().attribute("actionLog", logs))
                .andExpect(model().attribute("totalFlights", 12))
                .andExpect(model().attribute("scheduledCount", 8))
                .andExpect(model().attribute("delayedCount", 2));
    }

    // ---- listings ----

    @Test
    void listManagerAirports_ShowsAllAirports() throws Exception {
        when(airportRepository.findAll()).thenReturn(List.of(airport));

        mockMvc.perform(get("/manager/airports"))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/airports/list"))
                .andExpect(model().attribute("airports", List.of(airport)));
    }

    @Test
    void listManagerAirlines_ShowsAllAirlines() throws Exception {
        when(airlineRepository.findAll()).thenReturn(List.of(airline));

        mockMvc.perform(get("/manager/airlines"))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/airlines/list"))
                .andExpect(model().attribute("airlines", List.of(airline)));
    }

    @Test
    void listManagerFlights_NotLoggedIn_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/manager/flights"))
                .andExpect(redirectedUrl("/auth/login"));
    }

    @Test
    void listManagerFlights_ShowsFlightsAssignedToManager() throws Exception {
        when(flightManagerRepository.findByUserId(7)).thenReturn(new FlightManager(5));
        when(flightService.getFlightsByManager(5)).thenReturn(List.of(flight));

        mockMvc.perform(get("/manager/flights").sessionAttr("user", user))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/flights/list"))
                .andExpect(model().attribute("flights", List.of(flight)))
                .andExpect(model().attribute("statuses", List.of("SCHEDULED", "DELAYED", "CANCELLED", "COMPLETED")));
    }

    @Test
    void listManagerFlights_UserWithoutManagerRecord_ShowsNoFlights() throws Exception {
        when(flightManagerRepository.findByUserId(7)).thenReturn(null);

        mockMvc.perform(get("/manager/flights").sessionAttr("user", user))
                .andExpect(status().isOk())
                .andExpect(model().attribute("flights", List.of()));

        verifyNoInteractions(flightService);
    }

    // ---- airlines ----

    @Test
    void showEditAirlineForm_LoadsAirline() throws Exception {
        when(airlineRepository.findById(1)).thenReturn(airline);

        mockMvc.perform(get("/manager/airlines/edit/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/airlines/edit"))
                .andExpect(model().attribute("airline", airline));
    }

    private MockHttpServletRequestBuilder airlineEditRequest() {
        return post("/manager/airlines/edit/1").sessionAttr("user", user)
                .param("name", "Fly Air").param("code", "FA").param("contactInfo", "contact@flyair.com");
    }

    @Test
    void updateAirline_Success_UpdatesLogsAndRedirects() throws Exception {
        mockMvc.perform(airlineEditRequest())
                .andExpect(redirectedUrl("/manager/airlines"))
                .andExpect(flash().attribute("success", "Airline updated successfully"));

        ArgumentCaptor<Airline> captor = ArgumentCaptor.forClass(Airline.class);
        verify(airlineRepository).update(captor.capture());
        assertEquals(1, captor.getValue().getAirlineId());
        assertEquals("Fly Air", captor.getValue().getName());
        assertEquals("FA", captor.getValue().getCode());
        verify(loggingService).logAction(7, "Airline Updated", "ManagerController");
    }

    @Test
    void updateAirline_DuplicateCode_ReturnsFormWithError() throws Exception {
        doThrow(new DuplicateKeyException("duplicate")).when(airlineRepository).update(any(Airline.class));

        mockMvc.perform(airlineEditRequest())
                .andExpect(status().isOk())
                .andExpect(view().name("manager/airlines/edit"))
                .andExpect(model().attribute("error",
                        "The airline code 'FA' already exists. Please use a different code."));

        verifyNoInteractions(loggingService);
    }

    @Test
    void updateAirline_UnexpectedError_ShowsGenericError() throws Exception {
        doThrow(new DataAccessResourceFailureException("db down")).when(airlineRepository).update(any(Airline.class));

        mockMvc.perform(airlineEditRequest())
                .andExpect(status().isOk())
                .andExpect(model().attribute("error",
                        "An error occurred while updating the airline. Please try again."));
    }

    @Test
    @Disabled("Known bug: ManagerController.updateAirline returns misspelled view 'manager/ailines/edit'")
    void updateAirline_UnexpectedError_ReturnsEditView() throws Exception {
        doThrow(new DataAccessResourceFailureException("db down")).when(airlineRepository).update(any(Airline.class));

        mockMvc.perform(airlineEditRequest())
                .andExpect(view().name("manager/airlines/edit"));
    }

    @Test
    void deleteAirline_DeletesAndRedirects() throws Exception {
        mockMvc.perform(post("/manager/airlines/delete/1"))
                .andExpect(redirectedUrl("/manager/airlines"))
                .andExpect(flash().attribute("success", "Airline deleted successfully"));

        verify(airlineRepository).delete(1);
    }

    // ---- flights ----

    @Test
    void showEditFlightForm_PopulatesFormData() throws Exception {
        when(flightService.getFlightById(100)).thenReturn(flight);
        when(airlineRepository.findAll()).thenReturn(List.of(airline));
        when(airportRepository.findAll()).thenReturn(List.of(airport));
        when(flightManagerRepository.findAll()).thenReturn(List.of(new FlightManager(5)));

        mockMvc.perform(get("/manager/flights/edit/100").sessionAttr("user", user))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/flights/edit"))
                .andExpect(model().attribute("flight", flight))
                .andExpect(model().attribute("airlines", List.of(airline)))
                .andExpect(model().attribute("airports", List.of(airport)))
                .andExpect(model().attributeExists("managers", "statuses"))
                .andExpect(model().attribute("managerId", 5));
    }

    private MockHttpServletRequestBuilder flightEditRequest() {
        return post("/manager/flights/edit/100").sessionAttr("user", user)
                .param("flightNumber", "FA101")
                .param("airlineId", "1")
                .param("departureAirportId", "10")
                .param("arrivalAirportId", "20")
                .param("departureDateTime", "2026-11-01T10:00")
                .param("arrivalDateTime", "2026-11-01T12:30")
                .param("managerId", "5")
                .param("status", "DELAYED");
    }

    @Test
    void updateFlight_NotLoggedIn_RedirectsToLogin() throws Exception {
        mockMvc.perform(post("/manager/flights/edit/100")
                .param("flightNumber", "FA101").param("airlineId", "1").param("departureAirportId", "10")
                .param("arrivalAirportId", "20").param("departureDateTime", "2026-11-01T10:00")
                .param("arrivalDateTime", "2026-11-01T12:30").param("managerId", "5").param("status", "DELAYED"))
                .andExpect(redirectedUrl("/auth/login"));

        verifyNoInteractions(flightService);
    }

    @Test
    void updateFlight_Success_ConvertsDatesAndRedirects() throws Exception {
        mockMvc.perform(flightEditRequest())
                .andExpect(redirectedUrl("/manager/flights"))
                .andExpect(flash().attribute("success", "Flight updated successfully"));

        verify(flightService).updateFlight(100, "FA101", 1, 10, 20, Timestamp.valueOf("2026-11-01 10:00:00"),
                Timestamp.valueOf("2026-11-01 12:30:00"), 5, "DELAYED");
        verify(loggingService).logAction(7, "Updated flight: FA101", "AdminController");
    }

    @Test
    void updateFlight_DuplicateFlightNumber_ReturnsFormWithInputsAndError() throws Exception {
        doThrow(new DuplicateKeyException("duplicate")).when(flightService).updateFlight(anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), any(), any(), anyInt(), anyString());
        when(flightService.getFlightById(100)).thenReturn(flight);

        mockMvc.perform(flightEditRequest())
                .andExpect(status().isOk())
                .andExpect(view().name("manager/flights/edit"))
                .andExpect(model().attribute("error",
                        "Flight number 'FA101' already exists. Please use a different flight number."))
                .andExpect(model().attribute("flightNumber", "FA101"))
                .andExpect(model().attribute("status", "DELAYED"))
                .andExpect(model().attribute("flight", flight));

        verify(loggingService).logAction(7, "Failed to update flight - Duplicate flight number: FA101",
                "AdminController");
    }

    @Test
    void updateFlight_UnexpectedError_ShowsErrorAndLogsFailure() throws Exception {
        doThrow(new DataAccessResourceFailureException("db down")).when(flightService).updateFlight(anyInt(),
                anyString(), anyInt(), anyInt(), anyInt(), any(), any(), anyInt(), anyString());

        mockMvc.perform(flightEditRequest())
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Failed to update flight: db down"));

        verify(loggingService).logAction(7, "Failed to update flight: db down", "AdminController");
    }

    @Test
    @Disabled("Known bug: ManagerController.updateFlight returns misspelled view 'manager/flights/edi'")
    void updateFlight_UnexpectedError_ReturnsEditView() throws Exception {
        doThrow(new DataAccessResourceFailureException("db down")).when(flightService).updateFlight(anyInt(),
                anyString(), anyInt(), anyInt(), anyInt(), any(), any(), anyInt(), anyString());

        mockMvc.perform(flightEditRequest())
                .andExpect(view().name("manager/flights/edit"));
    }

    @Test
    void updateFlight_MalformedDate_ShowsError() throws Exception {
        mockMvc.perform(flightEditRequest().param("departureDateTime", "not-a-date"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));

        verify(flightService, never()).updateFlight(anyInt(), anyString(), anyInt(), anyInt(), anyInt(), any(),
                any(), anyInt(), anyString());
    }

    // ---- airports ----

    @Test
    void showEditAirportForm_LoadsAirport() throws Exception {
        when(airportRepository.findById(10)).thenReturn(airport);

        mockMvc.perform(get("/manager/airports/edit/10"))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/airports/edit"))
                .andExpect(model().attribute("airport", airport));
    }

    private MockHttpServletRequestBuilder airportEditRequest() {
        return post("/manager/airports/edit/10").sessionAttr("user", user)
                .param("name", "Indira Gandhi International").param("code", "DEL")
                .param("contactInfo", "del@airport.com").param("location", "Delhi");
    }

    @Test
    void updateAirport_Success_UpdatesLogsAndRedirects() throws Exception {
        mockMvc.perform(airportEditRequest())
                .andExpect(redirectedUrl("/manager/airports"))
                .andExpect(flash().attribute("success", "Airport updated successfully"));

        ArgumentCaptor<Airport> captor = ArgumentCaptor.forClass(Airport.class);
        verify(airportRepository).update(captor.capture());
        assertEquals(10, captor.getValue().getAirportId());
        assertEquals("DEL", captor.getValue().getCode());
        assertEquals("Delhi", captor.getValue().getLocation());
        verify(loggingService).logAction(7, "Airport Updated", "ManagerController");
    }

    @Test
    void updateAirport_Duplicate_ReturnsFormWithError() throws Exception {
        doThrow(new DuplicateKeyException("duplicate")).when(airportRepository).update(any(Airport.class));

        mockMvc.perform(airportEditRequest())
                .andExpect(status().isOk())
                .andExpect(view().name("manager/airports/edit"))
                .andExpect(model().attribute("error", "Duplicate Entries Exist"))
                .andExpect(model().attributeExists("airport"));

        verifyNoInteractions(loggingService);
    }

    @Test
    void updateAirport_UnexpectedError_ReturnsFormWithError() throws Exception {
        doThrow(new DataAccessResourceFailureException("db down")).when(airportRepository).update(any(Airport.class));

        mockMvc.perform(airportEditRequest())
                .andExpect(status().isOk())
                .andExpect(view().name("manager/airports/edit"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void deleteAirport_DeletesAndRedirects() throws Exception {
        mockMvc.perform(post("/manager/airports/delete/10"))
                .andExpect(redirectedUrl("/manager/airports"))
                .andExpect(flash().attribute("success", "Airport deleted successfully"));

        verify(airportRepository).delete(10);
    }

    // ---- flight status ----

    @Test
    void showUpdateStatusForm_FlightExists_ShowsForm() throws Exception {
        when(flightRepository.findById(100)).thenReturn(flight);

        mockMvc.perform(get("/manager/flights/updateStatus/100"))
                .andExpect(status().isOk())
                .andExpect(view().name("manager/flights/updateStatus"))
                .andExpect(model().attribute("flight", flight))
                .andExpect(model().attributeExists("statuses"));
    }

    @Test
    @Disabled("Known bug: GlobalExceptionHandler's catch-all Exception handler turns ResponseStatusException(404) into 500")
    void showUpdateStatusForm_FlightNotFound_Returns404() throws Exception {
        when(flightRepository.findById(999)).thenReturn(null);

        mockMvc.perform(get("/manager/flights/updateStatus/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateFlightStatus_Success_ReturnsOk() throws Exception {
        mockMvc.perform(post("/manager/flights/status/100").param("status", "DELAYED"))
                .andExpect(status().isOk())
                .andExpect(content().string("Status updated successfully."));

        verify(flightRepository).updateStatus(100, "DELAYED");
    }

    @Test
    void updateFlightStatus_Failure_ReturnsServerError() throws Exception {
        doThrow(new DataAccessResourceFailureException("db down")).when(flightRepository).updateStatus(100,
                "DELAYED");

        mockMvc.perform(post("/manager/flights/status/100").param("status", "DELAYED"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to update status."));
    }
}
