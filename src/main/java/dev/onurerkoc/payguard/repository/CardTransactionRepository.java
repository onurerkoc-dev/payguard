package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.entity.CardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;


// Yönetilen entity: CardTransaction
// Primary key tipi: Long
public interface CardTransactionRepository
        extends JpaRepository<CardTransaction, Long> {
}