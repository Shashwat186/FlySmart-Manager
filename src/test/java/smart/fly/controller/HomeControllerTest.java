package smart.fly.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import smart.fly.user.model.User;

class HomeControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HomeController()).build();

    @Test
    void home_LoggedIn_RedirectsToDashboard() throws Exception {
        mockMvc.perform(get("/").sessionAttr("user", new User()))
                .andExpect(redirectedUrl("/manager/dashboard"));
    }

    @Test
    void home_NotLoggedIn_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(redirectedUrl("/auth/login"));
    }
}
