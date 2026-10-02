package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.config.SecurityConfig;
import dev.onurerkoc.payguard.dto.UserRegistrationRequest;
import dev.onurerkoc.payguard.exception.EmailAlreadyExistsException;
import dev.onurerkoc.payguard.exception.InvalidPasswordException;
import dev.onurerkoc.payguard.security.PayGuardUserDetailsService;
import dev.onurerkoc.payguard.service.UserRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegistrationPageController.class)
@Import(SecurityConfig.class)
class RegistrationPageControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserRegistrationService registrationService;
    @MockitoBean
    private PayGuardUserDetailsService userDetailsService;

    @Test
    void page_whenAnonymous_shouldRenderFormWithCsrfAndEmptyPassword() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("bg-light")))
                .andExpect(content().string(containsString("action=\"/register\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(matchesPattern("(?s).*<input(?=[^>]*type=\"password\")(?=[^>]*value=)[^>]*>.*"))))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(unauthenticated());

        verifyNoInteractions(registrationService);
    }

    @Test
    void register_withoutCsrf_shouldRejectBeforeCallingService() throws Exception {
        mockMvc.perform(validForm())
                .andExpect(status().isForbidden());
        verifyNoInteractions(registrationService);
    }

    @Test
    void register_withInvalidCsrf_shouldRejectBeforeCallingService() throws Exception {
        mockMvc.perform(validForm().with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(registrationService);
    }

    @Test
    void register_whenValid_shouldPassFieldsAndRedirectWithoutAutomaticLogin() throws Exception {
        // Controller daha sonra şifreyi temizlediği için çağrı anındaki değerleri kontrol et.
        doAnswer(invocation -> {
            UserRegistrationRequest request = invocation.getArgument(0);
            org.junit.jupiter.api.Assertions.assertEquals("Onur", request.getFirstName());
            org.junit.jupiter.api.Assertions.assertEquals("Erkoç", request.getLastName());
            org.junit.jupiter.api.Assertions.assertEquals("new@example.com", request.getEmail());
            org.junit.jupiter.api.Assertions.assertEquals("TestPassword123!", request.getPassword());
            return null;
        }).when(registrationService).register(any());

        mockMvc.perform(validForm().with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/register"))
                .andExpect(flash().attribute("registrationSuccess", true))
                .andExpect(flash().attributeCount(1))
                .andExpect(unauthenticated());

        verify(registrationService).register(any());
    }

    @Test
    void register_whenFieldsInvalid_shouldShowErrorsWithoutReflectingPassword() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", " ").param("lastName", "")
                        .param("email", "invalid-email").param("password", "Short123!"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("request", "firstName", "lastName", "email", "password"))
                .andExpect(content().string(containsString("Geçerli bir e-posta adresi giriniz")))
                .andExpect(content().string(containsString("invalid-email")))
                .andExpect(content().string(not(containsString("Short123!"))));

        verifyNoInteractions(registrationService);
    }

    @Test
    void register_whenFieldsTooLong_shouldRejectOnServer() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "a".repeat(51)).param("lastName", "b".repeat(51))
                        .param("email", "a".repeat(140) + "@example.com")
                        .param("password", "p".repeat(73)))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("request", "firstName", "lastName", "email", "password"));

        verifyNoInteractions(registrationService);
    }

    @Test
    void register_whenEmailExists_shouldShowFieldErrorAndKeepOtherFields() throws Exception {
        when(registrationService.register(any())).thenThrow(
                new EmailAlreadyExistsException("Bu email adresi zaten kullanılıyor"));

        mockMvc.perform(validForm().with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("request", "email"))
                .andExpect(content().string(containsString("Bu email adresi zaten kullanılıyor")))
                .andExpect(content().string(containsString("value=\"Onur\"")))
                .andExpect(content().string(containsString("value=\"new@example.com\"")))
                .andExpect(content().string(not(containsString("TestPassword123!"))));
    }

    @Test
    void register_whenPasswordExceedsByteLimit_shouldShowPasswordError() throws Exception {
        when(registrationService.register(any())).thenThrow(
                new InvalidPasswordException("Şifre UTF-8 olarak en fazla 72 byte olabilir"));

        mockMvc.perform(validForm().with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("request", "password"))
                .andExpect(content().string(containsString("Şifre UTF-8 olarak en fazla 72 byte olabilir")))
                .andExpect(content().string(not(containsString("TestPassword123!"))));
    }

    @Test
    void register_whenInputContainsHtml_shouldEscapeItInForm() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("firstName", "<script>alert(1)</script>").param("lastName", "Test")
                        .param("email", "bad-email").param("password", "TestPassword123!"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>"))));

        verifyNoInteractions(registrationService);
    }

    @Test
    void register_whenExtraAccountFieldsSent_shouldSuppressThem() throws Exception {
        mockMvc.perform(validForm().with(csrf())
                        .param("role", "ADMIN").param("customerId", "1")
                        .param("enabled", "false").param("passwordHash", "fake"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/register"));

        verify(registrationService).register(any(UserRegistrationRequest.class));
    }

    @Test
    void successPage_shouldShowLoginLinkWithoutRegistrationForm() throws Exception {
        mockMvc.perform(get("/register").flashAttr("registrationSuccess", true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hesabınız oluşturuldu. Giriş yapabilirsiniz.")))
                .andExpect(content().string(not(containsString("<form"))))
                .andExpect(content().string(containsString("href=\"/login\"")));
    }

    @Test
    void otherPages_whenAnonymous_shouldStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/cards/new")).andExpect(status().isUnauthorized());
        verifyNoInteractions(registrationService);
    }

    private MockHttpServletRequestBuilder validForm() {
        return post("/register").param("firstName", "Onur").param("lastName", "Erkoç")
                .param("email", "new@example.com").param("password", "TestPassword123!");
    }
}
