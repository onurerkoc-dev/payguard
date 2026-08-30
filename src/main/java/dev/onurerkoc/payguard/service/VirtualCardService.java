package dev.onurerkoc.payguard.service;


import dev.onurerkoc.payguard.dto.*;
import dev.onurerkoc.payguard.entity.*;
import dev.onurerkoc.payguard.exception.CustomerNotFoundException;
import dev.onurerkoc.payguard.exception.InvalidCardLimitException;
import dev.onurerkoc.payguard.exception.VirtualCardNotFoundException;
import dev.onurerkoc.payguard.repository.CardTransactionRepository;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.VirtualCardRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/*
URL: /customers/1/cards
             ↓
Controller: customerId = 1
             ↓
CustomerRepository: customers tablosundan ID 1'i bulur
             ↓
Java: Customer customer
             ↓
VirtualCard içine customer nesnesi koyulur
             ↓
Kart kaydedilir
             ↓
MySQL: virtual_cards.customer_id = 1
 */
@Service
public class VirtualCardService {

    private final VirtualCardRepository virtualCardRepository;
    private final CustomerRepository customerRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final CardTransactionRepository cardTransactionRepository;


    public VirtualCardService(
            VirtualCardRepository virtualCardRepository,
            CustomerRepository customerRepository,
            CardTransactionRepository cardTransactionRepository) {

        this.virtualCardRepository = virtualCardRepository;
        this.customerRepository = customerRepository;
        this.cardTransactionRepository = cardTransactionRepository;
    }

