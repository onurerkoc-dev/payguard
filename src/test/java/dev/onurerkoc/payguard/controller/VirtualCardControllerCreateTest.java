package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.VirtualCardCreateRequest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.never;
import dev.onurerkoc.payguard.exception.CustomerNotFoundException;
import dev.onurerkoc.payguard.exception.InvalidCardLimitException;

@WebMvcTest(VirtualCardController.class)
class VirtualCardControllerCreateTest {

    // Gerçek HTTP sunucusu açmadan Controller'a istek göndermemizi sağlar.
    @Autowired
    private MockMvc mockMvc;

    // Controller gerçek servisi değil, Mockito tarafından oluşturulan
    // sahte VirtualCardService nesnesini kullanır.
    @MockitoBean
    private VirtualCardService virtualCardService;

    @Test
    void createCard_whenRequestIsValid_shouldReturnCreated()
            throws Exception {

        // GIVEN: Service'in döndüreceği örnek kart cevabı.
        VirtualCardResponse response = new VirtualCardResponse(
                1L,
                "Alışveriş Kartım",
                "9999989443489574",
                9,
                2030,
                new BigDecimal("0.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("15000.00"),
                false,
                true,
                false,
                1L
        );

        when(virtualCardService.createCard(
                eq(1L),
                any(VirtualCardCreateRequest.class)
        )).thenReturn(response);

        String requestBody = """
                {
                    "cardName": "Alışveriş Kartım",
                    "singleTransactionLimit": 5000.00,
                    "dailyLimit": 15000.00
                }
                """;

        // WHEN: Kart oluşturma endpoint'ine POST isteği gönderiyoruz.
        // THEN: 201 ve dönen JSON alanlarını doğruluyoruz.
        mockMvc.perform(
                        post("/api/customers/1/cards")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.cardName")
                        .value("Alışveriş Kartım"))
                .andExpect(jsonPath("$.cardNumber")
                        .value("9999989443489574"))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.frozen").value(false))
                .andExpect(jsonPath("$.customerId").value(1));

        // Controller'ın JSON'u DTO'ya doğru çevirdiğini kontrol ediyoruz.
        ArgumentCaptor<VirtualCardCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        VirtualCardCreateRequest.class
                );

        verify(virtualCardService)
                .createCard(eq(1L), requestCaptor.capture());

        VirtualCardCreateRequest capturedRequest =
                requestCaptor.getValue();

        assertEquals(
                "Alışveriş Kartım",
                capturedRequest.getCardName()
        );

        assertEquals(
                0,
                capturedRequest.getSingleTransactionLimit()
                        .compareTo(new BigDecimal("5000.00"))
        );

        assertEquals(
                0,
                capturedRequest.getDailyLimit()
                        .compareTo(new BigDecimal("15000.00"))
        );
    }
    @Test
    void createCard_whenCardNameIsBlank_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Kart adı boş bırakılmış geçersiz bir istek.
        String requestBody = """
            {
                "cardName": " ",
                "singleTransactionLimit": 5000.00,
                "dailyLimit": 15000.00
            }
            """;

