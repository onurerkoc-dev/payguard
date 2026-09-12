package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, Long> {

    // Giriş e-postasına ait hesabı bulur.
    Optional<UserAccount> findByEmail(String email);

    // Kayıt sırasında e-postanın kullanımda olup olmadığını kontrol eder.
    boolean existsByEmail(String email);
}