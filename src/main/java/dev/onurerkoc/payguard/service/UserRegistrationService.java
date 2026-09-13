package dev.onurerkoc.payguard.service;

import dev.onurerkoc.payguard.dto.UserRegistrationRequest;
import dev.onurerkoc.payguard.dto.UserRegistrationResponse;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.exception.EmailAlreadyExistsException;
import dev.onurerkoc.payguard.exception.InvalidPasswordException;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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

        String normalizedEmail = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        /*
         * Bu kontrol kullanıcıya hızlı ve anlaşılır cevap verir.
         * Fakat tek başına eşzamanlı isteklere karşı yeterli değildir.
         */
        if (userAccountRepository.existsByEmail(normalizedEmail)
                || customerRepository.existsByEmail(normalizedEmail)) {

            throw new EmailAlreadyExistsException(
                    "Bu email adresi zaten kullanılıyor"
            );
        }

        String rawPassword = request.getPassword();

        if (rawPassword
                .getBytes(StandardCharsets.UTF_8)
                .length > 72) {

            throw new InvalidPasswordException(
                    "Şifre UTF-8 olarak en fazla 72 byte olabilir"
            );
        }

        String passwordHash =
                passwordEncoder.encode(rawPassword);

        Customer customer = new Customer(
                request.getFirstName().trim(),
                request.getLastName().trim(),
                normalizedEmail
        );

        try {
            /*
             * saveAndFlush kullanarak unique constraint kontrolünün
             * metot tamamlanmadan MySQL tarafından yapılmasını sağlarız.
             */
            Customer savedCustomer =
                    customerRepository.saveAndFlush(customer);

            UserAccount account = new UserAccount(
                    normalizedEmail,
                    passwordHash,
                    savedCustomer
            );

            UserAccount savedAccount =
                    userAccountRepository.saveAndFlush(account);

            return new UserRegistrationResponse(
                    savedAccount.getId(),
                    savedCustomer.getId(),
                    savedAccount.getEmail()
            );

        } catch (DataIntegrityViolationException exception) {

            /*
             * İki istek aynı anda ön kontrolden geçse bile
             * veritabanındaki unique constraint bunlardan birini reddeder.
             *
             * Teknik veritabanı hatasını kullanıcıya kontrollü
             * 409 Conflict cevabı olarak dönüştürüyoruz.
             */
            throw new EmailAlreadyExistsException(
                    "Bu email adresi zaten kullanılıyor"
            );
        }
    }
}