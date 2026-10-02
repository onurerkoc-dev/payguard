package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.config.MySqlTestcontainersConfiguration;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.entity.UserRole;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfiguration.class)
@Transactional
class RegistrationPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registration_shouldPersistUserAndAllowLoginWithoutAdminAccess() throws Exception {
        String email = "mvc-" + UUID.randomUUID() + "@example.com";
        String password = "TestOnlyPassword123!";

        MvcResult registration = mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", " Onur ").param("lastName", " Erkoç ")
                        .param("email", email.toUpperCase(java.util.Locale.ROOT))
                        .param("password", password).param("role", "ADMIN")
                        .param("customerId", "999").param("enabled", "false"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/register"))
                .andExpect(unauthenticated())
                .andReturn();

        UserAccount account = userAccountRepository.findByEmail(email).orElseThrow();
        assertEquals(UserRole.USER, account.getRole());
        assertTrue(account.isEnabled());
        assertNotNull(account.getCustomer());
        assertEquals("Onur", account.getCustomer().getFirstName());
        assertEquals("Erkoç", account.getCustomer().getLastName());
        assertEquals(email, account.getCustomer().getEmail());
        assertNotEquals(password, account.getPasswordHash());
        assertTrue(passwordEncoder.matches(password, account.getPasswordHash()));

        mockMvc.perform(get("/register").session((MockHttpSession) registration.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hesabınız oluşturuldu.")));

        MvcResult login = mockMvc.perform(formLogin().user(email).password(password))
                .andExpect(authenticated().withUsername(email).withRoles("USER"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void repeatedRegistration_shouldNotCreateExtraCustomerOrChangePassword() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "Test").param("lastName", "Customer")
                        .param("email", email).param("password", "TestOnlyPassword123!"))
                .andExpect(status().isFound());

        long customerCount = customerRepository.count();
        long accountCount = userAccountRepository.count();
        String passwordHash = userAccountRepository.findByEmail(email).orElseThrow().getPasswordHash();

        mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "Another").param("lastName", "Customer")
                        .param("email", email.toUpperCase(java.util.Locale.ROOT))
                        .param("password", "DifferentPassword123!"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("request", "email"));

        assertEquals(customerCount, customerRepository.count());
        assertEquals(accountCount, userAccountRepository.count());
        assertEquals(passwordHash, userAccountRepository.findByEmail(email).orElseThrow().getPasswordHash());
    }

    @Test
    void passwordOverByteLimit_shouldNotPersistEitherRecord() throws Exception {
        String email = "utf8-" + UUID.randomUUID() + "@example.com";
        long customerCount = customerRepository.count();
        long accountCount = userAccountRepository.count();

        // 37 karakter, fakat UTF-8 kodlamasında 74 byte.
        mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "Test").param("lastName", "Customer")
                        .param("email", email).param("password", "ş".repeat(37)))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("request", "password"));

        assertFalse(userAccountRepository.existsByEmail(email));
        assertFalse(customerRepository.existsByEmail(email));
        assertEquals(customerCount, customerRepository.count());
        assertEquals(accountCount, userAccountRepository.count());
    }
}
