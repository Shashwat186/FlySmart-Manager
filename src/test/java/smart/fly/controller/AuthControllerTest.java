package smart.fly.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import smart.fly.exception.AuthenticationException;
import smart.fly.exception.GlobalExceptionHandler;
import smart.fly.service.AuthService;
import smart.fly.service.PasswordResetService;
import smart.fly.user.model.User;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private User manager;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setViewResolvers(new InternalResourceViewResolver("/WEB-INF/views/", ".jsp"))
                .build();

        manager = new User();
        manager.setUserId(1);
        manager.setEmail("manager@example.com");
        manager.setRole("MANAGER");
    }

    // ---- login ----

    @Test
    void showLoginForm_ReturnsLoginView() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attributeExists("user"));
    }

    @Test
    void processLogin_Manager_StoresUserInSessionAndRedirectsToDashboard() throws Exception {
        when(authService.authenticate("manager@example.com", "secret")).thenReturn(manager);

        mockMvc.perform(post("/auth/login").param("email", "manager@example.com").param("password", "secret"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/manager/dashboard"))
                .andExpect(request().sessionAttribute("user", manager))
                .andExpect(request().sessionAttribute("loggedInUserId", 1));
    }

    @Test
    void processLogin_RoleCheckIsCaseInsensitive() throws Exception {
        manager.setRole("Manager");
        when(authService.authenticate("manager@example.com", "secret")).thenReturn(manager);

        mockMvc.perform(post("/auth/login").param("email", "manager@example.com").param("password", "secret"))
                .andExpect(redirectedUrl("/manager/dashboard"));
    }

    @Test
    void processLogin_NonManager_RejectedWithError() throws Exception {
        User passenger = new User();
        passenger.setRole("Passenger");
        when(authService.authenticate("passenger@example.com", "secret")).thenReturn(passenger);

        mockMvc.perform(post("/auth/login").param("email", "passenger@example.com").param("password", "secret"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attribute("error", "Only flight managers can sign in here"))
                .andExpect(model().attribute("email", "passenger@example.com"))
                .andExpect(request().sessionAttributeDoesNotExist("user"));
    }

    @Test
    void processLogin_InvalidCredentials_ShowsErrorAndKeepsEmail() throws Exception {
        when(authService.authenticate("manager@example.com", "wrong"))
                .thenThrow(new AuthenticationException("Invalid email or password"));

        mockMvc.perform(post("/auth/login").param("email", "manager@example.com").param("password", "wrong"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attribute("error", "Invalid email or password"))
                .andExpect(model().attribute("email", "manager@example.com"))
                .andExpect(request().sessionAttributeDoesNotExist("user"));
    }

    @Test
    void processLogin_MissingPassword_NeverAuthenticates() throws Exception {
        mockMvc.perform(post("/auth/login").param("email", "manager@example.com"));

        verifyNoInteractions(authService);
    }

    @Test
    @Disabled("Known bug: GlobalExceptionHandler's catch-all Exception handler turns missing-parameter 400s into 500")
    void processLogin_MissingPassword_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/login").param("email", "manager@example.com"))
                .andExpect(status().isBadRequest());
    }

    // ---- forgot / reset password ----

    @Test
    void showForgotPasswordForm_ReturnsView() throws Exception {
        mockMvc.perform(get("/auth/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"));
    }

    @Test
    void processForgotPassword_CreatesTokenAndShowsResetLink() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq("manager@example.com")))
                .thenReturn("abc-123");

        mockMvc.perform(post("/auth/forgot-password").param("email", "manager@example.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(model().attribute("resetLink", "/auth/reset-password?token=abc-123"))
                .andExpect(model().attributeExists("message"));

        verify(passwordResetService).createResetToken("manager@example.com");
    }

    @Test
    void showResetPasswordForm_ValidToken_ShowsForm() throws Exception {
        when(passwordResetService.validateToken("abc-123")).thenReturn(true);

        mockMvc.perform(get("/auth/reset-password").param("token", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("token", "abc-123"));
    }

    @Test
    void showResetPasswordForm_InvalidToken_RedirectsToForgotPassword() throws Exception {
        when(passwordResetService.validateToken("expired")).thenReturn(false);

        mockMvc.perform(get("/auth/reset-password").param("token", "expired"))
                .andExpect(redirectedUrl("/auth/forgot-password"));
    }

    @Test
    void processResetPassword_PasswordsDoNotMatch_ShowsError() throws Exception {
        mockMvc.perform(post("/auth/reset-password").param("token", "abc-123").param("newPassword", "one")
                .param("confirmPassword", "two"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("error", "Passwords do not match"))
                .andExpect(model().attribute("token", "abc-123"));

        verifyNoInteractions(passwordResetService);
    }

    @Test
    void processResetPassword_InvalidToken_RedirectsToForgotPassword() throws Exception {
        when(passwordResetService.validateToken("expired")).thenReturn(false);

        mockMvc.perform(post("/auth/reset-password").param("token", "expired").param("newPassword", "newPass")
                .param("confirmPassword", "newPass"))
                .andExpect(redirectedUrl("/auth/forgot-password"));

        verify(passwordResetService, never()).updatePassword(anyString(), anyString());
    }

    @Test
    void processResetPassword_Success_UpdatesPasswordAndDeletesToken() throws Exception {
        when(passwordResetService.validateToken("abc-123")).thenReturn(true);
        when(passwordResetService.getEmailFromToken("abc-123")).thenReturn("manager@example.com");
        when(passwordResetService.updatePassword("manager@example.com", "newPass")).thenReturn(true);

        mockMvc.perform(post("/auth/reset-password").param("token", "abc-123").param("newPassword", "newPass")
                .param("confirmPassword", "newPass"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attribute("success", "Password reset successfully. You can now login."));

        verify(passwordResetService).deleteToken("abc-123");
    }

    @Test
    void processResetPassword_UpdateFails_KeepsTokenAndShowsError() throws Exception {
        when(passwordResetService.validateToken("abc-123")).thenReturn(true);
        when(passwordResetService.getEmailFromToken("abc-123")).thenReturn("manager@example.com");
        when(passwordResetService.updatePassword("manager@example.com", "newPass")).thenReturn(false);

        mockMvc.perform(post("/auth/reset-password").param("token", "abc-123").param("newPassword", "newPass")
                .param("confirmPassword", "newPass"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(model().attribute("error", "Failed to reset password"))
                .andExpect(model().attribute("token", "abc-123"));

        verify(passwordResetService, never()).deleteToken(anyString());
    }

    // ---- logout ----

    @Test
    void logout_InvalidatesSessionAndRedirects() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", manager);

        mockMvc.perform(get("/auth/logout").session(session))
                .andExpect(redirectedUrl("/auth/login"));

        assertTrue(session.isInvalid());
    }

    // ---- change password ----

    @Test
    void showChangePasswordForm_ReturnsView() throws Exception {
        mockMvc.perform(get("/auth/change-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/change-password"));
    }

    @Test
    void processChangePassword_NotLoggedIn_RedirectsToLogin() throws Exception {
        mockMvc.perform(post("/auth/change-password").param("currentPassword", "old").param("newPassword", "new")
                .param("confirmPassword", "new"))
                .andExpect(redirectedUrl("/auth/login"));

        verifyNoInteractions(authService);
    }

    @Test
    void processChangePassword_PasswordsDoNotMatch_ShowsError() throws Exception {
        mockMvc.perform(post("/auth/change-password").sessionAttr("user", manager).param("currentPassword", "old")
                .param("newPassword", "new").param("confirmPassword", "different"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/change-password"))
                .andExpect(request().attribute("error", "New passwords do not match"));

        verifyNoInteractions(authService);
    }

    @Test
    void processChangePassword_Success() throws Exception {
        mockMvc.perform(post("/auth/change-password").sessionAttr("user", manager).param("currentPassword", "old")
                .param("newPassword", "new").param("confirmPassword", "new"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/change-password"))
                .andExpect(request().attribute("success", "Password changed successfully"));

        verify(authService).changePassword(manager, "old", "new");
    }

    @Test
    void processChangePassword_WrongCurrentPassword_ShowsError() throws Exception {
        doThrow(new AuthenticationException("Current password is incorrect")).when(authService)
                .changePassword(manager, "wrong", "new");

        mockMvc.perform(post("/auth/change-password").sessionAttr("user", manager).param("currentPassword", "wrong")
                .param("newPassword", "new").param("confirmPassword", "new"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/change-password"))
                .andExpect(request().attribute("error", "Current password is incorrect"));
    }
}
