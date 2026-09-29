package dev.onurerkoc.payguard.controller;

import dev.onurerkoc.payguard.dto.*;
import dev.onurerkoc.payguard.exception.IdempotencyConflictException;
import dev.onurerkoc.payguard.exception.InvalidCardLimitException;
import dev.onurerkoc.payguard.exception.VirtualCardNotFoundException;
import dev.onurerkoc.payguard.security.PayGuardUserDetails;
import dev.onurerkoc.payguard.service.VirtualCardService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
public class CardPageController {

    private final VirtualCardService virtualCardService;

    public CardPageController(VirtualCardService virtualCardService) {
        this.virtualCardService = virtualCardService;
    }

    @GetMapping("/cards/new")
    public String showCreateCardPage(Model model) {
        // Boş form nesnesini HTML sayfasına gönderiyoruz.
        model.addAttribute("request", new VirtualCardCreateRequest());
        return "card-create";
    }

    @PostMapping("/cards/new")
    public String createCard(
            @Valid @ModelAttribute("request") VirtualCardCreateRequest request,
            BindingResult errors,
            @AuthenticationPrincipal PayGuardUserDetails user,
            Model model) {

        // Kart adı veya limit alanları geçersizse formu hatalarıyla göster.
        if (errors.hasErrors()) {
            return "card-create";
        }

        try {
            // Müşteri ID'sini formdan değil, giriş yapan kullanıcıdan alıyoruz.
            virtualCardService.createCard(user.getCustomerId(), request);
        } catch (InvalidCardLimitException exception) {
            // Günlük limit tek işlem limitinden küçükse formda açıklama göster.
            model.addAttribute("limitError", exception.getMessage());
            return "card-create";
        }

        // Başarılı işlemden sonra kart listesinin bulunduğu panele dön.
        return "redirect:/";
    }
    @GetMapping("/cards/{cardId}")
    public String showCardDetail(
            @PathVariable Long cardId,
            @AuthenticationPrincipal PayGuardUserDetails user,
            Model model) {

        // Kartı yalnızca giriş yapan müşterinin ID'siyle arıyoruz.
        VirtualCardResponse card = virtualCardService.getCardById(
                user.getCustomerId(),
                cardId
        );

        model.addAttribute("cardName", card.getCardName());
        model.addAttribute("balance", card.getBalance());
        model.addAttribute("singleTransactionLimit",
                card.getSingleTransactionLimit());
        model.addAttribute("dailyLimit", card.getDailyLimit());
        model.addAttribute("frozen", card.isFrozen());
        model.addAttribute("cardId", cardId);
        // Kartın kayıtlı ödeme izinlerini detay sayfasına gönder.
        model.addAttribute(
                "onlineTransactionsEnabled",
                card.isOnlineTransactionsEnabled()
        );
        model.addAttribute(
                "internationalTransactionsEnabled",
                card.isInternationalTransactionsEnabled()
        );
// 0 = ilk sayfa, 10 = en fazla 10 işlem.
// Servis işlemleri en yeniden eskiye sıralıyor.
        model.addAttribute(
                "transactions",
                virtualCardService
                        .getTransactionsByCardId(user.getCustomerId(), cardId, 0, 10)
                        .getContent()
        );
        return "card-detail";
    }
    @PostMapping("/cards/{cardId}/freeze")
    public String freezeCard(
            @PathVariable Long cardId,
            @AuthenticationPrincipal PayGuardUserDetails user) {

        // Müşteri ID'si giriş yapan hesaptan gelir.
        // Servis, kartın bu müşteriye ait olduğunu da kontrol eder.
        virtualCardService.freezeCard(user.getCustomerId(), cardId);

        // İşlemden sonra güncel durumu görmek için detay sayfasını yeniden aç.
        return "redirect:/cards/" + cardId;
    }

    @PostMapping("/cards/{cardId}/unfreeze")
    public String unfreezeCard(
            @PathVariable Long cardId,
            @AuthenticationPrincipal PayGuardUserDetails user) {

        virtualCardService.unfreezeCard(user.getCustomerId(), cardId);

        return "redirect:/cards/" + cardId;
    }
    @GetMapping("/cards/{cardId}/balance")
    public String showBalanceLoadPage(
            @PathVariable Long cardId,
            @AuthenticationPrincipal PayGuardUserDetails user,
            Model model) {

        // Formu göstermeden önce kartın giriş yapan müşteriye ait olduğunu doğrula.
        virtualCardService.getCardById(user.getCustomerId(), cardId);

        model.addAttribute("cardId", cardId);
        model.addAttribute("balanceRequest", new VirtualCardBalanceLoadRequest());
        return "balance-load";
    }

