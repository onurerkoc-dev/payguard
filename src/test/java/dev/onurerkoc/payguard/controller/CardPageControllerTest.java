package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.VirtualCardLimitUpdateRequest;
import dev.onurerkoc.payguard.dto.VirtualCardResponse;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.exception.GlobalExceptionHandler;
import dev.onurerkoc.payguard.exception.InvalidCardLimitException;
import dev.onurerkoc.payguard.exception.VirtualCardNotFoundException;
import dev.onurerkoc.payguard.security.PayGuardUserDetails;
import dev.onurerkoc.payguard.service.VirtualCardService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(CardPageController.class)
@Import(GlobalExceptionHandler.class)
class CardPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Gerçek veritabanını kullanmadan servis cevabını belirleyebiliriz.
    @MockitoBean
    private VirtualCardService virtualCardService;

    @Test
    void showCardDetail_whenCardNotFound_shouldShowNotFoundPage()
            throws Exception {

        // Giriş yapan hesabın müşteri bilgilerini hazırlıyoruz.
        Customer customer = new Customer(
                "Onur", "Erkoç", "onur@example.com"
        );

        // Veritabanına kaydetmediğimiz için test ID'sini elle veriyoruz.
        ReflectionTestUtils.setField(customer, "id", 7L);

        UserAccount account = new UserAccount(
                "onur@example.com", "test-password-hash", customer
        );

        PayGuardUserDetails principal = new PayGuardUserDetails(account);

        // Bu müşteri için kart bulunamadığında servis hata verecek.
        when(virtualCardService.getCardById(7L, 99999999L))
                .thenThrow(new VirtualCardNotFoundException(
                        "Sanal kart bulunamadı: 99999999"
                ));

        mockMvc.perform(
                        get("/cards/99999999")
                                // Controller'a giriş yapan kullanıcıyı iletir.
                                .with(user(principal))
                                .accept(MediaType.TEXT_HTML)
                )
                .andExpect(status().isNotFound())
                .andExpect(view().name("card-not-found"));
    }

    @Test
    void showCardLimits_shouldPopulateFormWithSavedLimits() throws Exception {
        when(virtualCardService.getCardById(7L, 10L)).thenReturn(savedCard());

        mockMvc.perform(get("/cards/10/limits").with(user(cardOwner())))
                .andExpect(status().isOk())
                .andExpect(view().name("card-limits"))
                .andExpect(model().attribute("cardId", 10L))
                .andExpect(model().attribute("limitRequest", hasProperty(
                        "singleTransactionLimit", is(new BigDecimal("5000.00")))))
                .andExpect(model().attribute("limitRequest", hasProperty(
                        "dailyLimit", is(new BigDecimal("10000.00")))))
                // Thymeleaf formu gerçekten render eder ve CSRF alanını ekler.
                .andExpect(content().string(containsString("action=\"/cards/10/limits\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void updateCardLimits_whenValid_shouldSaveAndRedirect() throws Exception {
        when(virtualCardService.getCardById(7L, 10L)).thenReturn(savedCard());

        mockMvc.perform(post("/cards/10/limits")
                        .with(user(cardOwner())).with(csrf())
                        .param("singleTransactionLimit", "2000.00")
                        .param("dailyLimit", "3000.00")
                        // Müşteri ID'si formdan değil, oturumdan alınmalı.
                        .param("customerId", "999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cards/10"));

        ArgumentCaptor<VirtualCardLimitUpdateRequest> captor =
                ArgumentCaptor.forClass(VirtualCardLimitUpdateRequest.class);
        verify(virtualCardService).updateLimits(eq(7L), eq(10L), captor.capture());
        assertEquals(new BigDecimal("2000.00"), captor.getValue().getSingleTransactionLimit());
        assertEquals(new BigDecimal("3000.00"), captor.getValue().getDailyLimit());
    }

    @ParameterizedTest
    @CsvSource({
            "'', 3000.00, singleTransactionLimit",
            "0, 3000.00, singleTransactionLimit",
            "-1, 3000.00, singleTransactionLimit",
            "2000.001, 3000.00, singleTransactionLimit",
            "abc, 3000.00, singleTransactionLimit",
            "100000000000000000, 3000.00, singleTransactionLimit",
            "2000.00, '', dailyLimit",
            "2000.00, 0, dailyLimit",
            "2000.00, -1, dailyLimit",
            "2000.00, 3000.001, dailyLimit",
            "2000.00, abc, dailyLimit",
            "2000.00, 100000000000000000, dailyLimit"
    })
    void updateCardLimits_whenFieldInvalid_shouldShowErrorWithoutSaving(
            String singleLimit, String dailyLimit, String invalidField) throws Exception {
        when(virtualCardService.getCardById(7L, 10L)).thenReturn(savedCard());

        mockMvc.perform(post("/cards/10/limits")
                        .with(user(cardOwner())).with(csrf())
                        .param("singleTransactionLimit", singleLimit)
                        .param("dailyLimit", dailyLimit))
                .andExpect(status().isOk())
                .andExpect(view().name("card-limits"))
                .andExpect(model().attribute("cardId", 10L))
                .andExpect(model().attributeHasFieldErrors("limitRequest", invalidField))
                .andExpect(content().string(containsString("text-danger small")));

        verify(virtualCardService, never()).updateLimits(any(), any(), any());
    }

    @Test
    void updateCardLimits_whenDailyLimitLower_shouldKeepInputAndShowError() throws Exception {
        when(virtualCardService.getCardById(7L, 10L)).thenReturn(savedCard());
        String message = "Günlük limit, tek işlem limitinden küçük olamaz";
        when(virtualCardService.updateLimits(eq(7L), eq(10L), any()))
                .thenThrow(new InvalidCardLimitException(message));

        mockMvc.perform(post("/cards/10/limits")
                        .with(user(cardOwner())).with(csrf())
                        .param("singleTransactionLimit", "2000.00")
                        .param("dailyLimit", "1000.00"))
                .andExpect(status().isOk())
                .andExpect(view().name("card-limits"))
                .andExpect(model().attribute("limitError", message))
                .andExpect(model().attribute("limitRequest", hasProperty(
                        "dailyLimit", is(new BigDecimal("1000.00")))))
                .andExpect(content().string(containsString(message)));
    }

    @Test
    void showCardLimits_whenCardNotOwned_shouldShowNotFoundPage() throws Exception {
        when(virtualCardService.getCardById(7L, 10L))
                .thenThrow(new VirtualCardNotFoundException("Sanal kart bulunamadı: 10"));

        mockMvc.perform(get("/cards/10/limits").with(user(cardOwner())))
                .andExpect(status().isNotFound())
                .andExpect(view().name("card-not-found"));
    }

    @ParameterizedTest
    @CsvSource({"2000.00, 3000.00", "-1, 3000.00"})
    void updateCardLimits_whenCardNotOwned_shouldRejectEvenInvalidForm(
            String singleLimit, String dailyLimit) throws Exception {
        when(virtualCardService.getCardById(7L, 10L))
                .thenThrow(new VirtualCardNotFoundException("Sanal kart bulunamadı: 10"));

        mockMvc.perform(post("/cards/10/limits")
                        .with(user(cardOwner())).with(csrf())
                        .param("singleTransactionLimit", singleLimit)
                        .param("dailyLimit", dailyLimit))
                .andExpect(status().isNotFound())
                .andExpect(view().name("card-not-found"));

        verify(virtualCardService, never()).updateLimits(any(), any(), any());
    }

    @Test
    void updateCardLimits_withoutCsrf_shouldRejectRequest() throws Exception {
        mockMvc.perform(post("/cards/10/limits")
                        .with(user(cardOwner()))
                        .param("singleTransactionLimit", "2000.00")
                        .param("dailyLimit", "3000.00"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(virtualCardService);
    }

    @Test
    void showCardLimits_withoutLogin_shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/cards/10/limits").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verifyNoInteractions(virtualCardService);
    }

    private PayGuardUserDetails cardOwner() {
        Customer customer = new Customer("Onur", "Erkoç", "onur@example.com");
        ReflectionTestUtils.setField(customer, "id", 7L);
        return new PayGuardUserDetails(new UserAccount(
                "onur@example.com", "test-password-hash", customer));
    }

    private VirtualCardResponse savedCard() {
        return new VirtualCardResponse(
                10L, "Alışveriş Kartım", "9999123456789012", 9, 2030,
                BigDecimal.ZERO, new BigDecimal("5000.00"), new BigDecimal("10000.00"),
                false, true, false, 7L);
    }
}
