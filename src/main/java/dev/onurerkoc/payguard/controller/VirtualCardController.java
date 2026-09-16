package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.config.OpenApiConfig;
import dev.onurerkoc.payguard.dto.*;
import dev.onurerkoc.payguard.service.VirtualCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Sanal Kartlar ve Ödemeler",
        description = "Sanal kart, bakiye, limit, ödeme ve işlem geçmişi işlemleri"
)
@SecurityRequirement(name = OpenApiConfig.BASIC_AUTH_SCHEME)
@RestController
@RequestMapping("/api/customers")
public class VirtualCardController {

    private final VirtualCardService virtualCardService;

    public VirtualCardController(
            VirtualCardService virtualCardService) {

        this.virtualCardService = virtualCardService;
    }

    @Operation(
            summary = "Yeni sanal kart oluşturur",
            description = "Giriş yapan kullanıcının müşteri hesabına bağlı, "
                    + "başlangıç bakiyesi sıfır olan sanal kart oluşturur."
    )
    @PostMapping("/{customerId}/cards")
    public ResponseEntity<VirtualCardResponse> createCard(
            @PathVariable("customerId") Long customerId,
            @Valid @RequestBody VirtualCardCreateRequest request) {

        VirtualCardResponse response =
                virtualCardService.createCard(
                        customerId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(
            summary = "Müşterinin sanal kartlarını listeler",
            description = "Kart numaralarını maskelenmiş biçimde "
                    + "ve ID sırasına göre döndürür."
    )
    @GetMapping("/{customerId}/cards")
    public ResponseEntity<List<VirtualCardSummaryResponse>> getCards(
            @PathVariable("customerId") Long customerId) {

        List<VirtualCardSummaryResponse> cards =
                virtualCardService.getCardsByCustomerId(customerId);

        return ResponseEntity.ok(cards);
    }

    @Operation(
            summary = "Sanal kart detayını getirir",
            description = "Belirtilen müşteriye ait sanal kartın "
                    + "güncel bilgilerini döndürür."
    )
    @GetMapping("/{customerId}/cards/{cardId}")
    public ResponseEntity<VirtualCardResponse> getCardById(
            @PathVariable("customerId") Long customerId,
            @PathVariable("cardId") Long cardId) {

        VirtualCardResponse card =
                virtualCardService.getCardById(
                        customerId,
                        cardId
                );

        return ResponseEntity.ok(card);
    }

    @Operation(
            summary = "Sanal karta bakiye yükler",
            description = "Kart bakiyesini artırır ve işlemi "
                    + "BALANCE_LOAD olarak işlem geçmişine kaydeder."
    )
    @PostMapping("/{customerId}/cards/{cardId}/balance")
    public ResponseEntity<VirtualCardResponse> loadBalance(
            @PathVariable("customerId") Long customerId,
            @PathVariable("cardId") Long cardId,
            @Valid @RequestBody
            VirtualCardBalanceLoadRequest request) {

        VirtualCardResponse response =
                virtualCardService.loadBalance(
                        customerId,
                        cardId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Sanal kartı dondurur",
            description = "Kartı ödeme işlemlerine kapatır."
    )
    @PatchMapping("/{customerId}/cards/{cardId}/freeze")
    public ResponseEntity<VirtualCardResponse> freezeCard(
            @PathVariable("customerId") Long customerId,
            @PathVariable("cardId") Long cardId) {

        VirtualCardResponse response =
                virtualCardService.freezeCard(
                        customerId,
                        cardId
                );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Sanal kartı yeniden kullanıma açar",
            description = "Dondurulmuş kartın tekrar ödeme işlemlerinde "
                    + "kullanılmasını sağlar."
    )
    @PatchMapping("/{customerId}/cards/{cardId}/unfreeze")
    public ResponseEntity<VirtualCardResponse> unfreezeCard(
            @PathVariable("customerId") Long customerId,
            @PathVariable("cardId") Long cardId) {

        VirtualCardResponse response =
                virtualCardService.unfreezeCard(
                        customerId,
                        cardId
                );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Kart limitlerini günceller",
            description = "Tek işlem ve günlük harcama limitlerini "
                    + "doğrulayarak günceller."
    )
    @PatchMapping("/{customerId}/cards/{cardId}/limits")
    public ResponseEntity<VirtualCardResponse> updateLimits(
            @PathVariable("customerId") Long customerId,
            @PathVariable("cardId") Long cardId,
            @Valid @RequestBody
            VirtualCardLimitUpdateRequest request) {

        VirtualCardResponse response =
                virtualCardService.updateLimits(
                        customerId,
                        cardId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Kartın ödeme izinlerini günceller",
            description = "İnternet ve yurt dışı işlem izinlerini "
                    + "birlikte günceller."
    )
    @PatchMapping("/{customerId}/cards/{cardId}/payment-settings")
    public ResponseEntity<VirtualCardResponse> updatePaymentSettings(
            @PathVariable("customerId") Long customerId,
            @PathVariable("cardId") Long cardId,
            @Valid @RequestBody
            VirtualCardPaymentSettingsRequest request) {

        VirtualCardResponse response =
                virtualCardService.updatePaymentSettings(
                        customerId,
                        cardId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Ödeme isteğini yetkilendirir",
            description = "Kart durumu, bakiye, limitler ve ödeme izinlerini "
                    + "kontrol ederek APPROVED veya DECLINED sonucu üretir."
    )
    @PostMapping("/{customerId}/cards/{cardId}/payments")
    public ResponseEntity<PaymentAuthorizationResponse> authorizePayment(
            @PathVariable("customerId") Long customerId,
            @PathVariable("cardId") Long cardId,
            @Parameter(
                    description = "Aynı ödeme isteğinin ikinci kez "
                            + "işlenmesini engelleyen benzersiz anahtar",
                    required = true,
                    example = "payment-2026-0001"
            )
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,
            @Valid @RequestBody
            PaymentAuthorizationRequest request) {

        PaymentAuthorizationResponse response =
                virtualCardService.authorizePayment(
                        customerId,
                        cardId,
                        idempotencyKey,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Kartın işlem geçmişini getirir",
            description = "Bakiye yükleme ve ödeme işlemlerini "
                    + "sayfalı biçimde döndürür."
    )
    @GetMapping("/{customerId}/cards/{cardId}/transactions")
    public ResponseEntity<Page<CardTransactionResponse>>
    getCardTransactions(
            @PathVariable("customerId") Long customerId,
            @PathVariable("cardId") Long cardId,
            @Parameter(
                    description = "Sıfırdan başlayan sayfa numarası",
                    example = "0"
            )
            @RequestParam(defaultValue = "0")
            @Min(
                    value = 0,
                    message = "Sayfa numarası negatif olamaz"
            )
            int page,
            @Parameter(
                    description = "Bir sayfada döndürülecek işlem sayısı",
                    example = "10"
            )
            @RequestParam(defaultValue = "10")
            @Min(
                    value = 1,
                    message = "Sayfa boyutu en az 1 olmalıdır"
            )
            @Max(
                    value = 100,
                    message = "Sayfa boyutu en fazla 100 olabilir"
            )
            int size) {

        Page<CardTransactionResponse> transactions =
                virtualCardService.getTransactionsByCardId(
                        customerId,
                        cardId,
                        page,
                        size
                );

        return ResponseEntity.ok(transactions);
    }
}