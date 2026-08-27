package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.entity.VirtualCard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualCardRepository 
        extends JpaRepository<VirtualCard, Long> {

    boolean existsByCardNumber(String cardNumber);
}