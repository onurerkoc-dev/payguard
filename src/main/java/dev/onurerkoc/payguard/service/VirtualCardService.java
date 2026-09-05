package dev.onurerkoc.payguard.service;


import dev.onurerkoc.payguard.dto.*;
import dev.onurerkoc.payguard.entity.*;
import dev.onurerkoc.payguard.exception.*;
import dev.onurerkoc.payguard.repository.CardTransactionRepository;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.VirtualCardRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import dev.onurerkoc.payguard.dto.VirtualCardPaymentSettingsRequest;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    @Transactional
    public VirtualCardResponse updatePaymentSettings(
            Long customerId,
            Long cardId,
            VirtualCardPaymentSettingsRequest request) {

        // URL'deki müşterinin gerçekten var olduğunu kontrol eder.
        findCustomerById(customerId);

        // Kartı bulur ve belirtilen müşteriye ait olduğunu doğrular.
        VirtualCard card =
                findCardByIdAndCustomerId(cardId, customerId);

        // JSON'dan DTO'ya gelen ayarları karta uygular.
        card.updatePaymentSettings(
                request.getOnlineTransactionsEnabled(),
                request.getInternationalTransactionsEnabled()
        );

        // Kartın güncel bilgilerini JSON cevabına dönüştürür.
        return mapToResponse(card);
    }
    /*
Ödeme isteğini bütün PayGuard iş kurallarına göre değerlendirir.

Ödeme reddedilirse bakiye değişmeden DECLINED kayıt oluşturur.
Ödeme onaylanırsa bakiyeyi azaltıp APPROVED kayıt oluşturur.
*/
    @Transactional
    public PaymentAuthorizationResponse authorizePayment(
            Long customerId,
            Long cardId,
            String idempotencyKey,
            PaymentAuthorizationRequest request) {
        String normalizedIdempotencyKey =
                validateAndNormalizeIdempotencyKey(idempotencyKey);
        // URL'deki müşterinin gerçekten var olduğunu kontrol eder.
        findCustomerById(customerId);

        // Kartı bulur ve URL'deki müşteriye ait olduğunu doğrular.
        VirtualCard card =
                findCardByIdAndCustomerId(cardId, customerId);
        // Aynı idempotency anahtarıyla daha önce işlem yapılmış mı?
        Optional<CardTransaction> existingTransaction =
                cardTransactionRepository.findByIdempotencyKey(
                        normalizedIdempotencyKey
                );

        if (existingTransaction.isPresent()) {

            CardTransaction transaction = existingTransaction.get();

            validateIdempotencyRequest(
                    transaction,
                    card,
                    request
            );

            // Aynı ödeme isteğinin tekrarıysa bakiye tekrar düşürülmez.
            // Daha önce oluşturulmuş işlemin sonucu döndürülür.
            return mapToPaymentAuthorizationResponse(transaction);
        }

        // Ödeme kurallarını sırayla kontrol eder.
        CardTransactionDeclineReason declineReason =
                determineDeclineReason(card, request);

        // Bir ret nedeni bulunduysa ödeme reddedilir.
        if (declineReason != null) {
            return createDeclinedPayment(
                    card,
                    request,
                    declineReason,
                    normalizedIdempotencyKey
            );
        }

        return createApprovedPayment(
                card,
                request,
                normalizedIdempotencyKey
        );
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
                card.isOnlineTransactionsEnabled(),
                card.isInternationalTransactionsEnabled(),
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

    /*
İstanbul saatine göre bugünün başlangıç ve bitiş zamanını hesaplar.
Yalnızca bugün onaylanmış PAYMENT işlemlerinin toplamını döndürür.
*/
    private BigDecimal calculateTodayApprovedPaymentTotal(Long cardId) {

        ZoneId istanbulZone = ZoneId.of("Europe/Istanbul");

        LocalDate today = LocalDate.now(istanbulZone);

        Instant startTime =
                today.atStartOfDay(istanbulZone).toInstant();

        Instant endTime =
                today.plusDays(1)
                        .atStartOfDay(istanbulZone)
                        .toInstant();

        return cardTransactionRepository.calculateTotalAmount(
                cardId,
                CardTransactionType.PAYMENT,
                CardTransactionStatus.APPROVED,
                startTime,
                endTime
        );
    }
    /*
Kartın son kullanma ayını İstanbul'daki mevcut ay ile karşılaştırır.
Kart, son kullanma ayının sonuna kadar geçerlidir.
*/
    private boolean isCardExpired(VirtualCard card) {

        YearMonth currentMonth =
                YearMonth.now(ZoneId.of("Europe/Istanbul"));

        YearMonth cardExpiryDate =
                YearMonth.of(
                        card.getExpiryYear(),
                        card.getExpiryMonth()
                );

        return cardExpiryDate.isBefore(currentMonth);
    }
    /*
Ödeme kurallarını belirlenen sırayla kontrol eder.

Bir kural geçmezse ilgili ret nedenini döndürür.
Bütün kurallar geçerse null döndürür.
Bu metot bakiyeyi değiştirmez ve işlem kaydı oluşturmaz.
*/
    private CardTransactionDeclineReason determineDeclineReason(
            VirtualCard card,
            PaymentAuthorizationRequest request) {

        // 1. Dondurulmuş kart hiçbir ödeme yapamaz.
        if (card.isFrozen()) {
            return CardTransactionDeclineReason.CARD_FROZEN;
        }

        // 2. Son kullanma tarihi geçmiş kart ödeme yapamaz.
        if (isCardExpired(card)) {
            return CardTransactionDeclineReason.CARD_EXPIRED;
        }

        // 3. Ödeme internet işlemi ise kartın internet izni açık olmalıdır.
        if (Boolean.TRUE.equals(request.getOnlineTransaction())
                && !card.isOnlineTransactionsEnabled()) {

            return CardTransactionDeclineReason
                    .ONLINE_TRANSACTIONS_DISABLED;
        }

        // 4. Ödeme yurt dışı işlemi ise kartın yurt dışı izni açık olmalıdır.
        if (Boolean.TRUE.equals(request.getInternationalTransaction())
                && !card.isInternationalTransactionsEnabled()) {

            return CardTransactionDeclineReason
                    .INTERNATIONAL_TRANSACTIONS_DISABLED;
        }

        // 5. Ödeme tutarı tek işlem limitini aşamaz.
        if (request.getAmount()
                .compareTo(card.getSingleTransactionLimit()) > 0) {

            return CardTransactionDeclineReason
                    .SINGLE_TRANSACTION_LIMIT_EXCEEDED;
        }

        // 6. Bugünkü onaylanmış ödemelerin toplamını bulur.
        BigDecimal todayApprovedTotal =
                calculateTodayApprovedPaymentTotal(card.getId());

        BigDecimal totalAfterPayment =
                todayApprovedTotal.add(request.getAmount());

        // Yeni ödemeyle birlikte günlük limit aşılıyor mu?
        if (totalAfterPayment.compareTo(card.getDailyLimit()) > 0) {

            return CardTransactionDeclineReason
                    .DAILY_LIMIT_EXCEEDED;
        }

        // 7. Kart bakiyesi ödeme tutarından küçük olamaz.
        if (card.getBalance().compareTo(request.getAmount()) < 0) {

            return CardTransactionDeclineReason
                    .INSUFFICIENT_BALANCE;
        }

        // Hiçbir ret sebebi bulunmadı; ödeme onaylanabilir.
        return null;
    }
    /*
Kaydedilmiş ödeme işlemini API response DTO'suna dönüştürür.
Kartın işlemden sonraki mevcut bakiyesini de cevaba ekler.
*/
    private PaymentAuthorizationResponse
    mapToPaymentAuthorizationResponse(CardTransaction transaction) {

        return new PaymentAuthorizationResponse(
                transaction.getId(),
                transaction.getStatus(),
                transaction.getDeclineReason(),
                transaction.getAmount(),
                transaction.getMerchantName(),
                transaction.getCard().getBalance(),
                transaction.getCreatedAt(),
                transaction.getCard().getId()
        );
    }
    /*
Reddedilen ödeme için işlem geçmişi kaydı oluşturur.
Kart bakiyesini azaltmaz.
*/
    private PaymentAuthorizationResponse createDeclinedPayment(
            VirtualCard card,
            PaymentAuthorizationRequest request,
            CardTransactionDeclineReason declineReason,
            String idempotencyKey) {

        CardTransaction transaction = new CardTransaction(
                CardTransactionType.PAYMENT,
                CardTransactionStatus.DECLINED,
                request.getAmount(),
                request.getMerchantName().trim(),
                declineReason,
                idempotencyKey,
                card
        );

        CardTransaction savedTransaction =
                savePaymentTransaction(transaction);

        return mapToPaymentAuthorizationResponse(savedTransaction);
    }
    /*
Onaylanan ödeme tutarını kart bakiyesinden düşürür.
Ardından APPROVED ödeme kaydını işlem geçmişine ekler.
*/
    private PaymentAuthorizationResponse createApprovedPayment(
            VirtualCard card,
            PaymentAuthorizationRequest request,
            String idempotencyKey) {

        // Ödeme onaylandığı için kart bakiyesi azaltılır.
        card.deductBalance(request.getAmount());

        CardTransaction transaction = new CardTransaction(
                CardTransactionType.PAYMENT,
                CardTransactionStatus.APPROVED,
                request.getAmount(),
                request.getMerchantName().trim(),
                null,
                idempotencyKey,
                card
        );

        CardTransaction savedTransaction =
                savePaymentTransaction(transaction);

        return mapToPaymentAuthorizationResponse(savedTransaction);
    }
    // Header'dan gelen idempotency anahtarını kontrol eder
// ve başındaki/sonundaki gereksiz boşlukları temizler.
    private String validateAndNormalizeIdempotencyKey(
            String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency anahtarı boş olamaz"
            );
        }

        String normalizedKey = idempotencyKey.trim();

        if (normalizedKey.length() > 100) {
            throw new InvalidIdempotencyKeyException(
                    "Idempotency anahtarı en fazla 100 karakter olabilir"
            );
        }

        return normalizedKey;
    }
    // Aynı idempotency anahtarının yalnızca aynı ödeme isteğinde
// tekrar kullanılmasına izin verir.
    private void validateIdempotencyRequest(
            CardTransaction existingTransaction,
            VirtualCard card,
            PaymentAuthorizationRequest request) {

        boolean sameCard =
                existingTransaction.getCard().getId().equals(card.getId());

        boolean sameAmount =
                existingTransaction.getAmount()
                        .compareTo(request.getAmount()) == 0;

        boolean sameMerchant =
                existingTransaction.getMerchantName()
                        .equals(request.getMerchantName().trim());

        if (!sameCard || !sameAmount || !sameMerchant) {
            throw new IdempotencyConflictException(
                    "Idempotency anahtarı farklı bir ödeme için kullanılmış"
            );
        }
    }
    // Ödeme işlemini hemen MySQL'e gönderir.
// Aynı idempotency anahtarı eş zamanlı kullanılırsa
// veritabanı hatasını anlamlı bir 409 Conflict hatasına dönüştürür.
    private CardTransaction savePaymentTransaction(
            CardTransaction transaction) {

        try {
            return cardTransactionRepository.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException exception) {
            throw new IdempotencyConflictException(
                    "Idempotency anahtarı başka bir ödeme işlemiyle çakıştı"
            );
        }
    }
}