        // WHEN: Geçersiz JSON ile kart oluşturma isteği gönderiyoruz.
        // THEN: Validation isteği durdurmalı ve 400 dönmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.cardName")
                        .value("Kart adı zorunludur"));

        // Validation hatası Controller metodundan önce oluşur.
        // Bu nedenle Service kesinlikle çağrılmamalıdır.
        verify(virtualCardService, never())
                .createCard(
                        eq(1L),
                        any(VirtualCardCreateRequest.class)
                );
    }
    /*
    0 ve -100 değerleri gelir
        ↓
@DecimalMin(value = "0.01") kuralları çalışır
        ↓
İstek 400 Bad Request ile durdurulur
        ↓
Service ve veritabanı katmanına geçilmez
     */
    @Test
    void createCard_whenLimitsAreNotPositive_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Her iki limit de sıfırdan büyük olma kuralını ihlal ediyor.
        String requestBody = """
            {
                "cardName": "Alışveriş Kartım",
                "singleTransactionLimit": 0,
                "dailyLimit": -100
            }
            """;

        // WHEN: Geçersiz limitlerle istek gönderiyoruz.
        // THEN: Her iki alanın validation mesajını doğruluyoruz.
        mockMvc.perform(
                        post("/api/customers/1/cards")
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

        // DTO validation başarısız olduğu için Service çalışmamalı.
        verify(virtualCardService, never())
                .createCard(
                        eq(1L),
                        any(VirtualCardCreateRequest.class)
                );
    }
    @Test
    void createCard_whenCustomerDoesNotExist_shouldReturnNotFound()
            throws Exception {

        // GIVEN: JSON geçerli fakat 99 numaralı müşteri bulunmuyor.
        String requestBody = """
            {
                "cardName": "Alışveriş Kartım",
                "singleTransactionLimit": 5000.00,
                "dailyLimit": 15000.00
            }
            """;

        // Gerçek Service çalışmıyor.
        // Mockito'ya bu çağrıda exception fırlatmasını söylüyoruz.
        when(virtualCardService.createCard(
                eq(99L),
                any(VirtualCardCreateRequest.class)
        )).thenThrow(
                new CustomerNotFoundException(
                        "Müşteri bulunamadı: 99"
                )
        );

        // WHEN: Olmayan müşteriye kart oluşturmaya çalışıyoruz.
        // THEN: GlobalExceptionHandler 404 ve hata JSON'u dönmeli.
        mockMvc.perform(
                        post("/api/customers/99/cards")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Müşteri bulunamadı: 99"));

        // Validation başarılı olduğu için Service bir kez çağrılmalıdır.
        verify(virtualCardService)
                .createCard(
                        eq(99L),
                        any(VirtualCardCreateRequest.class)
                );
    }
    @Test
    void createCard_whenDailyLimitIsLowerThanSingleLimit_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Alanlar tek başlarına geçerli fakat birbirleriyle uyumsuz.
        String requestBody = """
            {
                "cardName": "Alışveriş Kartım",
                "singleTransactionLimit": 5000.00,
                "dailyLimit": 3000.00
            }
            """;

        // Service içerisindeki iş kuralı hatasını taklit ediyoruz.
        when(virtualCardService.createCard(
                eq(1L),
                any(VirtualCardCreateRequest.class)
        )).thenThrow(
                new InvalidCardLimitException(
                        "Günlük limit, tek işlem limitinden küçük olamaz"
                )
        );

        // WHEN: Birbiriyle uyumsuz limitlerle istek gönderiyoruz.
        // THEN: GlobalExceptionHandler 400 cevabı üretmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(
                                "Günlük limit, tek işlem limitinden küçük olamaz"
                        ));

        // DTO validation geçtiği için Service gerçekten çağrılmalıdır.
        verify(virtualCardService)
                .createCard(
                        eq(1L),
                        any(VirtualCardCreateRequest.class)
                );
    }
    @Test
    void createCard_whenLimitsHaveMoreThanTwoDecimalPlaces_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Limitlerde ikiden fazla ondalık basamak bulunuyor.
        String requestBody = """
            {
                "cardName": "Alışveriş Kartım",
                "singleTransactionLimit": 5000.123,
                "dailyLimit": 15000.456
            }
            """;

        // WHEN: Para formatına uygun olmayan limitler gönderiyoruz.
        // THEN: @Digits validation mesajlarını bekliyoruz.
        mockMvc.perform(
                        post("/api/customers/1/cards")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.singleTransactionLimit")
                        .value(
                                "Tek işlem limiti en fazla 2 ondalık basamak içerebilir"
                        ))
                .andExpect(jsonPath("$.dailyLimit")
                        .value(
                                "Günlük limit en fazla 2 ondalık basamak içerebilir"
                        ));

        // DTO validation başarısız olduğu için Service'e ulaşılmamalıdır.
        verify(virtualCardService, never())
                .createCard(
                        eq(1L),
                        any(VirtualCardCreateRequest.class)
                );
    }
    @Test
    void createCard_whenLimitsAreMissing_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Limit alanları JSON içerisinde hiç gönderilmiyor.
        String requestBody = """
            {
                "cardName": "Alışveriş Kartım"
            }
            """;

        // WHEN: Eksik alanlarla kart oluşturma isteği gönderiliyor.
        // THEN: @NotNull validation mesajları dönmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.singleTransactionLimit")
                        .value("Tek işlem limiti zorunludur"))
                .andExpect(jsonPath("$.dailyLimit")
                        .value("Günlük limit zorunludur"));

        // Validation başarısız olduğu için Service çağrılmamalıdır.
        verify(virtualCardService, never())
                .createCard(
                        eq(1L),
                        any(VirtualCardCreateRequest.class)
                );
    }
}