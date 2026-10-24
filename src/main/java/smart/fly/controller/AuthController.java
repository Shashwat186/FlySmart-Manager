package smart.fly.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import smart.fly.exception.AuthenticationException;
import smart.fly.service.AuthService;
import smart.fly.service.PasswordResetService;
import smart.fly.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/auth")
public class AuthController {
	private final AuthService authService;
	private final PasswordResetService passwordResetService;
	private final JdbcTemplate jdbcTemplate;

	public AuthController(AuthService authService, PasswordResetService passwordResetService,
			JdbcTemplate jdbcTemplate) {
		super();
		this.authService = authService;
		this.passwordResetService = passwordResetService;
		this.jdbcTemplate = jdbcTemplate;

	}

	@GetMapping("/login")
	public String showLoginForm(Model model) {
		model.addAttribute("user", new User());
		return "auth/login";
	}

	@PostMapping("/login")
	public String processLogin(@RequestParam String email, @RequestParam String password,
	    HttpSession session, Model model) {

	    try {
	        User user = authService.authenticate(email, password);
	        // This application is the flight manager console only
	        if (!"MANAGER".equalsIgnoreCase(user.getRole())) {
	            throw new AuthenticationException("Only flight managers can sign in here");
	        }
	        session.setAttribute("user", user);
	        session.setAttribute("loggedInUserId", user.getUserId());
	        return "redirect:/manager/dashboard";
	    } catch (AuthenticationException e) {
	        // Repopulate the form fields
	        model.addAttribute("email", email);

	        model.addAttribute("error", e.getMessage());
	        return "auth/login";
	    }
	}

	@GetMapping("/forgot-password")
	public String showForgotPasswordForm() {
		return "auth/forgot-password";
	}

	@PostMapping("/forgot-password")
	public String processForgotPassword(@RequestParam String email, Model model) {
		// Create reset token (whether or not email exists - for security)
		passwordResetService.createResetToken(email);

		// For a simple app, show the link (in real app, would email it)
		// Scaling and the new scope of the application will include this.
		String token = jdbcTemplate.queryForObject("SELECT token FROM password_reset_tokens WHERE email = ?",
				String.class, email);

		model.addAttribute("resetLink", "/auth/reset-password?token=" + token);
		model.addAttribute("message", "If your email is registered, a reset link has been generated");

		return "auth/forgot-password";
	}

	@GetMapping("/reset-password")
	public String showResetPasswordForm(@RequestParam String token, Model model) {
		if (!passwordResetService.validateToken(token)) {
			model.addAttribute("error", "Invalid or expired reset token");
			return "redirect:/auth/forgot-password";
		}

		model.addAttribute("token", token);
		return "auth/reset-password";
	}

	@PostMapping("/reset-password")
	public String processResetPassword(@RequestParam String token, @RequestParam String newPassword,
			@RequestParam String confirmPassword, Model model) {

		if (!newPassword.equals(confirmPassword)) {
			model.addAttribute("error", "Passwords do not match");
			model.addAttribute("token", token);
			return "auth/reset-password";
		}

		if (!passwordResetService.validateToken(token)) {
			model.addAttribute("error", "Invalid or expired reset token");
			return "redirect:/auth/forgot-password";
		}

		// Get email from token
		String email = passwordResetService.getEmailFromToken(token);

		// Update password using your PasswordUtil
		boolean success = passwordResetService.updatePassword(email, newPassword);

		if (success) {
			// Delete the used token
			passwordResetService.deleteToken(token);

			model.addAttribute("success", "Password reset successfully. You can now login.");
			return "auth/login";
		} else {
			model.addAttribute("error", "Failed to reset password");
			model.addAttribute("token", token);
			return "auth/reset-password";
		}
	}

	@GetMapping("/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/auth/login";
	}

	@GetMapping("/change-password")
	public String showChangePasswordForm() {
		return "auth/change-password";
	}

	@PostMapping("/change-password")
	public String processChangePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
			@RequestParam String confirmPassword, HttpSession session, HttpServletRequest request) {

		// Get current logged-in user
		User currentUser = (User) session.getAttribute("user");
		if (currentUser == null) {
			return "redirect:/auth/login";
		}

		// Validate password inputs
		if (!newPassword.equals(confirmPassword)) {
			request.setAttribute("error", "New passwords do not match");
			return "auth/change-password";
		}

		try {
			authService.changePassword(currentUser, currentPassword, newPassword);
			request.setAttribute("success", "Password changed successfully");
			return "auth/change-password";
		} catch (AuthenticationException e) {
			request.setAttribute("error", e.getMessage());
			return "auth/change-password";
		}
	}

}