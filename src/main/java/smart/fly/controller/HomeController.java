package smart.fly.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;

@Controller
public class HomeController {

	@GetMapping("/")
	public String home(HttpSession session) {
		// Send signed-in managers straight to their dashboard
		if (session.getAttribute("user") != null) {
			return "redirect:/manager/dashboard";
		}
		return "redirect:/auth/login";
	}
}
