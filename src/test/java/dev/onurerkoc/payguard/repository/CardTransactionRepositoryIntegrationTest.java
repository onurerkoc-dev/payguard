package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.config.MySqlTestcontainersConfiguration;
import dev.onurerkoc.payguard.entity.CardTransaction;
import dev.onurerkoc.payguard.entity.CardTransactionStatus;
import dev.onurerkoc.payguard.entity.CardTransactionType;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.VirtualCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import dev.onurerkoc.payguard.entity.CardTransactionDeclineReason;
import java.time.Instant;
import java.util.List;


@DataJpaTest
@Import(MySqlTestcontainersConfiguration.class)
class CardTransactionRepositoryIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VirtualCardRepository virtualCardRepository;

    @Autowired
    private CardTransactionRepository cardTransactionRepository;

    private VirtualCard card;

    @BeforeEach
    void setUp() {

        Customer customer = new Customer(
                "Onur",
                "Erkoc",
                "payment-integration@test.com"
        );

        Customer savedCustomer =
                customerRepository.saveAndFlush(customer);

        VirtualCard virtualCard = new VirtualCard(
                "Test Kartı",
                "9999000011112222",
                12,
                2030,
                new BigDecimal("500.00"),
                new BigDecimal("1000.00"),
                savedCustomer
        );

        card = virtualCardRepository.saveAndFlush(virtualCard);
    }

    @Test
    void save_shouldPersistTransactionWithIdempotencyKey() {

        CardTransaction transaction = new CardTransaction(
                CardTransactionType.PAYMENT,
                CardTransactionStatus.APPROVED,
                new BigDecimal("100.00"),
                "Migros",
                null,
                "payment-integration-001",
                card
        );

        CardTransaction savedTransaction =
                cardTransactionRepository.saveAndFlush(transaction);

        assertNotNull(savedTransaction.getId());
        assertEquals(
                "payment-integration-001",
                savedTransaction.getIdempotencyKey()
        );
    }
    @Test
    void save_whenIdempotencyKeyAlreadyExists_shouldRejectDuplicateKey() {

        CardTransaction firstTransaction = new CardTransaction(
                CardTransactionType.PAYMENT,
                CardTransactionStatus.APPROVED,
                new BigDecimal("100.00"),
                "Migros",
                null,
                "same-payment-key",
                card
        );

        CardTransaction secondTransaction = new CardTransaction(
                CardTransactionType.PAYMENT,
                CardTransactionStatus.APPROVED,
                new BigDecimal("200.00"),
                "Teknosa",
                null,
                "same-payment-key",
                card
        );

        cardTransactionRepository.saveAndFlush(firstTransaction);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> cardTransactionRepository.saveAndFlush(secondTransaction)
        );
    }
    @Test
    void calculateTotalAmount_shouldSumOnlyApprovedPayments() {

        Instant startTime = Instant.now().minusSeconds(1);

        CardTransaction firstApprovedPayment = new CardTransaction(
                CardTransactionType.PAYMENT,
                CardTransactionStatus.APPROVED,
                new BigDecimal("100.00"),
                "Migros",
                null,
                "sum-test-key-1",
                card
        );

        CardTransaction secondApprovedPayment = new CardTransaction(
                CardTransactionType.PAYMENT,
                CardTransactionStatus.APPROVED,
                new BigDecimal("200.00"),
                "Teknosa",
                null,
                "sum-test-key-2",
                card
        );

        CardTransaction declinedPayment = new CardTransaction(
                CardTransactionType.PAYMENT,
                CardTransactionStatus.DECLINED,
                new BigDecimal("400.00"),
                "Amazon",
                CardTransactionDeclineReason.INSUFFICIENT_BALANCE,
                "sum-test-key-3",
                card
        );

        CardTransaction balanceLoad = new CardTransaction(
                CardTransactionType.BALANCE_LOAD,
                CardTransactionStatus.APPROVED,
                new BigDecimal("500.00"),
                null,
                null,
                card
        );

        cardTransactionRepository.saveAllAndFlush(
                List.of(
                        firstApprovedPayment,
                        secondApprovedPayment,
                        declinedPayment,
                        balanceLoad
                )
        );

        Instant endTime = Instant.now().plusSeconds(1);

        BigDecimal total = cardTransactionRepository.calculateTotalAmount(
                card.getId(),
                CardTransactionType.PAYMENT,
                CardTransactionStatus.APPROVED,
                startTime,
                endTime
        );

        assertEquals(
                0,
                new BigDecimal("300.00").compareTo(total)
        );
    }
}