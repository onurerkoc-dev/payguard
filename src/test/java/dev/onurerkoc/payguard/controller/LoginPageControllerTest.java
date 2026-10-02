package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.config.SecurityConfig;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import dev.onurerkoc.payguard.security.PayGuardUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginPageController.class)
@Import({SecurityConfig.class, PayGuardUserDetailsService.class})
class LoginPageControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @Test
    void loginPage_whenAnonymous_shouldRenderFormAndRegistrationLink() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Giriş yap")))
                .andExpect(content().string(containsString("action=\"/login\"")))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("href=\"/register\"")))
                .andExpect(content().string(not(containsString("E-posta veya şifre hatalı."))))
                .andExpect(content().string(not(containsString("Başarıyla çıkış yaptınız."))))
                .andExpect(unauthenticated());

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void errorPage_shouldShowGenericMessageWithoutAuthenticationDetails() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WebAttributes.AUTHENTICATION_EXCEPTION,
                new BadCredentialsException("Private account or database detail"));

        mockMvc.perform(get("/login").param("error", "").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("E-posta veya şifre hatalı.")))
                .andExpect(content().string(not(containsString("Private account or database detail"))));
    }

    @Test
    void logoutPage_shouldShowSuccessfulLogoutMessage() throws Exception {
        mockMvc.perform(get("/login?logout"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Başarıyla çıkış yaptınız.")))
                .andExpect(content().string(not(containsString("E-posta veya şifre hatalı."))));
    }

    @Test
    void login_usingTokenFromPage_shouldAuthenticateWithRealPasswordVerification() throws Exception {
        savedAccount();
        MvcResult page = mockMvc.perform(get("/login"))
                .andExpect(status().isOk()).andReturn();
        CsrfToken token = (CsrfToken) page.getRequest().getAttribute("_csrf");
        assertNotNull(token);
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession(false);
        assertNotNull(session);

        // Aynı sayfanın CSRF token'ı ve oturumu ile gerçek POST /login isteği.
        mockMvc.perform(post("/login").session(session)
                        .param(token.getParameterName(), token.getToken())
                        .param("username", "customer@example.com")
                        .param("password", "TestOnlyPassword123!"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("customer@example.com").withRoles("USER"));

        verify(userAccountRepository).findByEmail("customer@example.com");
    }

    @Test
    void login_whenPasswordWrong_shouldRejectAndRenderErrorWithoutPassword() throws Exception {
        savedAccount();
        MvcResult result = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "customer@example.com")
                        .param("password", "WrongPassword123!"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated()).andReturn();

        mockMvc.perform(get("/login").param("error", "")
                        .session((MockHttpSession) result.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("E-posta veya şifre hatalı.")))
                .andExpect(content().string(not(containsString("WrongPassword123!"))))
                .andExpect(content().string(not(containsString("customer@example.com"))));
    }

    @Test
    void login_withoutCsrf_shouldRejectBeforeReadingAccount() throws Exception {
        mockMvc.perform(post("/login").param("username", "customer@example.com")
                        .param("password", "TestOnlyPassword123!"))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void login_withInvalidCsrf_shouldRejectBeforeReadingAccount() throws Exception {
        mockMvc.perform(post("/login").with(csrf().useInvalidToken())
                        .param("username", "customer@example.com")
                        .param("password", "TestOnlyPassword123!"))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void protectedPage_whenAnonymous_shouldRedirectToAccessibleLoginPage() throws Exception {
        mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());
    }

    private void savedAccount() {
        Customer customer = new Customer("Onur", "Erkoç", "customer@example.com");
        UserAccount account = new UserAccount("customer@example.com",
                passwordEncoder.encode("TestOnlyPassword123!"), customer);
        when(userAccountRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(account));
    }
}
