package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.VirtualCardBalanceLoadRequest;
import dev.onurerkoc.payguard.dto.VirtualCardResponse;
import dev.onurerkoc.payguard.service.VirtualCardService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import dev.onurerkoc.payguard.exception.VirtualCardNotFoundException;
import dev.onurerkoc.payguard.dto.VirtualCardLimitUpdateRequest;
import dev.onurerkoc.payguard.exception.InvalidCardLimitException;
import dev.onurerkoc.payguard.dto.VirtualCardPaymentSettingsRequest;

@WebMvcTest(VirtualCardController.class)
class VirtualCardControllerManagementTest {

    // Gerçek sunucu açmadan Controller'a HTTP isteği gönderir.
    @Autowired
    private MockMvc mockMvc;

    // Controller'ın bağımlı olduğu Service'i sahte nesneyle değiştirir.
    @MockitoBean
    private VirtualCardService virtualCardService;

    @Test
    void loadBalance_whenRequestIsValid_shouldReturnUpdatedCard()
            throws Exception {

        // GIVEN: Bakiye yüklendikten sonra Service'in döndüreceği kart.
        VirtualCardResponse response = new VirtualCardResponse(
                5L,
                "Alışveriş Kartım",
                "9999989443489574",
                9,
                2030,
                new BigDecimal("1400.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("15000.00"),
                false,
                true,
                false,
                1L
        );

        when(virtualCardService.loadBalance(
                eq(1L),
                eq(5L),
                any(VirtualCardBalanceLoadRequest.class)
        )).thenReturn(response);

        String requestBody = """
                {
                    "amount": 250.00
                }
                """;

        // WHEN: 1 numaralı müşterinin 5 numaralı kartına
        // 250 TL yükleme isteği gönderiyoruz.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.balance").value(1400.00))
                .andExpect(jsonPath("$.customerId").value(1));

        // JSON'daki amount değerinin DTO'ya doğru çevrildiğini
        // kontrol etmek için Service'e giden request'i yakalıyoruz.
        ArgumentCaptor<VirtualCardBalanceLoadRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        VirtualCardBalanceLoadRequest.class
                );

        verify(virtualCardService)
                .loadBalance(
                        eq(1L),
                        eq(5L),
                        requestCaptor.capture()
                );

        VirtualCardBalanceLoadRequest capturedRequest =
                requestCaptor.getValue();

        assertEquals(
                0,
                capturedRequest.getAmount()
                        .compareTo(new BigDecimal("250.00"))
        );
    }
    @Test  //Buradaki önemli güvenlik kuralı şu: geçersiz miktar Service’e ulaşmadığı için bakiye değişmez ve BALANCE_LOAD işlem kaydı da oluşmaz.
    void loadBalance_whenAmountIsNotPositive_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Yükleme miktarı sıfırdan büyük değil.
        String requestBody = """
            {
                "amount": 0
            }
            """;

