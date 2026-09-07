package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.entity.CardTransaction;
import dev.onurerkoc.payguard.entity.CardTransactionStatus;
import dev.onurerkoc.payguard.entity.CardTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;


// Yönetilen entity: CardTransaction
// Primary key tipi: Long
public interface CardTransactionRepository
        extends JpaRepository<CardTransaction, Long> {

    // Idempotency anahtarıyla daha önce oluşturulmuş işlemi bulur.
    Optional<CardTransaction> findByIdempotencyKey(
            String idempotencyKey
    );
    // Belirtilen karta ait işlemleri sayfa sayfa getirir.
// En yeni işlemler listenin başında bulunur.
    Page<CardTransaction> findAllByCardIdOrderByCreatedAtDescIdDesc(
            Long cardId,
            Pageable pageable
    );
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM CardTransaction t
        WHERE t.card.id = :cardId
          AND t.type = :type
          AND t.status = :status
          AND t.createdAt >= :startTime
          AND t.createdAt < :endTime
        """)
    BigDecimal calculateTotalAmount(
            @Param("cardId") Long cardId,
            @Param("type") CardTransactionType type,
            @Param("status") CardTransactionStatus status,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
}