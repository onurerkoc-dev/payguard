package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.entity.VirtualCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VirtualCardRepository 
        extends JpaRepository<VirtualCard, Long> {

    boolean existsByCardNumber(String cardNumber);
    List<VirtualCard> findAllByCustomerIdOrderByIdAsc(Long customerId);
}