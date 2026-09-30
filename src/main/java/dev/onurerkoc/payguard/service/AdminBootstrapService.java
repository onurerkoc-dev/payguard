package dev.onurerkoc.payguard.service;

import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.entity.UserRole;
import dev.onurerkoc.payguard.exception.InvalidPasswordException;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AdminBootstrapService {

    private final UserAccountRepository userAccountRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public AdminBootstrapService(
            UserAccountRepository userAccountRepository,
            CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder,
            Validator validator) {
        this.userAccountRepository = userAccountRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Transactional
    public boolean createAdmin(String email, String rawPassword) {
        String normalizedEmail = email == null
                ? null : email.trim().toLowerCase(Locale.ROOT);

        // Kurulum bilgileri HTTP isteğinden gelmediği için burada doğrulanır.
        Credentials credentials = new Credentials(normalizedEmail, rawPassword);
        if (!validator.validate(credentials).isEmpty()) {
            throw new IllegalArgumentException(
                    "Admin kurulumu için geçerli e-posta ve 12-72 karakterlik şifre gerekir"
            );
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidPasswordException(
                    "Şifre UTF-8 olarak en fazla 72 byte olabilir"
            );
        }

        UserAccount existingAccount = userAccountRepository
                .findByEmail(normalizedEmail).orElse(null);
        if (existingAccount != null) {
            if (existingAccount.getRole() != UserRole.ADMIN) {
                throw new IllegalStateException(
                        "Admin kurulum e-postası normal bir kullanıcı hesabına ait"
                );
            }
            if (!existingAccount.isEnabled()) {
                throw new IllegalStateException(
                        "Devre dışı admin hesabı kurulumla etkinleştirilemez"
                );
            }
            // Tekrar çalıştırma mevcut adminin şifresini veya rolünü değiştirmez.
            return false;
        }
        if (customerRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException(
                    "Admin kurulum e-postası mevcut bir müşteri profiline ait"
            );
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        UserAccount admin = UserAccount.createAdmin(normalizedEmail, passwordHash);
        // Unique constraint eşzamanlı aynı e-postalı kayıtları da engeller.
        userAccountRepository.saveAndFlush(admin);
        return true;
    }

    private static class Credentials {
        @NotBlank
        @Email
        @Size(max = 150)
        private final String email;

        @NotBlank
        @Size(min = 12, max = 72)
        private final String password;

        private Credentials(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }
}
