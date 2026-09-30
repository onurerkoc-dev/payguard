package dev.onurerkoc.payguard.service;

import dev.onurerkoc.payguard.config.MySqlTestcontainersConfiguration;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.entity.UserRole;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "payguard.admin-bootstrap.enabled=true",
        "payguard.admin-bootstrap.email= BOOTSTRAP@EXAMPLE.COM ",
        "payguard.admin-bootstrap.password=TestOnlyPassword123!"
})
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfiguration.class)
class AdminBootstrapIntegrationTest {

    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private AdminBootstrapService adminBootstrapService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MockMvc mockMvc;

    @Test
    void applicationStartup_shouldPersistAdminWithWorkingPassword() {
        UserAccount admin = userAccountRepository.findByEmail("bootstrap@example.com")
                .orElseThrow();

        assertEquals(UserRole.ADMIN, admin.getRole());
        assertTrue(admin.isEnabled());
        assertNull(admin.getCustomer());
        assertTrue(passwordEncoder.matches("TestOnlyPassword123!", admin.getPasswordHash()));
    }

    @Test
    void repeatedBootstrap_shouldKeepSavedIdAndPassword() {
        UserAccount before = userAccountRepository.findByEmail("bootstrap@example.com")
                .orElseThrow();
        long accountCount = userAccountRepository.count();

        boolean created = adminBootstrapService.createAdmin(
                "bootstrap@example.com", "DifferentPassword123!"
        );

        UserAccount after = userAccountRepository.findByEmail("bootstrap@example.com")
                .orElseThrow();
        assertFalse(created);
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getPasswordHash(), after.getPasswordHash());
        assertEquals(accountCount, userAccountRepository.count());
    }

    @Test
    void adminFormLogin_shouldReachDashboardWithAuthenticatedSession() throws Exception {
        MvcResult login = mockMvc.perform(formLogin()
                        .user("bootstrap@example.com")
                        .password("TestOnlyPassword123!"))
                .andExpect(status().isFound())
                .andExpect(authenticated().withUsername("bootstrap@example.com"))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/admin"));
        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-dashboard"))
                .andExpect(content().string(containsString("Yönetici paneli")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void bootstrappedAdmin_shouldAuthenticateAndAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .with(httpBasic("bootstrap@example.com", "TestOnlyPassword123!")))
                .andExpect(status().isOk());
    }
}