    /*
    Controller customerId ve JSON’u verecek
→ müşteri bulunacak
→ limitler kontrol edilecek
→ kart numarası üretilecek
→ son kullanma tarihi üretilecek
→ VirtualCard oluşturulacak
→ MySQL’e kaydedilecek
→ VirtualCardResponse döndürülecek
     */
    @Transactional
    public VirtualCardResponse createCard(
            Long customerId,
            VirtualCardCreateRequest request) {

        Customer customer = findCustomerById(customerId);

        validateLimits(
                request.getSingleTransactionLimit(),
                request.getDailyLimit()
        );

        String cardNumber = generateUniqueCardNumber();
        YearMonth expiryDate = generateExpiryDate();

        VirtualCard virtualCard = new VirtualCard(
                request.getCardName().trim(),
                cardNumber,
                expiryDate.getMonthValue(),
                expiryDate.getYear(),
                request.getSingleTransactionLimit(),
                request.getDailyLimit(),
                customer
        );

        VirtualCard savedCard = virtualCardRepository.save(virtualCard);

        return mapToResponse(savedCard);
    }
    /**
     * Verilen müşterinin varlığını kontrol eder.
     * Müşteriye ait kartları ID sırasına göre getirir ve
     * kart numaralarını maskeleyerek listeleme DTO'larına dönüştürür.
     *
     * @param customerId kartları listelenecek müşterinin ID'si
     * @return müşterinin maskelenmiş kart bilgilerinden oluşan liste
     * @throws CustomerNotFoundException müşteri bulunamazsa
     */
    @Transactional(readOnly = true)
    public List<VirtualCardSummaryResponse> getCardsByCustomerId(
            Long customerId) {

        findCustomerById(customerId);

        List<VirtualCard> cards =
                virtualCardRepository
                        .findAllByCustomerIdOrderByIdAsc(customerId);

        List<VirtualCardSummaryResponse> responses =
                new ArrayList<>();

        for (VirtualCard card : cards) {
            responses.add(mapToSummaryResponse(card));
        }

        return responses;
    }
    /*
    cardId ve customerId al
→ Repository’de kartı ara
→ Bulursan VirtualCard döndür
→ Bulamazsan exception fırlat
     */
    private VirtualCard findCardByIdAndCustomerId(
            Long cardId,
            Long customerId) {

        return virtualCardRepository
                .findByIdAndCustomerId(cardId, customerId)
                .orElseThrow(() ->
                        new VirtualCardNotFoundException(
                                "Sanal kart bulunamadı: " + cardId
                        )
                );
    }
    private Customer findCustomerById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new CustomerNotFoundException(
                                "Müşteri bulunamadı: " + customerId
                        )
                );
    }
    //Günlük limit, tek işlem limitinden küçük olamaz.
    private void validateLimits(
            BigDecimal singleTransactionLimit,
            BigDecimal dailyLimit) {

        if (dailyLimit.compareTo(singleTransactionLimit) < 0) {

            throw new InvalidCardLimitException(
                    "Günlük limit, tek işlem limitinden küçük olamaz"
            );
        }
    }
    @Transactional(readOnly = true)
    public VirtualCardResponse getCardById(
            Long customerId,
            Long cardId) {

        findCustomerById(customerId);

        VirtualCard card =
                findCardByIdAndCustomerId(cardId, customerId);

        return mapToResponse(card);
    }

    /*
    Bu mekanizmaya dirty checking denir:
Entity veritabanından getirildi
→ Hibernate eski hâlini takip etti
→ balance değişti
→ Transaction tamamlanırken UPDATE çalıştı
Dolayısıyla mevcut ve takip edilen bir entity için burada ayrıca:virtualCardRepository.save(card);
yazmak zorunlu değil.
     */
    @Transactional
    public VirtualCardResponse loadBalance(
            Long customerId,
            Long cardId,
            VirtualCardBalanceLoadRequest request) {

        findCustomerById(customerId);

        VirtualCard card =
                findCardByIdAndCustomerId(cardId, customerId);

        card.loadBalance(request.getAmount());

        // Kart bakiyesi yukarıdaki loadBalance çağrısıyla zaten artırıldı.
        // Aşağıdaki işlem bakiyeyi tekrar artırmaz;
        // yapılan bakiye yüklemesini işlem geçmişine kaydetmek için oluşturulur.
        CardTransaction balanceLoadTransaction =
                new CardTransaction(
                        CardTransactionType.BALANCE_LOAD,
                        CardTransactionStatus.APPROVED,
                        request.getAmount(),
                        null,
                        null,
                        card
                );
        // İşlem geçmişi kaydını card_transactions tablosuna yazar.
        cardTransactionRepository.save(balanceLoadTransaction);

        return mapToResponse(card);
    }
    @Transactional
    public VirtualCardResponse freezeCard(
            Long customerId,
            Long cardId) {

        findCustomerById(customerId);

        VirtualCard card =
                findCardByIdAndCustomerId(cardId, customerId);

        card.freeze();

        return mapToResponse(card);
    }
    @Transactional
    public VirtualCardResponse unfreezeCard(
            Long customerId,
            Long cardId) {

        findCustomerById(customerId);

        VirtualCard card =
                findCardByIdAndCustomerId(cardId, customerId);

        card.unfreeze();

        return mapToResponse(card);
    }
    @Transactional
    public VirtualCardResponse updateLimits(
            Long customerId,
            Long cardId,
            VirtualCardLimitUpdateRequest request) {

        findCustomerById(customerId);

        VirtualCard card =
                findCardByIdAndCustomerId(cardId, customerId);

        validateLimits(
                request.getSingleTransactionLimit(),
                request.getDailyLimit()
        );

        card.updateLimits(
                request.getSingleTransactionLimit(),
                request.getDailyLimit()
        );

        return mapToResponse(card);
    }

    // kart numarası üretme
    private String generateUniqueCardNumber() {

        String cardNumber;

        do {
            StringBuilder numberBuilder = new StringBuilder("9999");

            for (int i = 0; i < 12; i++) {
                numberBuilder.append(secureRandom.nextInt(10));
            }

            cardNumber = numberBuilder.toString();

        } while (virtualCardRepository.existsByCardNumber(cardNumber));

        return cardNumber;
    }
    private YearMonth generateExpiryDate() {
        return YearMonth.now().plusYears(4);
    }
    private VirtualCardResponse mapToResponse(VirtualCard card) {

        return new VirtualCardResponse(
                card.getId(),
                card.getCardName(),
                card.getCardNumber(),
                card.getExpiryMonth(),
                card.getExpiryYear(),
                card.getBalance(),
                card.getSingleTransactionLimit(),
                card.getDailyLimit(),
                card.isFrozen(),
                card.getCustomer().getId()
        );
    }
    //Service’te kart numarasını maskeleyen yardımcı metod
    private String maskCardNumber(String cardNumber) {

        String lastFourDigits =
                cardNumber.substring(cardNumber.length() - 4);

        return "**** **** **** " + lastFourDigits;
    }
    /**
     * VirtualCard entity'sini listeleme response DTO'suna dönüştürür.
     * Dönüşüm sırasında kart numarasının yalnızca son dört hanesini gösterir.
     */
    private VirtualCardSummaryResponse mapToSummaryResponse(
            VirtualCard card) {

        return new VirtualCardSummaryResponse(
                card.getId(),
                card.getCardName(),
                maskCardNumber(card.getCardNumber()),
                card.getBalance(),
                card.isFrozen()
        );
    }
}
