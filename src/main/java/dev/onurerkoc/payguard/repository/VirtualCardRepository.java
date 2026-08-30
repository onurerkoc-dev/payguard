package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.entity.VirtualCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VirtualCardRepository 
        extends JpaRepository<VirtualCard, Long> {

    boolean existsByCardNumber(String cardNumber);
    List<VirtualCard> findAllByCustomerIdOrderByIdAsc(Long customerId);
    Optional<VirtualCard> findByIdAndCustomerId(
            Long cardId,
            Long customerId
    );
    boolean existsByCustomerId(Long customerId);
}