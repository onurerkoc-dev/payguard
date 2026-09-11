package dev.onurerkoc.payguard.service;

import dev.onurerkoc.payguard.exception.IdempotencyConflictException;
import dev.onurerkoc.payguard.exception.VirtualCardNotFoundException;
import dev.onurerkoc.payguard.repository.CardTransactionRepository;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.VirtualCardRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.onurerkoc.payguard.dto.VirtualCardCreateRequest;
import dev.onurerkoc.payguard.exception.CustomerNotFoundException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.VirtualCard;
import dev.onurerkoc.payguard.exception.InvalidCardLimitException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import dev.onurerkoc.payguard.dto.VirtualCardResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import dev.onurerkoc.payguard.dto.VirtualCardBalanceLoadRequest;
import dev.onurerkoc.payguard.entity.CardTransaction;
import dev.onurerkoc.payguard.entity.CardTransactionStatus;
import dev.onurerkoc.payguard.entity.CardTransactionType;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import dev.onurerkoc.payguard.dto.PaymentAuthorizationRequest;
import dev.onurerkoc.payguard.dto.PaymentAuthorizationResponse;
import dev.onurerkoc.payguard.entity.CardTransactionDeclineReason;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import dev.onurerkoc.payguard.exception.InvalidIdempotencyKeyException;

import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.dao.DataIntegrityViolationException;

/*
Gerçek VirtualCardService
        │
        ├── Mock VirtualCardRepository
        ├── Mock CustomerRepository
        └── Mock CardTransactionRepository
 */
//Bu test sınıfında Mockito kullanacağız. @Mock gibi Mockito annotation’larını hazırla.
@ExtendWith(MockitoExtension.class)
class VirtualCardServiceTest {

    @Mock
    private VirtualCardRepository virtualCardRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CardTransactionRepository cardTransactionRepository;

    @InjectMocks
    private VirtualCardService virtualCardService;


    /*
    Service, findById(99) çağırır
→ Mock repository Optional.empty() döndürür
→ Service CustomerNotFoundException fırlatır
→ assertThrows exception’ı yakalar
→ Test başarılı olur
     */
    @Test
    void createCardShouldThrowExceptionWhenCustomerDoesNotExist() {

        // Arrange: Geçerli bir kart oluşturma isteği hazırlanır.
        Long customerId = 99L;

        VirtualCardCreateRequest request =
                new VirtualCardCreateRequest();

        request.setCardName("Test Kartı");
        request.setSingleTransactionLimit(
                new BigDecimal("1000.00")
        );
        request.setDailyLimit(
                new BigDecimal("5000.00")
        );

        // Mock repository'ye, bu müşterinin bulunmadığını söyleriz.
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.empty());

        // Act + Assert: Service'in exception fırlatması beklenir.
        CustomerNotFoundException exception =
                assertThrows(
                        CustomerNotFoundException.class,
                        () -> virtualCardService.createCard(
                                customerId,
                                request
                        )
                );

