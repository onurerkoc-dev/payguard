package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.exception.GlobalExceptionHandler;
import dev.onurerkoc.payguard.exception.VirtualCardNotFoundException;
import dev.onurerkoc.payguard.security.PayGuardUserDetails;
import dev.onurerkoc.payguard.service.VirtualCardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}