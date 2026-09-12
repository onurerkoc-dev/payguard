package dev.onurerkoc.payguard.service;

import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import dev.onurerkoc.payguard.dto.UserRegistrationRequest;
import dev.onurerkoc.payguard.dto.UserRegistrationResponse;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.exception.EmailAlreadyExistsException;
import dev.onurerkoc.payguard.exception.InvalidPasswordException;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;


@Service
public class UserRegistrationService {

    private final CustomerRepository customerRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(
            CustomerRepository customerRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder) {

        this.customerRepository = customerRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }
    @Transactional
    public UserRegistrationResponse register(
            UserRegistrationRequest request) {

        // Giriş e-postasını tutarlı biçimde saklarız.
        String normalizedEmail = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        // Hem hesap hem müşteri tarafında e-postanın kullanımını kontrol ederiz.
        if (userAccountRepository.existsByEmail(normalizedEmail)
                || customerRepository.existsByEmail(normalizedEmail)) {

            throw new EmailAlreadyExistsException(
                    "Bu email adresi zaten kullanılıyor"
            );
        }

        // Şifreyi trimlemiyoruz veya küçük harfe çevirmiyoruz.
        String rawPassword = request.getPassword();

        // BCrypt sınırı karakter değil, UTF-8 byte uzunluğu üzerindendir.
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {

            throw new InvalidPasswordException(
                    "Şifre UTF-8 olarak en fazla 72 byte olabilir"
            );
        }

        // Açık şifre yerine yalnızca hash'i kaydedeceğiz.
        String passwordHash = passwordEncoder.encode(rawPassword);

        // Kayıt isteği için yeni müşteri profilini oluştururuz.
        Customer customer = new Customer(
                request.getFirstName().trim(),
                request.getLastName().trim(),
                normalizedEmail
        );

        Customer savedCustomer = customerRepository.save(customer);

        // Constructor rolü USER, hesabı aktif olarak ayarlar.
        UserAccount account = new UserAccount(
                normalizedEmail,
                passwordHash,
                savedCustomer
        );

        UserAccount savedAccount = userAccountRepository.save(account);

        // Şifre veya şifre hash'i response'a eklenmez.
        return new UserRegistrationResponse(
                savedAccount.getId(),
                savedCustomer.getId(),
                savedAccount.getEmail()
        );
    }
}