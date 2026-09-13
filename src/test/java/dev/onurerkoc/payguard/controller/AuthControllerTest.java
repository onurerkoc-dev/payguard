package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.config.SecurityConfig;
import dev.onurerkoc.payguard.dto.UserRegistrationRequest;
import dev.onurerkoc.payguard.dto.UserRegistrationResponse;
import dev.onurerkoc.payguard.service.UserRegistrationService;
import dev.onurerkoc.payguard.exception.EmailAlreadyExistsException;
import dev.onurerkoc.payguard.exception.InvalidPasswordException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import dev.onurerkoc.payguard.security.PayGuardUserDetailsService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@WithAnonymousUser
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRegistrationService userRegistrationService;

    @MockitoBean
    private PayGuardUserDetailsService userDetailsService;
    @Test
    void register_whenAnonymousRequestIsValid_shouldReturnCreated()
            throws Exception {

        // GIVEN: Kayıt servisi başarılı bir cevap döndürecek.
        UserRegistrationResponse response =
                new UserRegistrationResponse(
                        3L,
                        7L,
                        "onur@example.com"
                );

        when(userRegistrationService.register(
                any(UserRegistrationRequest.class)
        )).thenReturn(response);

        String requestBody = """
            {
                "firstName": "Onur",
                "lastName": "Erkoç",
                "email": "onur@example.com",
                "password": "GuvenliSifre123!"
            }
            """;

        // WHEN: Giriş yapmadan, geçerli CSRF tokenıyla kayıt istiyoruz.
        mockMvc.perform(
                        post("/api/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )

                // THEN: Kayıt endpoint'i anonim isteğe izin vermeli.
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.accountId").value(3))
                .andExpect(jsonPath("$.customerId").value(7))
                .andExpect(jsonPath("$.email").value("onur@example.com"))

                // Açık şifre ve hash response'a çıkmamalı.
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // JSON alanlarının servise doğru iletildiğini kontrol ediyoruz.
        ArgumentCaptor<UserRegistrationRequest> requestCaptor =
                ArgumentCaptor.forClass(UserRegistrationRequest.class);

        verify(userRegistrationService).register(requestCaptor.capture());

        UserRegistrationRequest capturedRequest = requestCaptor.getValue();

        assertEquals("Onur", capturedRequest.getFirstName());
        assertEquals("Erkoç", capturedRequest.getLastName());
        assertEquals("onur@example.com", capturedRequest.getEmail());
        assertEquals("GuvenliSifre123!", capturedRequest.getPassword());
    }
    @Test
    void register_whenRequestIsInvalid_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: İsimler boş, e-posta geçersiz, şifre çok kısa.
        String requestBody = """
        {
            "firstName": "",
            "lastName": "",
            "email": "gecersiz-email",
            "password": "123"
        }
        """;

        // WHEN: Güvenliği geçebilen fakat validation'dan geçemeyen istek.
        mockMvc.perform(
                        post("/api/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )

                // THEN: Alan hataları 400 cevabıyla dönmeli.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.firstName")
                        .value("Ad alanı zorunludur"))
                .andExpect(jsonPath("$.lastName")
                        .value("Soyadı alanı zorunludur"))
                .andExpect(jsonPath("$.email")
                        .value("Geçerli bir e-posta adresi giriniz"))
                .andExpect(jsonPath("$.password")
                        .value("Şifre 12 ile 72 karakter arasında olmalıdır"));

        // Geçersiz bilgiler kayıt servisine gönderilmemeli.
        verifyNoInteractions(userRegistrationService);
    }
    @Test
    void register_whenEmailAlreadyExists_shouldReturnConflict()
            throws Exception {

        // GIVEN: Servis, e-postanın kullanımda olduğunu bildiriyor.
        when(userRegistrationService.register(
                any(UserRegistrationRequest.class)
        )).thenThrow(
                new EmailAlreadyExistsException(
                        "Bu email adresi zaten kullanılıyor"
                )
        );

        // Veriler geçerli; hata DTO doğrulamasından gelmemeli.
        String requestBody = """
        {
            "firstName": "Onur",
            "lastName": "Erkoç",
            "email": "onur@example.com",
            "password": "GuvenliSifre123!"
        }
        """;

        // WHEN: Anonim kullanıcı geçerli CSRF tokenıyla kayıt istiyor.
        mockMvc.perform(
                        post("/api/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )

                // THEN: Handler exception'ı 409 cevabına dönüştürmeli.
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message")
                        .value("Bu email adresi zaten kullanılıyor"));

        // İstek validation'ı geçtiği için servis çağrılmış olmalı.
        verify(userRegistrationService)
                .register(any(UserRegistrationRequest.class));
    }
    @Test
    void register_whenPasswordExceedsByteLimit_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Servis şifrenin UTF-8 byte sınırını aştığını bildiriyor.
        when(userRegistrationService.register(
                any(UserRegistrationRequest.class)
        )).thenThrow(
                new InvalidPasswordException(
                        "Şifre UTF-8 olarak en fazla 72 byte olabilir"
                )
        );

        // 37 tane ş, UTF-8 olarak 74 byte eder.
        // DTO uzunluk kontrolünü geçer, servisteki byte kontrolünü geçemez.
        String requestBody = """
        {
            "firstName": "Onur",
            "lastName": "Erkoç",
            "email": "onur@example.com",
            "password": "%s"
        }
        """.formatted("ş".repeat(37));

        // WHEN: Geçerli CSRF tokenıyla kayıt isteği gönderiyoruz.
        mockMvc.perform(
                        post("/api/auth/register")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )

                // THEN: Handler şifre hatasını 400 cevabına dönüştürmeli.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Şifre UTF-8 olarak en fazla 72 byte olabilir"));

        // DTO doğrulaması geçtiğinden servis çağrılmış olmalı.
        verify(userRegistrationService)
                .register(any(UserRegistrationRequest.class));
    }
    @Test
    void register_whenCsrfTokenIsMissing_shouldReturnForbidden()
            throws Exception {

        // GIVEN: Kayıt bilgileri geçerli.
        String requestBody = """
        {
            "firstName": "Onur",
            "lastName": "Erkoç",
            "email": "onur@example.com",
            "password": "GuvenliSifre123!"
        }
        """;

        // WHEN: Anonim kayıt isteğinde CSRF tokenını bilerek göndermiyoruz.
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )

                // THEN: CSRF koruması isteği durdurmalı.
                .andExpect(status().isForbidden());

        // İstek controller metoduna ulaşmadan reddedilmeli.
        verifyNoInteractions(userRegistrationService);
    }
}