        // Fırlatılan hata mesajını da doğrularız.
        assertEquals(
                "Müşteri bulunamadı: 99",
                exception.getMessage()
        );
    }
    @Test
    void createCardShouldNotSaveCardWhenLimitsAreInvalid() {

        // Arrange: Veritabanında var olduğunu kabul ettiğimiz müşteri.
        Long customerId = 1L;

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        // Tek işlem limiti günlük limitten büyük olacak şekilde
        // hatalı bir kart oluşturma isteği hazırlanır.
        VirtualCardCreateRequest request =
                new VirtualCardCreateRequest();

        request.setCardName("Test Kartı");
        request.setSingleTransactionLimit(
                new BigDecimal("1000.00")
        );
        request.setDailyLimit(
                new BigDecimal("500.00")
        );

        // Müşteri arandığında bulunduğunu söyleriz.
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        // Act + Assert: Geçersiz limitler exception oluşturmalıdır.
        InvalidCardLimitException exception =
                assertThrows(
                        InvalidCardLimitException.class,
                        () -> virtualCardService.createCard(
                                customerId,
                                request
                        )
                );

        assertEquals(
                "Günlük limit, tek işlem limitinden küçük olamaz",
                exception.getMessage()
        );

        // Geçersiz istek nedeniyle kart kesinlikle kaydedilmemelidir.
        verify(virtualCardRepository, never())
                .save(any(VirtualCard.class));
    }

    /*
    Mock müşteri bulundu dedi
→ Limitler geçerli
→ Benzersiz kart numarası üretildi
→ VirtualCard oluşturuldu
→ Mock repository save() çağrısını aldı
→ Kaydedilen kart response’a çevrildi
→ Alanlar doğrulandı
     */
    @Test
    void createCardShouldSaveAndReturnCardWhenRequestIsValid() {

        // Arrange
        Long customerId = 1L;

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCardCreateRequest request =
                new VirtualCardCreateRequest();

        request.setCardName("Test Kartı");
        request.setSingleTransactionLimit(
                new BigDecimal("1000.00")
        );
        request.setDailyLimit(
                new BigDecimal("5000.00")
        );

        // Müşterinin bulunduğunu kabul ediyoruz.
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        // Üretilen kart numarasının daha önce kullanılmadığını kabul ediyoruz.
        when(virtualCardRepository.existsByCardNumber(anyString()))
                .thenReturn(false);

        // Repository, kaydedilen kart nesnesini geri döndürsün.
        when(virtualCardRepository.save(any(VirtualCard.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        VirtualCardResponse response =
                virtualCardService.createCard(customerId, request);

        // Assert: Response değerleri kontrol edilir.
        assertEquals("Test Kartı", response.getCardName());
        assertEquals(BigDecimal.ZERO, response.getBalance());

        assertEquals(
                new BigDecimal("1000.00"),
                response.getSingleTransactionLimit()
        );

        assertEquals(
                new BigDecimal("5000.00"),
                response.getDailyLimit()
        );

        assertFalse(response.isFrozen());
        assertTrue(response.isOnlineTransactionsEnabled());
        assertFalse(response.isInternationalTransactionsEnabled());

        // Sistem tarafından oluşturulan kart numarası kontrol edilir.
        assertEquals(16, response.getCardNumber().length());
        assertTrue(response.getCardNumber().startsWith("9999"));

        // Kartın repository'ye kaydedildiği doğrulanır.
        verify(virtualCardRepository)
                .save(any(VirtualCard.class));
    }
    @Test
    void freezeCardShouldFreezeCardWhenCustomerAndCardExist() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                9,
                2030,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        // Müşteri arandığında bulunduğunu kabul ediyoruz.
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        // Bu müşteriye ait kart arandığında bulunduğunu kabul ediyoruz.
        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        // Act
        VirtualCardResponse response =
                virtualCardService.freezeCard(customerId, cardId);

        // Assert
        assertTrue(card.isFrozen());
        assertTrue(response.isFrozen());

        verify(customerRepository).findById(customerId);

        verify(virtualCardRepository)
                .findByIdAndCustomerId(cardId, customerId);
    }
    @Test
    void freezeCardShouldThrowExceptionWhenCardDoesNotBelongToCustomer() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        // Müşteri sistemde bulunuyor.
        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        // Fakat bu ID'ye sahip kart müşteriye ait değil
        // veya böyle bir kart bulunmuyor.
        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.empty());

        // Act + Assert
        VirtualCardNotFoundException exception =
                assertThrows(
                        VirtualCardNotFoundException.class,
                        () -> virtualCardService.freezeCard(
                                customerId,
                                cardId
                        )
                );

        assertEquals(
                "Sanal kart bulunamadı: 10",
                exception.getMessage()
        );

        verify(customerRepository).findById(customerId);

        verify(virtualCardRepository)
                .findByIdAndCustomerId(cardId, customerId);
    }
    /*
    250 TL yükle
→ Kart bakiyesi 250 TL olur
→ BALANCE_LOAD işlemi oluşturulur
→ Durumu APPROVED olur
→ İşlem repository'ye gönderilir
→ ArgumentCaptor işlemi yakalar
→ Alanları test edilir
     */
    @Test
    void loadBalanceShouldIncreaseBalanceAndCreateTransaction() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                9,
                2030,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        VirtualCardBalanceLoadRequest request =
                new VirtualCardBalanceLoadRequest();

        request.setAmount(new BigDecimal("250.00"));

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        // Act
        VirtualCardResponse response =
                virtualCardService.loadBalance(
                        customerId,
                        cardId,
                        request
                );

        // Assert: Kartın bakiyesi artmış olmalıdır.
        assertEquals(
                new BigDecimal("250.00"),
                card.getBalance()
        );

        assertEquals(
                new BigDecimal("250.00"),
                response.getBalance()
        );

        // Repository'ye gönderilen işlem kaydını yakalarız.
        ArgumentCaptor<CardTransaction> transactionCaptor =
                ArgumentCaptor.forClass(CardTransaction.class);

        verify(cardTransactionRepository)
                .save(transactionCaptor.capture());

        CardTransaction savedTransaction =
                transactionCaptor.getValue();

        // Oluşturulan işlem kaydının içeriğini doğrularız.
        assertEquals(
                CardTransactionType.BALANCE_LOAD,
                savedTransaction.getType()
        );

        assertEquals(
                CardTransactionStatus.APPROVED,
                savedTransaction.getStatus()
        );

        assertEquals(
                new BigDecimal("250.00"),
                savedTransaction.getAmount()
        );

        assertNull(savedTransaction.getMerchantName());
        assertNull(savedTransaction.getDeclineReason());
        assertSame(card, savedTransaction.getCard());
    }
    /*
    Kart bakiyesi 500 TL
→ Kart donduruldu
→ 100 TL ödeme istendi
→ Service CARD_FROZEN nedenini buldu
→ Bakiye düşürülmedi
→ DECLINED işlem oluşturuldu
→ İşlem saveAndFlush ile kaydedildi
     */
    @Test
    void authorizePaymentShouldDeclineWhenCardIsFrozen() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-001";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                9,
                2030,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        // Karta 500 TL yüklenir ve kart dondurulur.
        card.loadBalance(new BigDecimal("500.00"));
        card.freeze();

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        // Bu idempotency anahtarı daha önce kullanılmamış.
        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.empty());

        // Kaydedilen işlemin kendisini geri döndür.
        when(cardTransactionRepository.saveAndFlush(
                any(CardTransaction.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentAuthorizationResponse response =
                virtualCardService.authorizePayment(
                        customerId,
                        cardId,
                        idempotencyKey,
                        request
                );

        // Assert: Ödeme reddedilmelidir.
        assertEquals(
                CardTransactionStatus.DECLINED,
                response.getStatus()
        );

        assertEquals(
                CardTransactionDeclineReason.CARD_FROZEN,
                response.getDeclineReason()
        );

        // Bakiye 500 TL olarak kalmalıdır.
        assertEquals(
                new BigDecimal("500.00"),
                card.getBalance()
        );

        assertEquals(
                new BigDecimal("500.00"),
                response.getRemainingBalance()
        );

        // Reddedilen işlem yine de işlem geçmişine kaydedilmelidir.
        ArgumentCaptor<CardTransaction> transactionCaptor =
                ArgumentCaptor.forClass(CardTransaction.class);

        verify(cardTransactionRepository)
                .saveAndFlush(transactionCaptor.capture());

        CardTransaction savedTransaction =
                transactionCaptor.getValue();

        assertEquals(
                CardTransactionStatus.DECLINED,
                savedTransaction.getStatus()
        );

        assertEquals(
                CardTransactionDeclineReason.CARD_FROZEN,
                savedTransaction.getDeclineReason()
        );

        assertEquals(
                new BigDecimal("100.00"),
                savedTransaction.getAmount()
        );
// Reddedilen ödeme de idempotency karşılaştırması için
// işlem özelliklerini saklamalıdır.
        assertEquals(
                Boolean.FALSE,
                savedTransaction.getOnlineTransaction()
        );

        assertEquals(
                Boolean.FALSE,
                savedTransaction.getInternationalTransaction()
        );

// Reddedilen ödemede bakiye düşmediği için
// işlem sonrası bakiye 500 TL olarak kaydedilmelidir.
        assertEquals(
                new BigDecimal("500.00"),
                savedTransaction.getBalanceAfterTransaction()
        );
        assertSame(card, savedTransaction.getCard());
    }
    @Test
    void authorizePaymentShouldApproveAndDeductBalanceWhenRulesPass() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-002";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        // Gerçek uygulamada ID'yi JPA verir.
        // Unit testte JPA çalışmadığı için test amacıyla ID'yi biz veriyoruz.
        ReflectionTestUtils.setField(card, "id", cardId);

        card.loadBalance(new BigDecimal("500.00"));

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        // Idempotency anahtarı daha önce kullanılmamış.
        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.empty());

        // Bugün daha önce ödeme yapılmadığını kabul ediyoruz.
        when(cardTransactionRepository.calculateTotalAmount(
                eq(cardId),
                eq(CardTransactionType.PAYMENT),
                eq(CardTransactionStatus.APPROVED),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(BigDecimal.ZERO);

        when(cardTransactionRepository.saveAndFlush(
                any(CardTransaction.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentAuthorizationResponse response =
                virtualCardService.authorizePayment(
                        customerId,
                        cardId,
                        idempotencyKey,
                        request
                );

        // Assert: Ödeme onaylanmalıdır.
        assertEquals(
                CardTransactionStatus.APPROVED,
                response.getStatus()
        );

        assertNull(response.getDeclineReason());

        // 500 - 100 = 400 TL kalmalıdır.
        assertEquals(
                new BigDecimal("400.00"),
                card.getBalance()
        );

        assertEquals(
                new BigDecimal("400.00"),
                response.getRemainingBalance()
        );

        assertEquals("Migros", response.getMerchantName());
        assertEquals(cardId, response.getCardId());

        // Kaydedilen ödeme işlemini yakalarız.
        ArgumentCaptor<CardTransaction> transactionCaptor =
                ArgumentCaptor.forClass(CardTransaction.class);

        verify(cardTransactionRepository)
                .saveAndFlush(transactionCaptor.capture());

        CardTransaction savedTransaction =
                transactionCaptor.getValue();

        assertEquals(
                CardTransactionType.PAYMENT,
                savedTransaction.getType()
        );

        assertEquals(
                CardTransactionStatus.APPROVED,
                savedTransaction.getStatus()
        );

        assertNull(savedTransaction.getDeclineReason());

        assertEquals(
                new BigDecimal("100.00"),
                savedTransaction.getAmount()
        );
// İstek türlerinin işlem kaydına aktarıldığını doğrular.
        assertEquals(
                Boolean.FALSE,
                savedTransaction.getOnlineTransaction()
        );

        assertEquals(
                Boolean.FALSE,
                savedTransaction.getInternationalTransaction()
        );

// İşlemden sonra kalan bakiyenin işlem anındaki haliyle
// transaction kaydına alındığını doğrular.
        assertEquals(
                new BigDecimal("400.00"),
                savedTransaction.getBalanceAfterTransaction()
        );
        assertSame(card, savedTransaction.getCard());
    }
    @Test
    void authorizePaymentShouldDeclineWhenBalanceIsInsufficient() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-003";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);

        // Kartta yalnızca 50 TL bulunuyor.
        card.loadBalance(new BigDecimal("50.00"));

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        // 100 TL ödeme yapılmak isteniyor.
        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.empty());

        // Bugün daha önce onaylanmış ödeme bulunmuyor.
        when(cardTransactionRepository.calculateTotalAmount(
                eq(cardId),
                eq(CardTransactionType.PAYMENT),
                eq(CardTransactionStatus.APPROVED),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(BigDecimal.ZERO);

        when(cardTransactionRepository.saveAndFlush(
                any(CardTransaction.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentAuthorizationResponse response =
                virtualCardService.authorizePayment(
                        customerId,
                        cardId,
                        idempotencyKey,
                        request
                );

        // Assert: Ödeme reddedilmelidir.
        assertEquals(
                CardTransactionStatus.DECLINED,
                response.getStatus()
        );

        assertEquals(
                CardTransactionDeclineReason.INSUFFICIENT_BALANCE,
                response.getDeclineReason()
        );

        // Başarısız ödeme bakiyeyi değiştirmemelidir.
        assertEquals(
                new BigDecimal("50.00"),
                card.getBalance()
        );

        assertEquals(
                new BigDecimal("50.00"),
                response.getRemainingBalance()
        );

        // Reddedilen işlemin kaydedildiğini doğrularız.
        ArgumentCaptor<CardTransaction> transactionCaptor =
                ArgumentCaptor.forClass(CardTransaction.class);

        verify(cardTransactionRepository)
                .saveAndFlush(transactionCaptor.capture());

        CardTransaction savedTransaction =
                transactionCaptor.getValue();

        assertEquals(
                CardTransactionStatus.DECLINED,
                savedTransaction.getStatus()
        );

        assertEquals(
                CardTransactionDeclineReason.INSUFFICIENT_BALANCE,
                savedTransaction.getDeclineReason()
        );

        assertEquals(
                new BigDecimal("100.00"),
                savedTransaction.getAmount()
        );
    }
    @Test
    void authorizePaymentShouldDeclineWhenSingleTransactionLimitIsExceeded() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-004";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,

                // Tek işlem limiti 100 TL
                new BigDecimal("100.00"),

                // Günlük limit 1000 TL
                new BigDecimal("1000.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);

        // Bakiye özellikle yüksek tutuluyor.
        // Böylece ret nedeninin yetersiz bakiye olmadığını biliyoruz.
        card.loadBalance(new BigDecimal("5000.00"));

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        // Tek işlem limitini aşan 150 TL ödeme
        request.setAmount(new BigDecimal("150.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(cardTransactionRepository.saveAndFlush(
                any(CardTransaction.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentAuthorizationResponse response =
                virtualCardService.authorizePayment(
                        customerId,
                        cardId,
                        idempotencyKey,
                        request
                );

        // Assert
        assertEquals(
                CardTransactionStatus.DECLINED,
                response.getStatus()
        );

        assertEquals(
                CardTransactionDeclineReason
                        .SINGLE_TRANSACTION_LIMIT_EXCEEDED,
                response.getDeclineReason()
        );

        // Reddedilen ödeme bakiyeyi azaltmamalıdır.
        assertEquals(
                new BigDecimal("5000.00"),
                card.getBalance()
        );

        verify(cardTransactionRepository)
                .saveAndFlush(any(CardTransaction.class));
    }
    @Test
    void authorizePaymentShouldDeclineWhenDailyLimitIsExceeded() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-005";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,

                // Tek işlem limiti
                new BigDecimal("1000.00"),

                // Günlük limit
                new BigDecimal("500.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);

        // Bakiye yeterli olsun ki test yalnızca günlük limite odaklansın.
        card.loadBalance(new BigDecimal("5000.00"));

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.empty());

        // Bugün daha önce toplam 450 TL ödeme yapılmış.
        when(cardTransactionRepository.calculateTotalAmount(
                eq(cardId),
                eq(CardTransactionType.PAYMENT),
                eq(CardTransactionStatus.APPROVED),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(new BigDecimal("450.00"));

        when(cardTransactionRepository.saveAndFlush(
                any(CardTransaction.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentAuthorizationResponse response =
                virtualCardService.authorizePayment(
                        customerId,
                        cardId,
                        idempotencyKey,
                        request
                );

        // Assert
        assertEquals(
                CardTransactionStatus.DECLINED,
                response.getStatus()
        );

        assertEquals(
                CardTransactionDeclineReason.DAILY_LIMIT_EXCEEDED,
                response.getDeclineReason()
        );

        // 450 + 100 = 550 TL, günlük limit ise 500 TL.
        // Ödeme reddedildiği için kart bakiyesi değişmemelidir.
        assertEquals(
                new BigDecimal("5000.00"),
                card.getBalance()
        );

        verify(cardTransactionRepository)
                .saveAndFlush(any(CardTransaction.class));
    }
    @Test
    void authorizePaymentShouldDeclineWhenOnlineTransactionsAreDisabled() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-006";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);

        card.loadBalance(new BigDecimal("5000.00"));

        // Online işlem iznini kapatıyoruz.
        // Yurt dışı işlem izni de kapalı kalıyor.
        card.updatePaymentSettings(false, false);

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Online Mağaza");

        // Bu ödeme internet üzerinden geliyor.
        request.setOnlineTransaction(true);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(cardTransactionRepository.saveAndFlush(
                any(CardTransaction.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentAuthorizationResponse response =
                virtualCardService.authorizePayment(
                        customerId,
                        cardId,
                        idempotencyKey,
                        request
                );

        // Assert
        assertEquals(
                CardTransactionStatus.DECLINED,
                response.getStatus()
        );

        assertEquals(
                CardTransactionDeclineReason
                        .ONLINE_TRANSACTIONS_DISABLED,
                response.getDeclineReason()
        );

        // Reddedilen ödeme bakiyeyi azaltmamalıdır.
        assertEquals(
                new BigDecimal("5000.00"),
                card.getBalance()
        );

        verify(cardTransactionRepository)
                .saveAndFlush(any(CardTransaction.class));
    }
    @Test
    void authorizePaymentShouldDeclineWhenInternationalTransactionsAreDisabled() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-007";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);

        card.loadBalance(new BigDecimal("5000.00"));

        // Online işlem açık, yurt dışı işlem kapalı.
        card.updatePaymentSettings(true, false);

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Foreign Store");

        // Online olmayan fakat yurt dışından gelen ödeme.
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(true);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(cardTransactionRepository.saveAndFlush(
                any(CardTransaction.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentAuthorizationResponse response =
                virtualCardService.authorizePayment(
                        customerId,
                        cardId,
                        idempotencyKey,
                        request
                );

        // Assert
        assertEquals(
                CardTransactionStatus.DECLINED,
                response.getStatus()
        );

        assertEquals(
                CardTransactionDeclineReason
                        .INTERNATIONAL_TRANSACTIONS_DISABLED,
                response.getDeclineReason()
        );

        // Reddedilen ödeme bakiyeyi değiştirmemelidir.
        assertEquals(
                new BigDecimal("5000.00"),
                card.getBalance()
        );

        verify(cardTransactionRepository)
                .saveAndFlush(any(CardTransaction.class));
    }
    @Test
    void authorizePaymentShouldReturnExistingTransactionForRepeatedRequest() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-008";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);

// Tekrar istek bakiyeyi yeniden düşürmemelidir.
// Kartın güncel bakiyesi 300 TL olarak kalır.
        card.loadBalance(new BigDecimal("300.00"));

        // Daha önce kaydedilmiş ödeme işlemi.
        // İlk ödeme tamamlandığında kartta 400 TL kalmıştı.
// Kartın güncel bakiyesi artık 300 TL olsa da bu işlem
// kendi gerçekleştiği andaki 400 TL bakiyeyi saklar.
        CardTransaction existingTransaction =
                new CardTransaction(
                        CardTransactionType.PAYMENT,
                        CardTransactionStatus.APPROVED,
                        new BigDecimal("100.00"),
                        "Migros",
                        null,
                        idempotencyKey,
                        false,
                        false,
                        new BigDecimal("400.00"),
                        card
                );

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        // Önceki işlemle aynı ödeme bilgileri gönderiliyor.
        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        // Aynı idempotency anahtarıyla eski işlem bulundu.
        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.of(existingTransaction));

        // Act
        PaymentAuthorizationResponse response =
                virtualCardService.authorizePayment(
                        customerId,
                        cardId,
                        idempotencyKey,
                        request
                );

        // Assert: Önceki işlemin sonucu geri dönmelidir.
        assertEquals(
                CardTransactionStatus.APPROVED,
                response.getStatus()
        );

        assertEquals(
                new BigDecimal("100.00"),
                response.getAmount()
        );

        assertEquals("Migros", response.getMerchantName());
        assertEquals(cardId, response.getCardId());

// Tekrar istek bakiyeyi yeniden düşürmemelidir.
// Kartın güncel bakiyesi 300 TL olarak kalır.
        assertEquals(
                new BigDecimal("300.00"),
                card.getBalance()
        );

// Fakat response, kartın güncel bakiyesini değil,
// ilk ödeme tamamlandığında kaydedilen bakiyeyi döndürmelidir.
        assertEquals(
                new BigDecimal("400.00"),
                response.getRemainingBalance()
        );

        // Yeni bir işlem kaydı kesinlikle oluşturulmamalıdır.
        verify(cardTransactionRepository, never())
                .saveAndFlush(any(CardTransaction.class));
    }
    @Test
    void authorizePaymentShouldThrowConflictWhenIdempotencyKeyIsReusedForDifferentPayment() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-009";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);
        card.loadBalance(new BigDecimal("400.00"));

        // Bu anahtar daha önce 100 TL'lik ödeme için kullanılmış.
        CardTransaction existingTransaction =
                new CardTransaction(
                        CardTransactionType.PAYMENT,
                        CardTransactionStatus.APPROVED,
                        new BigDecimal("100.00"),
                        "Migros",
                        null,
                        idempotencyKey,
                        card
                );

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        // Aynı anahtarla bu kez farklı tutar gönderiliyor.
        request.setAmount(new BigDecimal("200.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.of(existingTransaction));

        // Act + Assert
        IdempotencyConflictException exception =
                assertThrows(
                        IdempotencyConflictException.class,
                        () -> virtualCardService.authorizePayment(
                                customerId,
                                cardId,
                                idempotencyKey,
                                request
                        )
                );

        assertEquals(
                "Idempotency anahtarı farklı bir ödeme için kullanılmış",
                exception.getMessage()
        );

        // Çakışma nedeniyle bakiye değişmemelidir.
        assertEquals(
                new BigDecimal("400.00"),
                card.getBalance()
        );

        // Yeni işlem kaydı oluşturulmamalıdır.
        verify(cardTransactionRepository, never())
                .saveAndFlush(any(CardTransaction.class));
    }

    //fail fast yaklaşımı
    @Test
    void authorizePaymentShouldRejectBlankIdempotencyKeyBeforeRepositoryCalls() {

        // Arrange
        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(false);

        // Act + Assert
        InvalidIdempotencyKeyException exception =
                assertThrows(
                        InvalidIdempotencyKeyException.class,
                        () -> virtualCardService.authorizePayment(
                                1L,
                                10L,

                                // Anahtar yalnızca boşluklardan oluşuyor.
                                "   ",
                                request
                        )
                );

        assertEquals(
                "Idempotency anahtarı boş olamaz",
                exception.getMessage()
        );

        // Anahtar geçersiz olduğu için hiçbir repository
        // çağrılmamalıdır.
        verifyNoInteractions(
                customerRepository,
                virtualCardRepository,
                cardTransactionRepository
        );
    }
    @Test
    void authorizePaymentShouldDeclineWhenCardIsExpired() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-010";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Süresi Geçmiş Kart",
                "9999123456789012",

                // Geçmiş bir son kullanma tarihi
                1,
                2020,

                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);

        // Bakiye yeterli olmasına rağmen kartın tarihi geçmiştir.
        card.loadBalance(new BigDecimal("5000.00"));

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(cardTransactionRepository.saveAndFlush(
                any(CardTransaction.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PaymentAuthorizationResponse response =
                virtualCardService.authorizePayment(
                        customerId,
                        cardId,
                        idempotencyKey,
                        request
                );

        // Assert
        assertEquals(
                CardTransactionStatus.DECLINED,
                response.getStatus()
        );

        assertEquals(
                CardTransactionDeclineReason.CARD_EXPIRED,
                response.getDeclineReason()
        );

        // Reddedilen ödeme kart bakiyesini değiştirmemelidir.
        assertEquals(
                new BigDecimal("5000.00"),
                card.getBalance()
        );

        verify(cardTransactionRepository)
                .saveAndFlush(any(CardTransaction.class));
    }
    @Test
    void authorizePaymentShouldConvertDatabaseConflictToIdempotencyConflict() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-concurrent";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);
        card.loadBalance(new BigDecimal("500.00"));

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.empty());

        when(cardTransactionRepository.calculateTotalAmount(
                eq(cardId),
                eq(CardTransactionType.PAYMENT),
                eq(CardTransactionStatus.APPROVED),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(BigDecimal.ZERO);

        // MySQL'deki unique constraint çakışmasını taklit ediyoruz.
        when(cardTransactionRepository.saveAndFlush(
                any(CardTransaction.class)
        )).thenThrow(
                new DataIntegrityViolationException("Duplicate key")
        );

        // Act + Assert
        IdempotencyConflictException exception =
                assertThrows(
                        IdempotencyConflictException.class,
                        () -> virtualCardService.authorizePayment(
                                customerId,
                                cardId,
                                idempotencyKey,
                                request
                        )
                );

        assertEquals(
                "Idempotency anahtarı başka bir ödeme işlemiyle çakıştı",
                exception.getMessage()
        );

        verify(cardTransactionRepository)
                .saveAndFlush(any(CardTransaction.class));
    }
    @Test
    void authorizePaymentShouldThrowConflictWhenOnlineTransactionTypeChanges() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-online-conflict";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);
        card.loadBalance(new BigDecimal("500.00"));

        // Bu anahtar daha önce fiziksel ödeme için kullanılmış.
        CardTransaction existingTransaction =
                new CardTransaction(
                        CardTransactionType.PAYMENT,
                        CardTransactionStatus.APPROVED,
                        new BigDecimal("100.00"),
                        "Migros",
                        null,
                        idempotencyKey,
                        false,
                        false,
                        new BigDecimal("400.00"),
                        card
                );

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Migros");

        // Yeni istek aynı tutar ve mağazaya sahip olsa da
        // bu kez internet ödemesi olarak gönderiliyor.
        request.setOnlineTransaction(true);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.of(existingTransaction));

        // Act + Assert
        IdempotencyConflictException exception =
                assertThrows(
                        IdempotencyConflictException.class,
                        () -> virtualCardService.authorizePayment(
                                customerId,
                                cardId,
                                idempotencyKey,
                                request
                        )
                );

        assertEquals(
                "Idempotency anahtarı farklı bir ödeme için kullanılmış",
                exception.getMessage()
        );

        // Çakışan istek bakiyeyi değiştirmemelidir.
        assertEquals(
                new BigDecimal("500.00"),
                card.getBalance()
        );

        // Çakışan istek için yeni transaction oluşturulmamalıdır.
        verify(cardTransactionRepository, never())
                .saveAndFlush(any(CardTransaction.class));
    }
    @Test
    void authorizePaymentShouldThrowConflictWhenInternationalTransactionTypeChanges() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "payment-international-conflict";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);
        card.loadBalance(new BigDecimal("500.00"));

        // Bu anahtar daha önce yurt içi ödeme için kullanılmış.
        CardTransaction existingTransaction =
                new CardTransaction(
                        CardTransactionType.PAYMENT,
                        CardTransactionStatus.APPROVED,
                        new BigDecimal("100.00"),
                        "Migros",
                        null,
                        idempotencyKey,
                        false,
                        false,
                        new BigDecimal("400.00"),
                        card
                );

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);

        // Aynı anahtar bu kez yurt dışı işlem için kullanılıyor.
        request.setInternationalTransaction(true);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.of(existingTransaction));

        // Act + Assert
        IdempotencyConflictException exception =
                assertThrows(
                        IdempotencyConflictException.class,
                        () -> virtualCardService.authorizePayment(
                                customerId,
                                cardId,
                                idempotencyKey,
                                request
                        )
                );

        assertEquals(
                "Idempotency anahtarı farklı bir ödeme için kullanılmış",
                exception.getMessage()
        );

        // Çakışan istek bakiyeyi değiştirmemelidir.
        assertEquals(
                new BigDecimal("500.00"),
                card.getBalance()
        );

        // Yeni bir transaction oluşturulmamalıdır.
        verify(cardTransactionRepository, never())
                .saveAndFlush(any(CardTransaction.class));
    }
    @Test
    void authorizePaymentShouldSupportLegacyTransactionWithoutPaymentDetails() {

        // Arrange
        Long customerId = 1L;
        Long cardId = 10L;
        String idempotencyKey = "legacy-payment-001";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        VirtualCard card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                12,
                2099,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );

        ReflectionTestUtils.setField(card, "id", cardId);
        card.loadBalance(new BigDecimal("300.00"));

        // Eski constructor kullanılıyor.
        // Bu nedenle yeni ödeme detayları ve bakiye snapshot'ı null olur.
        CardTransaction legacyTransaction =
                new CardTransaction(
                        CardTransactionType.PAYMENT,
                        CardTransactionStatus.APPROVED,
                        new BigDecimal("100.00"),
                        "Migros",
                        null,
                        idempotencyKey,
                        card
                );

        PaymentAuthorizationRequest request =
                new PaymentAuthorizationRequest();

        request.setAmount(new BigDecimal("100.00"));
        request.setMerchantName("Migros");
        request.setOnlineTransaction(false);
        request.setInternationalTransaction(false);

        when(customerRepository.findById(customerId))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.findByIdAndCustomerId(
                cardId,
                customerId
        )).thenReturn(Optional.of(card));

        when(cardTransactionRepository.findByIdempotencyKey(
                idempotencyKey
        )).thenReturn(Optional.of(legacyTransaction));

        // Act
        PaymentAuthorizationResponse response =
                virtualCardService.authorizePayment(
                        customerId,
                        cardId,
                        idempotencyKey,
                        request
                );

        // Assert
        assertEquals(
                CardTransactionStatus.APPROVED,
                response.getStatus()
        );

        // Eski işlemde balanceAfterTransaction bulunmadığı için
        // sistem güvenli fallback olarak kartın mevcut bakiyesini kullanır.
        assertEquals(
                new BigDecimal("300.00"),
                response.getRemainingBalance()
        );

        // Tekrar istek yeni ödeme oluşturmamalıdır.
        verify(cardTransactionRepository, never())
                .saveAndFlush(any(CardTransaction.class));
    }
    /*
    şu ana kadar test edilenler:
Müşteri bulunamadığında kart oluşturulmaması
Geçersiz limitlerle kart kaydedilmemesi
Geçerli kart oluşturma
Kart dondurma ve kart sahipliği kontrolü
Bakiye yükleme ve işlem kaydı
Başarılı ödeme ve bakiye düşümü
Donmuş kart
Süresi geçmiş kart
Yetersiz bakiye
Tek işlem limiti
Günlük limit
Online işlem izni
Yurt dışı işlem izni
Tekrarlanan idempotent istek
Idempotency çakışması
Unique constraint
     */
}