package dev.onurerkoc.payguard.security;

import dev.onurerkoc.payguard.config.SecurityConfig;
import dev.onurerkoc.payguard.controller.CustomerController;
import dev.onurerkoc.payguard.dto.CustomerResponse;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import dev.onurerkoc.payguard.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;


@WebMvcTest(CustomerController.class)
@Import({
        SecurityConfig.class,
        PayGuardUserDetailsService.class
})
class PayGuardSessionLoginTest {

    @Autowired
    private MockMvc mockMvc;

    // SecurityConfig içindeki gerçek BCrypt bean'i.
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void login_whenCredentialsAreValid_shouldAllowAccessWithSession()
            throws Exception {

        // GIVEN: Gerçek BCrypt hash'i bulunan hesap.
        String rawPassword = "GuvenliSifre123!";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        ReflectionTestUtils.setField(customer, "id", 7L);

        UserAccount account = new UserAccount(
                "onur@example.com",
                passwordEncoder.encode(rawPassword),
                customer
        );

        ReflectionTestUtils.setField(account, "id", 3L);

        when(userAccountRepository.findByEmail("onur@example.com"))
                .thenReturn(Optional.of(account));

        when(customerService.getCustomerById(7L))
                .thenReturn(new CustomerResponse(
                        7L,
                        "Onur",
                        "Erkoç",
                        "onur@example.com"
                ));

        // WHEN: Gerçek /login endpoint'ine giriş isteği gönderiyoruz.
        MvcResult loginResult = mockMvc.perform(
                        formLogin()
                                .user("onur@example.com")
                                .password(rawPassword)
                )
                .andExpect(status().isFound())
                .andExpect(authenticated()
                        .withUsername("onur@example.com"))
                .andReturn();

        // Giriş isteğinde oluşturulmuş session'ı alıyoruz.
        MockHttpSession session = (MockHttpSession)
                loginResult.getRequest().getSession(false);

        assertNotNull(session);

        // THEN: Şifreyi tekrar göndermeden aynı session ile erişebilmeliyiz.
        mockMvc.perform(
                        get("/api/customers/{id}", 7L)
                                .session(session)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(authenticated()
                        .withUsername("onur@example.com"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email")
                        .value("onur@example.com"));

        verify(customerService).getCustomerById(7L);
    }
    @Test
    void login_whenPasswordIsIncorrect_shouldRejectLogin()
            throws Exception {

        // GIVEN: Hesap mevcut; kayıtlı şifre farklı.
        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        UserAccount account = new UserAccount(
                "onur@example.com",
                passwordEncoder.encode("GuvenliSifre123!"),
                customer
        );

        when(userAccountRepository.findByEmail("onur@example.com"))
                .thenReturn(Optional.of(account));

        // WHEN: Yanlış şifreyle gerçek login isteği gönderiyoruz.
        mockMvc.perform(
                        formLogin()
                                .user("onur@example.com")
                                .password("YanlisSifre123!")
                )

                // THEN: Giriş başarısız olmalı.
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());

        verify(userAccountRepository).findByEmail("onur@example.com");

        // Başarısız giriş müşteri servisini çalıştırmamalı.
        verifyNoInteractions(customerService);
    }
    @Test
    void logout_whenUserIsLoggedIn_shouldInvalidateSession()
            throws Exception {

        // GIVEN: Gerçek şifre doğrulamasıyla giriş yapabilecek hesap.
        String rawPassword = "GuvenliSifre123!";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        UserAccount account = new UserAccount(
                "onur@example.com",
                passwordEncoder.encode(rawPassword),
                customer
        );

        when(userAccountRepository.findByEmail("onur@example.com"))
                .thenReturn(Optional.of(account));

        MvcResult loginResult = mockMvc.perform(
                        formLogin()
                                .user("onur@example.com")
                                .password(rawPassword)
                )
                .andExpect(authenticated())
                .andReturn();

        MockHttpSession session = (MockHttpSession)
                loginResult.getRequest().getSession(false);

        assertNotNull(session);

        // WHEN: Girişte oluşan session ile çıkış isteği gönderiyoruz.
        mockMvc.perform(
                        post("/logout")
                                .session(session)
                                .with(csrf())
                                .accept(MediaType.TEXT_HTML)
                )
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());

        // Oturumsuz yapılan yeni korunan istek reddedilmeli.
        mockMvc.perform(
                        get("/api/customers/{id}", 7L)
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());

        verifyNoInteractions(customerService);
    }
}