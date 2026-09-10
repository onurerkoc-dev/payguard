package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.VirtualCardSummaryResponse;
import dev.onurerkoc.payguard.service.VirtualCardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import dev.onurerkoc.payguard.exception.CustomerNotFoundException;
import dev.onurerkoc.payguard.dto.VirtualCardResponse;
import dev.onurerkoc.payguard.exception.VirtualCardNotFoundException;

@WebMvcTest(VirtualCardController.class)
class VirtualCardControllerQueryTest {

    // Gerçek web sunucusu açmadan HTTP isteği gönderir.
    @Autowired
    private MockMvc mockMvc;

    // Controller'ın kullandığı gerçek Service yerine sahte Service verir.
    @MockitoBean
    private VirtualCardService virtualCardService;

    @Test
    void getCards_whenCustomerHasCards_shouldReturnCardList()
            throws Exception {

        // GIVEN: Service'in döndüreceği iki kartlık liste.
        List<VirtualCardSummaryResponse> cards = List.of(
                new VirtualCardSummaryResponse(
                        1L,
                        "Alışveriş Kartım",
                        "**** **** **** 9574",
                        new BigDecimal("1150.00"),
                        false
                ),
                new VirtualCardSummaryResponse(
                        2L,
                        "İnternet Kartım",
                        "**** **** **** 1234",
                        new BigDecimal("500.00"),
                        true
                )
        );

        when(virtualCardService.getCardsByCustomerId(1L))
                .thenReturn(cards);

        // WHEN: Müşterinin kartlarını listeleyen endpoint'e istek atıyoruz.
        // THEN: HTTP durumunu ve JSON listesini doğruluyoruz.
        mockMvc.perform(
                        get("/api/customers/1/cards")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].cardName")
                        .value("Alışveriş Kartım"))
                .andExpect(jsonPath("$[0].maskedCardNumber")
                        .value("**** **** **** 9574"))
                .andExpect(jsonPath("$[0].balance").value(1150.00))
                .andExpect(jsonPath("$[0].frozen").value(false))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].cardName")
                        .value("İnternet Kartım"))
                .andExpect(jsonPath("$[1].maskedCardNumber")
                        .value("**** **** **** 1234"))
                .andExpect(jsonPath("$[1].frozen").value(true));

        // URL'deki customerId değerinin Service'e doğru aktarıldığını kanıtlar.
        verify(virtualCardService)
                .getCardsByCustomerId(1L);
    }
    @Test
    void getCards_whenCustomerHasNoCards_shouldReturnEmptyList()
            throws Exception {

        // GIVEN: Müşteri var fakat Service boş kart listesi döndürüyor.
        when(virtualCardService.getCardsByCustomerId(1L))
                .thenReturn(List.of());

        // WHEN: Müşterinin kartları isteniyor.
        // THEN: 200 OK ve boş JSON dizisi dönmelidir.
        mockMvc.perform(
                        get("/api/customers/1/cards")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.length()").value(0))
                .andExpect(content().json("[]"));

        // URL'deki customerId Service'e aktarılmış olmalıdır.
        verify(virtualCardService)
                .getCardsByCustomerId(1L);
    }
    @Test
    void getCards_whenCustomerDoesNotExist_shouldReturnNotFound()
            throws Exception {

        // GIVEN: 99 numaralı müşteri sistemde bulunmuyor.
        when(virtualCardService.getCardsByCustomerId(99L))
                .thenThrow(
                        new CustomerNotFoundException(
                                "Müşteri bulunamadı: 99"
                        )
                );

        // WHEN: Olmayan müşterinin kartları isteniyor.
        // THEN: GlobalExceptionHandler 404 ve hata mesajı döndürmeli.
        mockMvc.perform(
                        get("/api/customers/99/cards")
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message")
                        .value("Müşteri bulunamadı: 99"));

        // URL'deki 99 değerinin Service'e doğru aktarıldığını doğrular.
        verify(virtualCardService)
                .getCardsByCustomerId(99L);
    }
    @Test
    void getCardById_whenCardExists_shouldReturnCardDetails()
            throws Exception {

        // GIVEN: Service'in döndüreceği kart detayını hazırlıyoruz.
        VirtualCardResponse response = new VirtualCardResponse(
                5L,
                "Alışveriş Kartım",
                "9999989443489574",
                9,
                2030,
                new BigDecimal("1150.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("15000.00"),
                false,
                true,
                false,
                1L
        );

        when(virtualCardService.getCardById(1L, 5L))
                .thenReturn(response);

        // WHEN: 1 numaralı müşterinin 5 numaralı kartı isteniyor.
        // THEN: Kartın detayları JSON olarak dönmelidir.
        mockMvc.perform(
                        get("/api/customers/1/cards/5")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.cardName")
                        .value("Alışveriş Kartım"))
                .andExpect(jsonPath("$.cardNumber")
                        .value("9999989443489574"))
                .andExpect(jsonPath("$.expiryMonth").value(9))
                .andExpect(jsonPath("$.expiryYear").value(2030))
                .andExpect(jsonPath("$.balance").value(1150.00))
                .andExpect(jsonPath("$.singleTransactionLimit")
                        .value(5000.00))
                .andExpect(jsonPath("$.dailyLimit")
                        .value(15000.00))
                .andExpect(jsonPath("$.frozen").value(false))
                .andExpect(jsonPath("$.onlineTransactionsEnabled")
                        .value(true))
                .andExpect(jsonPath("$.internationalTransactionsEnabled")
                        .value(false))
                .andExpect(jsonPath("$.customerId").value(1));

        // Her iki PathVariable değerinin de doğru aktarıldığını doğrular.
        verify(virtualCardService)
                .getCardById(1L, 5L);
    }
    @Test
    void getCardById_whenCardDoesNotExist_shouldReturnNotFound()
            throws Exception {

        // GIVEN: 1 numaralı müşteriye ait 99 numaralı kart bulunmuyor.
        when(virtualCardService.getCardById(1L, 99L))
                .thenThrow(
                        new VirtualCardNotFoundException(
                                "Sanal kart bulunamadı: 99"
                        )
                );

        // WHEN: Bulunmayan veya müşteriye ait olmayan kart isteniyor.
        // THEN: GlobalExceptionHandler 404 cevabı döndürmelidir.
        mockMvc.perform(
                        get("/api/customers/1/cards/99")
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message")
                        .value("Sanal kart bulunamadı: 99"));

        // customerId ve cardId değerlerinin Service'e doğru gittiğini doğrular.
        verify(virtualCardService)
                .getCardById(1L, 99L);
    }
    @Test
    void getCardById_whenCustomerDoesNotExist_shouldReturnNotFound()
            throws Exception {

        // GIVEN: 99 numaralı müşteri sistemde bulunmuyor.
        when(virtualCardService.getCardById(99L, 5L))
                .thenThrow(
                        new CustomerNotFoundException(
                                "Müşteri bulunamadı: 99"
                        )
                );

        // WHEN: Olmayan müşteriye ait bir kart detayı isteniyor.
        // THEN: GlobalExceptionHandler 404 ve müşteri hata mesajı dönmeli.
        mockMvc.perform(
                        get("/api/customers/99/cards/5")
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.message")
                        .value("Müşteri bulunamadı: 99"));

        verify(virtualCardService)
                .getCardById(99L, 5L);
    }
}