    @PostMapping("/cards/{cardId}/balance")
    public String loadCardBalance(
            @PathVariable Long cardId,
            @Valid @ModelAttribute("balanceRequest")
            VirtualCardBalanceLoadRequest request,
            BindingResult errors,
            @AuthenticationPrincipal PayGuardUserDetails user,
            Model model) {

        // Geçersiz tutarda bile bu kartın kullanıcıya ait olduğunu kontrol et.
        virtualCardService.getCardById(user.getCustomerId(), cardId);

        if (errors.hasErrors()) {
            model.addAttribute("cardId", cardId);
            return "balance-load";
        }

        // Servis bakiyeyi artırır ve işlem geçmişini kaydeder.
        virtualCardService.loadBalance(user.getCustomerId(), cardId, request);

        return "redirect:/cards/" + cardId;
    }
    @GetMapping("/cards/{cardId}/payments/new")
    public String showPaymentPage(
            @PathVariable Long cardId,
            @AuthenticationPrincipal PayGuardUserDetails user,
            Model model) {

        // Formu göstermeden önce kartın giriş yapan müşteriye ait olduğunu doğrula.
        virtualCardService.getCardById(user.getCustomerId(), cardId);

        model.addAttribute("cardId", cardId);
        model.addAttribute("paymentRequest", new PaymentAuthorizationRequest());

        // Bu form gönderildiğinde kullanılacak tekil ödeme anahtarı.
        model.addAttribute("idempotencyKey", UUID.randomUUID().toString());

        return "payment-form";
    }
    @PostMapping("/cards/{cardId}/payments")
    public String simulatePayment(
            @PathVariable Long cardId,
            @Valid @ModelAttribute("paymentRequest")
            PaymentAuthorizationRequest request,
            BindingResult errors,
            @RequestParam("idempotencyKey") String idempotencyKey,
            @AuthenticationPrincipal PayGuardUserDetails user,
            Model model,
    RedirectAttributes redirectAttributes) {

        if (errors.hasErrors()) {
            // Hatalı formda bile kartın bu müşteriye ait olduğunu doğrula.
            virtualCardService.getCardById(user.getCustomerId(), cardId);

            model.addAttribute("cardId", cardId);
            // Aynı form yeniden gösterilirken anahtarı değiştirme.
            model.addAttribute("idempotencyKey", idempotencyKey);
            return "payment-form";
        }

// Servis, onay veya ret kararını ve işlem ID'sini döndürür.
        PaymentAuthorizationResponse result;

        try {
            result = virtualCardService.authorizePayment(
                    user.getCustomerId(),
                    cardId,
                    idempotencyKey,
                    request
            );
        } catch (IdempotencyConflictException exception) {
            // Çakışmada yeniden ödeme başlatma; önce işlem geçmişini göster.
            redirectAttributes.addFlashAttribute(
                    "paymentError",
                    "Ödeme anahtarı çakıştı. Yeni bir denemeden önce işlem geçmişini kontrol edin."
            );
            return "redirect:/cards/" + cardId;
        }

        redirectAttributes.addFlashAttribute("paymentResult", result);
        return "redirect:/cards/" + cardId;
    }
    @GetMapping("/cards/{cardId}/payment-settings")
    public String showPaymentSettingsPage(
            @PathVariable Long cardId,
            @AuthenticationPrincipal PayGuardUserDetails user,
            Model model) {

        // Kartın giriş yapan müşteriye ait olduğunu doğrula ve mevcut ayarları al.
        VirtualCardResponse card = virtualCardService.getCardById(
                user.getCustomerId(), cardId
        );

        VirtualCardPaymentSettingsRequest request =
                new VirtualCardPaymentSettingsRequest();

        // Form, kartın kayıtlı ayarlarını seçili olarak açacak.
        request.setOnlineTransactionsEnabled(card.isOnlineTransactionsEnabled());
        request.setInternationalTransactionsEnabled(
                card.isInternationalTransactionsEnabled()
        );

        model.addAttribute("cardId", cardId);
        model.addAttribute("settingsRequest", request);
        return "payment-settings";
    }
    @PostMapping("/cards/{cardId}/payment-settings")
    public String updatePaymentSettings(
            @PathVariable Long cardId,
            @Valid @ModelAttribute("settingsRequest")
            VirtualCardPaymentSettingsRequest request,
            BindingResult errors,
            @AuthenticationPrincipal PayGuardUserDetails user,
            Model model) {

        if (errors.hasErrors()) {
            // Hatalı istekte de kartın giriş yapan müşteriye ait olduğunu doğrula.
            virtualCardService.getCardById(user.getCustomerId(), cardId);
            model.addAttribute("cardId", cardId);
            return "payment-settings";
        }

        // İki izni birlikte kaydet; servis kart sahipliğini kontrol eder.
        virtualCardService.updatePaymentSettings(
                user.getCustomerId(),
                cardId,
                request
        );

        return "redirect:/cards/" + cardId;
    }
    @ExceptionHandler(VirtualCardNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String showCardNotFoundPage() {
        // Kart bulunamadığında HTML şablonunu 404 durum koduyla göster.
        return "card-not-found";
    }
}