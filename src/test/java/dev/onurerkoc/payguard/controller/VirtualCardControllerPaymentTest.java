package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.PaymentAuthorizationRequest;
import dev.onurerkoc.payguard.dto.PaymentAuthorizationResponse;
import dev.onurerkoc.payguard.entity.CardTransactionStatus;
import dev.onurerkoc.payguard.service.VirtualCardService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import dev.onurerkoc.payguard.entity.CardTransactionDeclineReason;
import dev.onurerkoc.payguard.exception.InvalidIdempotencyKeyException;
import dev.onurerkoc.payguard.exception.IdempotencyConflictException;
import dev.onurerkoc.payguard.exception.VirtualCardNotFoundException;
import dev.onurerkoc.payguard.entity.VirtualCard;
import org.springframework.orm.ObjectOptimisticLockingFailureException;


@WebMvcTest(VirtualCardController.class)
class VirtualCardControllerPaymentTest {

    // Gerçek sunucu açmadan HTTP isteği göndermemizi sağlar.
    @Autowired
    private MockMvc mockMvc;

    // Controller'ın gerçek Service bağımlılığını sahte Service ile değiştirir.
    @MockitoBean
    private VirtualCardService virtualCardService;

    @Test
    void authorizePayment_whenPaymentIsApproved_shouldReturnApprovedResponse()
            throws Exception {

        // GIVEN: Service'in oluşturduğu başarılı ödeme cevabı.
        Instant createdAt =
                Instant.parse("2026-09-09T18:30:00Z");

        PaymentAuthorizationResponse response =
                new PaymentAuthorizationResponse(
                        10L,
                        CardTransactionStatus.APPROVED,
                        null,
                        new BigDecimal("100.00"),
                        "Migros",
                        new BigDecimal("1300.00"),
                        createdAt,
                        5L
                );

        when(virtualCardService.authorizePayment(
                eq(1L),
                eq(5L),
                eq("payment-key-001"),
                any(PaymentAuthorizationRequest.class)
        )).thenReturn(response);

        String requestBody = """
                {
                    "amount": 100.00,
                    "merchantName": "Migros",
                    "onlineTransaction": false,
                    "internationalTransaction": false
                }
                """;

        // WHEN: Idempotency-Key header'ı ve ödeme JSON'u gönderiliyor.
        // THEN: Onaylanmış ödeme cevabı dönmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/payments")
                                .header(
                                        "Idempotency-Key",
                                        "payment-key-001"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(10))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.declineReason").doesNotExist())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.merchantName").value("Migros"))
                .andExpect(jsonPath("$.remainingBalance")
                        .value(1300.00))
                .andExpect(jsonPath("$.createdAt")
                        .value("2026-09-09T18:30:00Z"))
                .andExpect(jsonPath("$.cardId").value(5));

        // JSON'dan oluşturulan request DTO'sunu yakalıyoruz.
        ArgumentCaptor<PaymentAuthorizationRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        PaymentAuthorizationRequest.class
                );

        verify(virtualCardService)
                .authorizePayment(
                        eq(1L),
                        eq(5L),
                        eq("payment-key-001"),
                        requestCaptor.capture()
                );

        PaymentAuthorizationRequest capturedRequest =
                requestCaptor.getValue();

        assertEquals(
                0,
                capturedRequest.getAmount()
                        .compareTo(new BigDecimal("100.00"))
        );
        assertEquals(
                "Migros",
                capturedRequest.getMerchantName()
        );
        assertEquals(
                Boolean.FALSE,
                capturedRequest.getOnlineTransaction()
        );
        assertEquals(
                Boolean.FALSE,
                capturedRequest.getInternationalTransaction()
        );
    }
    @Test
    void authorizePayment_whenCardIsFrozen_shouldReturnDeclinedResponse()
            throws Exception {

        // GIVEN: Service, kart donmuş olduğu için reddedilmiş ödeme döndürüyor.
        PaymentAuthorizationResponse response =
                new PaymentAuthorizationResponse(
                        11L,
                        CardTransactionStatus.DECLINED,
                        CardTransactionDeclineReason.CARD_FROZEN,
                        new BigDecimal("100.00"),
                        "Migros",
                        new BigDecimal("1400.00"),
                        Instant.parse("2026-09-09T18:35:00Z"),
                        5L
                );

        when(virtualCardService.authorizePayment(
                eq(1L),
                eq(5L),
                eq("payment-key-002"),
                any(PaymentAuthorizationRequest.class)
        )).thenReturn(response);

        String requestBody = """
            {
                "amount": 100.00,
                "merchantName": "Migros",
                "onlineTransaction": false,
                "internationalTransaction": false
            }
            """;

        // WHEN: Donmuş kart için ödeme isteği gönderiliyor.
        // THEN: HTTP isteği başarıyla işlendiği için 200,
        // ödeme kararı ise DECLINED dönmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/payments")
                                .header(
                                        "Idempotency-Key",
                                        "payment-key-002"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(11))
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.declineReason")
                        .value("CARD_FROZEN"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.merchantName")
                        .value("Migros"))
                .andExpect(jsonPath("$.remainingBalance")
                        .value(1400.00))
                .andExpect(jsonPath("$.cardId").value(5));

        verify(virtualCardService)
                .authorizePayment(
                        eq(1L),
                        eq(5L),
                        eq("payment-key-002"),
                        any(PaymentAuthorizationRequest.class)
                );
    }
    @Test
    void authorizePayment_whenIdempotencyKeyIsMissing_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Ödeme JSON'u geçerli.
        // Fakat Idempotency-Key header'ı gönderilmeyecek.
        String requestBody = """
            {
                "amount": 100.00,
                "merchantName": "Migros",
                "onlineTransaction": false,
                "internationalTransaction": false
            }
            """;

        // WHEN: Header olmadan ödeme isteği gönderiyoruz.
        // THEN: Spring MVC isteği 400 Bad Request ile durdurmalıdır.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest());

        // Zorunlu header eksik olduğu için Controller metodu tamamlanamaz.
        // Bu nedenle Service kesinlikle çağrılmamalıdır.
        verify(virtualCardService, never())
                .authorizePayment(
                        eq(1L),
                        eq(5L),
                        any(String.class),
                        any(PaymentAuthorizationRequest.class)
                );
    }
    @Test
    void authorizePayment_whenIdempotencyKeyIsBlank_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: Ödeme JSON'u geçerli fakat header'ın değeri boş.
        String requestBody = """
            {
                "amount": 100.00,
                "merchantName": "Migros",
                "onlineTransaction": false,
                "internationalTransaction": false
            }
            """;

        // Header bulunduğu için Spring MVC isteği kabul eder.
        // Service ise boş anahtarı geçersiz kabul eder.
        when(virtualCardService.authorizePayment(
                eq(1L),
                eq(5L),
                eq("   "),
                any(PaymentAuthorizationRequest.class)
        )).thenThrow(
                new InvalidIdempotencyKeyException(
                        "Idempotency anahtarı boş olamaz"
                )
        );

        // WHEN: Sadece boşluklardan oluşan header gönderiliyor.
        // THEN: GlobalExceptionHandler 400 cevabı döndürmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/payments")
                                .header("Idempotency-Key", "   ")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Idempotency anahtarı boş olamaz"));

        // Header mevcut ve DTO geçerli olduğu için Service çağrılmıştır.
        verify(virtualCardService)
                .authorizePayment(
                        eq(1L),
                        eq(5L),
                        eq("   "),
                        any(PaymentAuthorizationRequest.class)
                );
    }
    @Test
    void authorizePayment_whenIdempotencyKeyConflicts_shouldReturnConflict()
            throws Exception {

        // GIVEN: Bu anahtar daha önce farklı bir ödeme için kullanılmış.
        String requestBody = """
            {
                "amount": 200.00,
                "merchantName": "Carrefour",
                "onlineTransaction": false,
                "internationalTransaction": false
            }
            """;

        when(virtualCardService.authorizePayment(
                eq(1L),
                eq(5L),
                eq("payment-key-001"),
                any(PaymentAuthorizationRequest.class)
        )).thenThrow(
                new IdempotencyConflictException(
                        "Idempotency anahtarı farklı bir ödeme için kullanılmış"
                )
        );

        // WHEN: Kullanılmış anahtarla farklı ödeme gönderiliyor.
        // THEN: GlobalExceptionHandler 409 Conflict dönmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/payments")
                                .header(
                                        "Idempotency-Key",
                                        "payment-key-001"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(
                                "Idempotency anahtarı farklı bir ödeme için kullanılmış"
                        ));

        verify(virtualCardService)
                .authorizePayment(
                        eq(1L),
                        eq(5L),
                        eq("payment-key-001"),
                        any(PaymentAuthorizationRequest.class)
                );
    }
    @Test
    void authorizePayment_whenRequestIsInvalid_shouldReturnBadRequest()
            throws Exception {

        // GIVEN:
        // amount geçersiz,
        // merchantName boş,
        // Boolean işlem bilgileri hiç gönderilmemiş.
        String requestBody = """
            {
                "amount": 0,
                "merchantName": " "
            }
            """;

        // WHEN: Geçerli header fakat geçersiz JSON gönderiliyor.
        // THEN: DTO validation bütün alan hatalarını döndürmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/payments")
                                .header(
                                        "Idempotency-Key",
                                        "payment-key-003"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount")
                        .value(
                                "Ödeme tutarı sıfırdan büyük olmalıdır"
                        ))
                .andExpect(jsonPath("$.merchantName")
                        .value("Mağaza adı zorunludur"))
                .andExpect(jsonPath("$.onlineTransaction")
                        .value(
                                "İnternet işlemi bilgisi zorunludur"
                        ))
                .andExpect(jsonPath("$.internationalTransaction")
                        .value(
                                "Yurt dışı işlemi bilgisi zorunludur"
                        ));

        // DTO validation başarısız olduğu için Service çağrılmamalıdır.
        verify(virtualCardService, never())
                .authorizePayment(
                        eq(1L),
                        eq(5L),
                        eq("payment-key-003"),
                        any(PaymentAuthorizationRequest.class)
                );
    }
    @Test
    void authorizePayment_whenAmountAndMerchantFormatAreInvalid_shouldReturnBadRequest()
            throws Exception {

        // GIVEN: DTO en fazla 100 karaktere izin veriyor.
        // Burada bilinçli olarak 101 karakter oluşturuyoruz.
        String longMerchantName = "A".repeat(101);

        String requestBody = """
            {
                "amount": 100.555,
                "merchantName": "%s",
                "onlineTransaction": false,
                "internationalTransaction": false
            }
            """.formatted(longMerchantName);

        // WHEN: Para formatı ve mağaza adı uzunluğu geçersiz.
        // THEN: İki validation mesajı da dönmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/payments")
                                .header(
                                        "Idempotency-Key",
                                        "payment-key-004"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount")
                        .value(
                                "Ödeme tutarı en fazla 2 ondalık basamak içerebilir"
                        ))
                .andExpect(jsonPath("$.merchantName")
                        .value(
                                "Mağaza adı en fazla 100 karakter olabilir"
                        ));

        // Validation başarısız olduğu için ödeme değerlendirilmemelidir.
        verify(virtualCardService, never())
                .authorizePayment(
                        eq(1L),
                        eq(5L),
                        eq("payment-key-004"),
                        any(PaymentAuthorizationRequest.class)
                );
    }
    @Test
    void authorizePayment_whenCardDoesNotExist_shouldReturnNotFound()
            throws Exception {

        // GIVEN: Ödeme isteği ve idempotency anahtarı geçerli,
        // ancak müşteriye ait kart bulunmuyor.
        String requestBody = """
            {
                "amount": 100.00,
                "merchantName": "Migros",
                "onlineTransaction": false,
                "internationalTransaction": false
            }
            """;

        when(virtualCardService.authorizePayment(
                eq(1L),
                eq(99L),
                eq("payment-key-005"),
                any(PaymentAuthorizationRequest.class)
        )).thenThrow(
                new VirtualCardNotFoundException(
                        "Sanal kart bulunamadı: 99"
                )
        );

        // WHEN: Bulunmayan karta ödeme gönderiliyor.
        // THEN: GlobalExceptionHandler 404 dönmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards/99/payments")
                                .header(
                                        "Idempotency-Key",
                                        "payment-key-005"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Sanal kart bulunamadı: 99"));

        verify(virtualCardService)
                .authorizePayment(
                        eq(1L),
                        eq(99L),
                        eq("payment-key-005"),
                        any(PaymentAuthorizationRequest.class)
                );
    }
    @Test
    void authorizePayment_whenOptimisticLockingFails_shouldReturnConflict()
            throws Exception {

        // GIVEN: Ödeme isteği geçerli ancak kart eş zamanlı olarak
        // başka bir işlem tarafından güncellenmiş.
        String requestBody = """
            {
                "amount": 100.00,
                "merchantName": "Migros",
                "onlineTransaction": false,
                "internationalTransaction": false
            }
            """;

        when(virtualCardService.authorizePayment(
                eq(1L),
                eq(5L),
                eq("payment-key-006"),
                any(PaymentAuthorizationRequest.class)
        )).thenThrow(
                new ObjectOptimisticLockingFailureException(
                        VirtualCard.class,
                        5L
                )
        );

        // WHEN: Eski kart versiyonuyla ödeme tamamlanmaya çalışılıyor.
        // THEN: GlobalExceptionHandler 409 Conflict döndürmelidir.
        mockMvc.perform(
                        post("/api/customers/1/cards/5/payments")
                                .header(
                                        "Idempotency-Key",
                                        "payment-key-006"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(
                                "Kart bilgileri başka bir işlem tarafından güncellendi. "
                                        + "Lütfen işlemi tekrar deneyin."
                        ));

        verify(virtualCardService)
                .authorizePayment(
                        eq(1L),
                        eq(5L),
                        eq("payment-key-006"),
                        any(PaymentAuthorizationRequest.class)
                );

    }
}