package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.entity.CardTransaction;
import dev.onurerkoc.payguard.entity.CardTransactionStatus;
import dev.onurerkoc.payguard.entity.CardTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;


// Yönetilen entity: CardTransaction
// Primary key tipi: Long
public interface CardTransactionRepository
        extends JpaRepository<CardTransaction, Long> {
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM CardTransaction t
        WHERE t.card.id = :cardId
          AND t.type = :type
          AND t.status = :status
          AND t.createdAt >= :startTime
          AND t.createdAt < :endTime
        """)
    /*
    URL'deki 5
→ @PathVariable
→ Service'teki cardId
→ Repository metodu
→ @Param
→ Query'deki :cardId
→ MySQL sorgusu
     */
    BigDecimal calculateTotalAmount(
            @Param("cardId") Long cardId,
            @Param("type") CardTransactionType type,
            @Param("status") CardTransactionStatus status,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
}