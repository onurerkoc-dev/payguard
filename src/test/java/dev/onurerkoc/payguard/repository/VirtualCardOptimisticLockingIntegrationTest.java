package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.config.MySqlTestcontainersConfiguration;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.VirtualCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Import(MySqlTestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VirtualCardOptimisticLockingIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VirtualCardRepository virtualCardRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long cardId;

    @BeforeEach
    void setUp() {

        transactionTemplate = new TransactionTemplate(transactionManager);

        cardId = transactionTemplate.execute(status -> {

            Customer customer = customerRepository.saveAndFlush(
                    new Customer(
                            "Onur",
                            "Erkoc",
                            "optimistic-lock@test.com"
                    )
            );

            VirtualCard card = virtualCardRepository.saveAndFlush(
                    new VirtualCard(
                            "Optimistic Lock Kartı",
                            "9999000099990000",
                            12,
                            2030,
                            new BigDecimal("500.00"),
                            new BigDecimal("1000.00"),
                            customer
                    )
            );

            return card.getId();
        });
    }

    @Test
    void update_whenCardWasChanged_shouldRejectStaleCard() {

        VirtualCard staleCard = transactionTemplate.execute(
                status -> virtualCardRepository.findById(cardId)
                        .orElseThrow()
        );

        transactionTemplate.executeWithoutResult(status -> {

            VirtualCard currentCard =
                    virtualCardRepository.findById(cardId)
                            .orElseThrow();

            currentCard.loadBalance(new BigDecimal("100.00"));

            virtualCardRepository.saveAndFlush(currentCard);
        });

        staleCard.loadBalance(new BigDecimal("50.00"));

        assertThrows(
                OptimisticLockingFailureException.class,
                () -> transactionTemplate.executeWithoutResult(
                        status -> virtualCardRepository.saveAndFlush(staleCard)
                )
        );
    }
    @AfterEach
    void cleanUp() {

        transactionTemplate.executeWithoutResult(status -> {
            virtualCardRepository.deleteAll();
            customerRepository.deleteAll();
        });
    }
}