        // WHEN: Geçersiz bakiye yükleme isteği gönderiyoruz.
        // THEN: DTO validation 400 Bad Request döndürmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount")
                        .value(
                                "Yüklenecek bakiye sıfırdan büyük olmalıdır"
                        ));

        // Validation Controller metodunu çalıştırmadan isteği durdurur.
        // Bu yüzden Service kesinlikle çağrılmamalıdır.
        verify(virtualCardService, never())
                .loadBalance(
                        eq(1L),
                        eq(5L),
                        any(VirtualCardBalanceLoadRequest.class)
                );
    }
    @Test
    void loadBalance_whenAmountIsMissing_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: amount alanı JSON içerisinde hiç bulunmuyor.
        String requestBody = """
            {
            }
            """;

        // WHEN: Eksik bakiye yükleme isteği gönderiyoruz.
        // THEN: @NotNull validation mesajı dönmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount")
                        .value("Yüklenecek bakiye zorunludur"));

        // Validation başarısız olduğu için Service'e ulaşılmamalıdır.
        verify(virtualCardService, never())
                .loadBalance(
                        eq(1L),
                        eq(5L),
                        any(VirtualCardBalanceLoadRequest.class)
                );
    }
    @Test
    void loadBalance_whenAmountHasMoreThanTwoDecimalPlaces_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Tutar ikiden fazla ondalık basamak içeriyor.
        String requestBody = """
            {
                "amount": 250.555
            }
            """;

        // WHEN: Para formatına uymayan bir miktar gönderiyoruz.
        // THEN: @Digits validation mesajı dönmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount")
                        .value(
                                "Yüklenecek bakiye en fazla 2 ondalık basamak içerebilir"
                        ));

        // DTO validation başarısız olduğu için bakiye yükleme
        // Service metoduna ulaşmamalıdır.
        verify(virtualCardService, never())
                .loadBalance(
                        eq(1L),
                        eq(5L),
                        any(VirtualCardBalanceLoadRequest.class)
                );
    }
    @Test
    void freezeCard_whenCardExists_shouldReturnFrozenCard()
            throws Exception {

        // GIVEN: Service, dondurulmuş kartın güncel hâlini döndürüyor.
        VirtualCardResponse response = new VirtualCardResponse(
                5L,
                "Alışveriş Kartım",
                "9999989443489574",
                9,
                2030,
                new BigDecimal("1400.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("15000.00"),
                true,
                true,
                false,
                1L
        );

        when(virtualCardService.freezeCard(1L, 5L))
                .thenReturn(response);

        // WHEN: Kartı dondurma isteği gönderiyoruz.
        // THEN: Güncel kartın frozen alanı true dönmelidir.
        mockMvc.perform(
                        patch("/api/customers/1/cards/5/freeze")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.frozen").value(true))
                .andExpect(jsonPath("$.customerId").value(1));

        // URL'deki iki ID'nin Service'e doğru aktarıldığını doğrular.
        verify(virtualCardService)
                .freezeCard(1L, 5L);
    }
    @Test
    void unfreezeCard_whenCardExists_shouldReturnActiveCard()
            throws Exception {

        // GIVEN: Service, tekrar kullanıma açılmış kartı döndürüyor.
        VirtualCardResponse response = new VirtualCardResponse(
                5L,
                "Alışveriş Kartım",
                "9999989443489574",
                9,
                2030,
                new BigDecimal("1400.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("15000.00"),
                false,
                true,
                false,
                1L
        );

        when(virtualCardService.unfreezeCard(1L, 5L))
                .thenReturn(response);

        // WHEN: Kartı tekrar açma isteği gönderiyoruz.
        // THEN: frozen alanı false dönmelidir.
        mockMvc.perform(
                        patch("/api/customers/1/cards/5/unfreeze")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.frozen").value(false))
                .andExpect(jsonPath("$.customerId").value(1));

        verify(virtualCardService)
                .unfreezeCard(1L, 5L);
    }
    @Test
    void freezeCard_whenCardDoesNotExist_shouldReturnNotFound()
            throws Exception {

        // GIVEN: 1 numaralı müşteriye ait 99 numaralı kart bulunmuyor.
        when(virtualCardService.freezeCard(1L, 99L))
                .thenThrow(
                        new VirtualCardNotFoundException(
                                "Sanal kart bulunamadı: 99"
                        )
                );

        // WHEN: Bulunmayan kartı dondurmaya çalışıyoruz.
        // THEN: GlobalExceptionHandler 404 cevabı üretmelidir.
        mockMvc.perform(
                        patch("/api/customers/1/cards/99/freeze")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Sanal kart bulunamadı: 99"));

        verify(virtualCardService)
                .freezeCard(1L, 99L);
    }
    @Test
    void updateLimits_whenRequestIsValid_shouldReturnUpdatedCard()
            throws Exception {

        // GIVEN: Service'in limitleri güncelledikten sonra döndüreceği kart.
        VirtualCardResponse response = new VirtualCardResponse(
                5L,
                "Alışveriş Kartım",
                "9999989443489574",
                9,
                2030,
                new BigDecimal("1400.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("10000.00"),
                false,
                true,
                false,
                1L
        );

        when(virtualCardService.updateLimits(
                eq(1L),
                eq(5L),
                any(VirtualCardLimitUpdateRequest.class)
        )).thenReturn(response);

        String requestBody = """
            {
                "singleTransactionLimit": 3000.00,
                "dailyLimit": 10000.00
            }
            """;

        // WHEN: Kart limitlerini güncelleme isteği gönderiyoruz.
        // THEN: Güncellenmiş limitlerin JSON cevabında dönmesini bekliyoruz.
        mockMvc.perform(
                        patch("/api/customers/1/cards/5/limits")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.singleTransactionLimit")
                        .value(3000.00))
                .andExpect(jsonPath("$.dailyLimit")
                        .value(10000.00))
                .andExpect(jsonPath("$.customerId").value(1));

        // JSON'un DTO'ya doğru aktarıldığını kontrol ediyoruz.
        ArgumentCaptor<VirtualCardLimitUpdateRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        VirtualCardLimitUpdateRequest.class
                );

        verify(virtualCardService)
                .updateLimits(
                        eq(1L),
                        eq(5L),
                        requestCaptor.capture()
                );

        VirtualCardLimitUpdateRequest capturedRequest =
                requestCaptor.getValue();

        assertEquals(
                0,
                capturedRequest.getSingleTransactionLimit()
                        .compareTo(new BigDecimal("3000.00"))
        );

        assertEquals(
                0,
                capturedRequest.getDailyLimit()
                        .compareTo(new BigDecimal("10000.00"))
        );
    }
    @Test
    void updateLimits_whenLimitsAreNotPositive_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Her iki limit de sıfırdan büyük olma kuralını ihlal ediyor.
        String requestBody = """
            {
                "singleTransactionLimit": 0,
                "dailyLimit": -100
            }
            """;

        // WHEN: Geçersiz limitlerle PATCH isteği gönderiyoruz.
        // THEN: DTO validation hataları dönmelidir.
        mockMvc.perform(
                        patch("/api/customers/1/cards/5/limits")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.singleTransactionLimit")
                        .value(
                                "Tek işlem limiti sıfırdan büyük olmalıdır"
                        ))
                .andExpect(jsonPath("$.dailyLimit")
                        .value(
                                "Günlük limit sıfırdan büyük olmalıdır"
                        ));

        // Validation başarısız olduğu için Service çağrılmamalıdır.
        verify(virtualCardService, never())
                .updateLimits(
                        eq(1L),
                        eq(5L),
                        any(VirtualCardLimitUpdateRequest.class)
                );
    }
    @Test
    void updateLimits_whenDailyLimitIsLowerThanSingleLimit_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Limitler pozitif ancak günlük limit,
        // tek işlem limitinden daha küçük.
        String requestBody = """
            {
                "singleTransactionLimit": 5000.00,
                "dailyLimit": 3000.00
            }
            """;

        // DTO validation geçer.
        // Service içerisindeki iş kuralı exception fırlatır.
        when(virtualCardService.updateLimits(
                eq(1L),
                eq(5L),
                any(VirtualCardLimitUpdateRequest.class)
        )).thenThrow(
                new InvalidCardLimitException(
                        "Günlük limit, tek işlem limitinden küçük olamaz"
                )
        );

        // WHEN: Birbiriyle uyumsuz limitler gönderiliyor.
        // THEN: GlobalExceptionHandler 400 cevabı üretmelidir.
        mockMvc.perform(
                        patch("/api/customers/1/cards/5/limits")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(
                                "Günlük limit, tek işlem limitinden küçük olamaz"
                        ));

        // DTO validation geçtiği için Service çağrılmalıdır.
        verify(virtualCardService)
                .updateLimits(
                        eq(1L),
                        eq(5L),
                        any(VirtualCardLimitUpdateRequest.class)
                );
    }
    @Test
    void updatePaymentSettings_whenRequestIsValid_shouldReturnUpdatedCard()
            throws Exception {

        // GIVEN: İnternet ödemeleri kapatılmış,
        // yurt dışı ödemeleri açılmış güncel kart cevabı.
        VirtualCardResponse response = new VirtualCardResponse(
                5L,
                "Alışveriş Kartım",
                "9999989443489574",
                9,
                2030,
                new BigDecimal("1400.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("10000.00"),
                false,
                false,
                true,
                1L
        );

        when(virtualCardService.updatePaymentSettings(
                eq(1L),
                eq(5L),
                any(VirtualCardPaymentSettingsRequest.class)
        )).thenReturn(response);

        String requestBody = """
            {
                "onlineTransactionsEnabled": false,
                "internationalTransactionsEnabled": true
            }
            """;

        // WHEN: Kartın ödeme ayarlarını değiştiriyoruz.
        // THEN: Güncel ayarlar response içerisinde dönmelidir.
        mockMvc.perform(
                        patch(
                                "/api/customers/1/cards/5/payment-settings"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.onlineTransactionsEnabled")
                        .value(false))
                .andExpect(jsonPath("$.internationalTransactionsEnabled")
                        .value(true))
                .andExpect(jsonPath("$.customerId").value(1));

        // JSON'un request DTO'suna doğru dönüştürüldüğünü kontrol ediyoruz.
        ArgumentCaptor<VirtualCardPaymentSettingsRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        VirtualCardPaymentSettingsRequest.class
                );

        verify(virtualCardService)
                .updatePaymentSettings(
                        eq(1L),
                        eq(5L),
                        requestCaptor.capture()
                );

        VirtualCardPaymentSettingsRequest capturedRequest =
                requestCaptor.getValue();

        assertEquals(
                Boolean.FALSE,
                capturedRequest.getOnlineTransactionsEnabled()
        );

        assertEquals(
                Boolean.TRUE,
                capturedRequest.getInternationalTransactionsEnabled()
        );
    }
    @Test
    void updatePaymentSettings_whenSettingsAreMissing_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: İki ödeme ayarı da JSON içerisinde bulunmuyor.
        String requestBody = """
            {
            }
            """;

        // WHEN: Eksik ödeme ayarlarıyla PATCH isteği gönderiyoruz.
        // THEN: Her iki @NotNull validation mesajı da dönmelidir.
        mockMvc.perform(
                        patch(
                                "/api/customers/1/cards/5/payment-settings"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.onlineTransactionsEnabled")
                        .value(
                                "İnternet alışverişi ayarı zorunludur"
                        ))
                .andExpect(jsonPath("$.internationalTransactionsEnabled")
                        .value(
                                "Yurt dışı alışverişi ayarı zorunludur"
                        ));

        // DTO validation başarısız olduğu için Service çağrılmamalıdır.
        verify(virtualCardService, never())
                .updatePaymentSettings(
                        eq(1L),
                        eq(5L),
                        any(VirtualCardPaymentSettingsRequest.class)
                );
    }
}