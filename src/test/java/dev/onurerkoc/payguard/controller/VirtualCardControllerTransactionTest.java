package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.CardTransactionResponse;
import dev.onurerkoc.payguard.entity.CardTransactionStatus;
import dev.onurerkoc.payguard.entity.CardTransactionType;
import dev.onurerkoc.payguard.exception.VirtualCardNotFoundException;
import dev.onurerkoc.payguard.service.VirtualCardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VirtualCardController.class)
class VirtualCardControllerTransactionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VirtualCardService virtualCardService;

    @Test
    void getCardTransactions_whenTransactionsExist_shouldReturnPage()
            throws Exception {

        // GIVEN: En yeni işlem ilk sırada olacak şekilde
        // iki işlem cevabı hazırlıyoruz.
        List<CardTransactionResponse> transactions = List.of(
                new CardTransactionResponse(
                        2L,
                        CardTransactionType.PAYMENT,
                        CardTransactionStatus.APPROVED,
                        new BigDecimal("100.00"),
                        "Migros",
                        null,
                        Instant.parse("2026-09-10T12:00:00Z"),
                        5L
                ),
                new CardTransactionResponse(
                        1L,
                        CardTransactionType.BALANCE_LOAD,
                        CardTransactionStatus.APPROVED,
                        new BigDecimal("250.00"),
                        null,
                        null,
                        Instant.parse("2026-09-09T12:00:00Z"),
                        5L
                )
        );

        Page<CardTransactionResponse> transactionPage =
                new PageImpl<>(
                        transactions,
                        PageRequest.of(0, 10),
                        2
                );

        when(virtualCardService.getTransactionsByCardId(
                1L,
                5L,
                0,
                10
        )).thenReturn(transactionPage);

        // WHEN: İlk sayfadan 10 işlem istiyoruz.
        // THEN: Sayfa bilgileri ve işlemler doğru dönmelidir.
        mockMvc.perform(
                        get(
                                "/api/customers/1/cards/5/transactions"
                        )
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].type")
                        .value("PAYMENT"))
                .andExpect(jsonPath("$.content[0].status")
                        .value("APPROVED"))
                .andExpect(jsonPath("$.content[0].amount")
                        .value(100.00))
                .andExpect(jsonPath("$.content[0].merchantName")
                        .value("Migros"))
                .andExpect(jsonPath("$.content[0].cardId")
                        .value(5))
                .andExpect(jsonPath("$.content[1].id").value(1))
                .andExpect(jsonPath("$.content[1].type")
                        .value("BALANCE_LOAD"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));

        // URL ve query parametrelerinin Service'e doğru aktarıldığını doğrular.
        verify(virtualCardService)
                .getTransactionsByCardId(
                        1L,
                        5L,
                        0,
                        10
                );
    }
    @Test
    void getCardTransactions_whenPageParametersAreMissing_shouldUseDefaults()
            throws Exception {

        // GIVEN: Varsayılan page=0 ve size=10 için boş bir sayfa.
        Page<CardTransactionResponse> emptyPage =
                Page.empty(PageRequest.of(0, 10));

        when(virtualCardService.getTransactionsByCardId(
                1L,
                5L,
                0,
                10
        )).thenReturn(emptyPage);

        // WHEN: URL'de page ve size parametreleri gönderilmiyor.
        mockMvc.perform(
                        get(
                                "/api/customers/1/cards/5/transactions"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));

        // Controller'ın varsayılan değerleri Service'e aktardığını doğrular.
        verify(virtualCardService)
                .getTransactionsByCardId(
                        1L,
                        5L,
                        0,
                        10
                );
    }
    @Test
    void getCardTransactions_whenPageIsNegative_shouldReturnBadRequest()
            throws Exception {

        // WHEN: Negatif sayfa numarası gönderiliyor.
        // THEN: Controller parametre validation 400 dönmelidir.
        mockMvc.perform(
                        get(
                                "/api/customers/1/cards/5/transactions"
                        )
                                .param("page", "-1")
                                .param("size", "10")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.page")
                        .value("Sayfa numarası negatif olamaz"));

        // Parametre validation başarısız olduğu için
        // Service kesinlikle çağrılmamalıdır.
        verify(virtualCardService, never())
                .getTransactionsByCardId(
                        eq(1L),
                        eq(5L),
                        anyInt(),
                        anyInt()
                );
    }
    @Test
    void getCardTransactions_whenSizeIsZero_shouldReturnBadRequest()
            throws Exception {

        // WHEN: Bir sayfada sıfır kayıt isteniyor.
        // THEN: @Min validation 400 Bad Request üretmelidir.
        mockMvc.perform(
                        get(
                                "/api/customers/1/cards/5/transactions"
                        )
                                .param("page", "0")
                                .param("size", "0")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.size")
                        .value(
                                "Sayfa boyutu en az 1 olmalıdır"
                        ));

        // Parametre validation başarısız olduğu için
        // Service çalıştırılmamalıdır.
        verify(virtualCardService, never())
                .getTransactionsByCardId(
                        eq(1L),
                        eq(5L),
                        anyInt(),
                        anyInt()
                );
    }
    @Test
    void getCardTransactions_whenSizeExceedsMaximum_shouldReturnBadRequest()
            throws Exception {

        // WHEN: İzin verilen maksimum 100 değerinin üzerinde
        // bir sayfa boyutu gönderiliyor.
        // THEN: @Max validation 400 Bad Request üretmelidir.
        mockMvc.perform(
                        get(
                                "/api/customers/1/cards/5/transactions"
                        )
                                .param("page", "0")
                                .param("size", "101")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.size")
                        .value(
                                "Sayfa boyutu en fazla 100 olabilir"
                        ));

        // Geçersiz pagination isteği Service'e ulaşmamalıdır.
        verify(virtualCardService, never())
                .getTransactionsByCardId(
                        eq(1L),
                        eq(5L),
                        anyInt(),
                        anyInt()
                );
    }
    @Test
    void getCardTransactions_whenCardDoesNotExist_shouldReturnNotFound()
            throws Exception {

        // GIVEN: 1 numaralı müşteriye ait 99 numaralı kart bulunmuyor.
        when(virtualCardService.getTransactionsByCardId(
                1L,
                99L,
                0,
                10
        )).thenThrow(
                new VirtualCardNotFoundException(
                        "Sanal kart bulunamadı: 99"
                )
        );

        // WHEN: Bulunmayan kartın işlem geçmişi isteniyor.
        // THEN: GlobalExceptionHandler 404 cevabı döndürmelidir.
        mockMvc.perform(
                        get(
                                "/api/customers/1/cards/99/transactions"
                        )
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Sanal kart bulunamadı: 99"));

        verify(virtualCardService)
                .getTransactionsByCardId(
                        1L,
                        99L,
                        0,
                        10
                );
    }
}