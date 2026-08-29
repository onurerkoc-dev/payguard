package dev.onurerkoc.payguard.service;


import dev.onurerkoc.payguard.dto.VirtualCardCreateRequest;
import dev.onurerkoc.payguard.dto.VirtualCardResponse;
import dev.onurerkoc.payguard.dto.VirtualCardSummaryResponse;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.VirtualCard;
import dev.onurerkoc.payguard.exception.CustomerNotFoundException;
import dev.onurerkoc.payguard.exception.InvalidCardLimitException;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.VirtualCardRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

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

    public VirtualCardService(VirtualCardRepository virtualCardRepository, CustomerRepository customerRepository) {
        this.virtualCardRepository = virtualCardRepository;
        this.customerRepository = customerRepository;
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

        validateLimits(request);

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
    private Customer findCustomerById(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() ->
                        new CustomerNotFoundException(
                                "Müşteri bulunamadı: " + customerId
                        )
                );
    }
    //Günlük limit, tek işlem limitinden küçük olamaz.
    private void validateLimits(VirtualCardCreateRequest request) {

        if (request.getDailyLimit()
                .compareTo(request.getSingleTransactionLimit()) < 0) {

            throw new InvalidCardLimitException(
                    "Günlük limit, tek işlem limitinden küçük olamaz"
            );
        }
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
