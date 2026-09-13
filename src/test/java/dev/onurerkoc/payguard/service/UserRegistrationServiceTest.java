package dev.onurerkoc.payguard.service;

import dev.onurerkoc.payguard.dto.UserRegistrationRequest;
import dev.onurerkoc.payguard.dto.UserRegistrationResponse;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.entity.UserRole;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import dev.onurerkoc.payguard.exception.EmailAlreadyExistsException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import dev.onurerkoc.payguard.exception.InvalidPasswordException;



@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserRegistrationService userRegistrationService;

    @Test
    void register_whenRequestIsValid_shouldCreateCustomerAndAccount() {

        // GIVEN: Geçerli kayıt isteği.
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setFirstName(" Onur ");
        request.setLastName(" Erkoç ");
        request.setEmail("ONUR@EXAMPLE.COM");
        request.setPassword("GuvenliSifre123!");

        String normalizedEmail = "onur@example.com";
        String passwordHash = "test-password-hash";

        when(userAccountRepository.existsByEmail(normalizedEmail))
                .thenReturn(false);

        when(customerRepository.existsByEmail(normalizedEmail))
                .thenReturn(false);

        when(passwordEncoder.encode("GuvenliSifre123!"))
                .thenReturn(passwordHash);

        // Gerçek veritabanı ID üretmediği için bu davranışı taklit ediyoruz.
        when(customerRepository.saveAndFlush(any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer customer = invocation.getArgument(0);
                    ReflectionTestUtils.setField(customer, "id", 7L);
                    return customer;
                });

        when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
                .thenAnswer(invocation -> {
                    UserAccount account = invocation.getArgument(0);
                    ReflectionTestUtils.setField(account, "id", 3L);
                    return account;
                });

        // WHEN: Gerçek kayıt servisini çalıştırıyoruz.
        UserRegistrationResponse response =
                userRegistrationService.register(request);

        // THEN: Kayıt cevabı doğru olmalı.
        assertEquals(3L, response.getAccountId());
        assertEquals(7L, response.getCustomerId());
        assertEquals(normalizedEmail, response.getEmail());

        // Kaydedilmek üzere gönderilen müşteriyi yakalıyoruz.
        ArgumentCaptor<Customer> customerCaptor =
                ArgumentCaptor.forClass(Customer.class);

        verify(customerRepository)
                .saveAndFlush(customerCaptor.capture());

        Customer savedCustomer = customerCaptor.getValue();

        assertEquals("Onur", savedCustomer.getFirstName());
        assertEquals("Erkoç", savedCustomer.getLastName());
        assertEquals(normalizedEmail, savedCustomer.getEmail());

        // Kaydedilmek üzere gönderilen hesabı yakalıyoruz.
        ArgumentCaptor<UserAccount> accountCaptor =
                ArgumentCaptor.forClass(UserAccount.class);

        verify(userAccountRepository).saveAndFlush(accountCaptor.capture());

        UserAccount savedAccount = accountCaptor.getValue();

        assertEquals(normalizedEmail, savedAccount.getEmail());
        assertEquals(passwordHash, savedAccount.getPasswordHash());
        assertEquals(UserRole.USER, savedAccount.getRole());
        assertTrue(savedAccount.isEnabled());

        // Hesap, oluşturulan müşteri nesnesine bağlanmalı.
        assertSame(savedCustomer, savedAccount.getCustomer());

        // Encoder'a açık şifrenin değiştirilmeden iletildiğini doğruluyoruz.
        verify(passwordEncoder).encode("GuvenliSifre123!");
    }
    @Test
    void register_whenAccountEmailAlreadyExists_shouldRejectRegistration() {

        // GIVEN: Kullanıcının gönderdiği e-posta hesaplarda zaten mevcut.
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setFirstName("Onur");
        request.setLastName("Erkoç");
        request.setEmail("ONUR@EXAMPLE.COM");
        request.setPassword("GuvenliSifre123!");

        when(userAccountRepository.existsByEmail("onur@example.com"))
                .thenReturn(true);

        // WHEN + THEN: Yeni kayıt kabul edilmemeli.
        EmailAlreadyExistsException exception = assertThrows(
                EmailAlreadyExistsException.class,
                () -> userRegistrationService.register(request)
        );

        assertEquals(
                "Bu email adresi zaten kullanılıyor",
                exception.getMessage()
        );

        // Yeni hesap kaydı oluşturulmamalı.
        verify(userAccountRepository, never())
                .saveAndFlush(any(UserAccount.class));

        // Müşteri tarafına ve şifreleme işlemine hiç geçilmemeli.
        verifyNoInteractions(customerRepository, passwordEncoder);
    }
    @Test
    void register_whenCustomerEmailAlreadyExists_shouldRejectRegistration() {

        // GIVEN: E-posta hesaplarda yok, müşteri kayıtlarında mevcut.
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setFirstName("Onur");
        request.setLastName("Erkoç");
        request.setEmail("ONUR@EXAMPLE.COM");
        request.setPassword("GuvenliSifre123!");

        when(userAccountRepository.existsByEmail("onur@example.com"))
                .thenReturn(false);

        when(customerRepository.existsByEmail("onur@example.com"))
                .thenReturn(true);

        // WHEN + THEN: Aynı e-postayla yeni müşteri ve hesap oluşturulmamalı.
        EmailAlreadyExistsException exception = assertThrows(
                EmailAlreadyExistsException.class,
                () -> userRegistrationService.register(request)
        );

        assertEquals(
                "Bu email adresi zaten kullanılıyor",
                exception.getMessage()
        );

        // İki repository'ye de yeni kayıt gönderilmemeli.
        verify(customerRepository, never())
                .saveAndFlush(any(Customer.class));

        verify(userAccountRepository, never())
                .saveAndFlush(any(UserAccount.class));

        // Kayıt reddedildiği için şifre hashleme işlemine geçilmemeli.
        verifyNoInteractions(passwordEncoder);
    }
    @Test
    void register_whenPasswordExceedsUtf8ByteLimit_shouldRejectRegistration() {

        // GIVEN: 37 karakter fakat UTF-8 olarak 74 byte uzunluğunda şifre.
        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setFirstName("Onur");
        request.setLastName("Erkoç");
        request.setEmail("onur@example.com");
        request.setPassword("ş".repeat(37));

        // E-posta kontrolleri geçmeli; hata şifre sınırından gelmeli.
        when(userAccountRepository.existsByEmail("onur@example.com"))
                .thenReturn(false);

        when(customerRepository.existsByEmail("onur@example.com"))
                .thenReturn(false);

        // WHEN + THEN: Byte sınırını aşan şifre reddedilmeli.
        InvalidPasswordException exception = assertThrows(
                InvalidPasswordException.class,
                () -> userRegistrationService.register(request)
        );

        assertEquals(
                "Şifre UTF-8 olarak en fazla 72 byte olabilir",
                exception.getMessage()
        );

        // Geçersiz şifre encoder'a gönderilmemeli.
        verifyNoInteractions(passwordEncoder);

        // Müşteri veya hesap kaydı oluşturulmamalı.
        verify(customerRepository, never())
                .saveAndFlush(any(Customer.class));

        verify(userAccountRepository, never())
                .saveAndFlush(any(UserAccount.class));
    }
    @Test
    void register_whenPasswordIsExactly72Utf8Bytes_shouldAcceptRegistration() {

        // GIVEN: 36 tane ş karakteri, UTF-8 olarak tam 72 byte eder.
        String rawPassword = "ş".repeat(36);
        String passwordHash = "boundary-test-hash";

        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setFirstName("Onur");
        request.setLastName("Erkoç");
        request.setEmail("onur@example.com");
        request.setPassword(rawPassword);

        when(userAccountRepository.existsByEmail("onur@example.com"))
                .thenReturn(false);

        when(customerRepository.existsByEmail("onur@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode(rawPassword))
                .thenReturn(passwordHash);

        // Veritabanının ID üretmesini taklit ediyoruz.
        when(customerRepository.saveAndFlush(any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer customer = invocation.getArgument(0);
                    ReflectionTestUtils.setField(customer, "id", 7L);
                    return customer;
                });

        when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
                .thenAnswer(invocation -> {
                    UserAccount account = invocation.getArgument(0);
                    ReflectionTestUtils.setField(account, "id", 3L);
                    return account;
                });

        // WHEN: Tam sınırdaki şifreyle kayıt yapıyoruz.
        UserRegistrationResponse response =
                userRegistrationService.register(request);

        // THEN: Kayıt başarılı olmalı.
        assertEquals(3L, response.getAccountId());
        assertEquals(7L, response.getCustomerId());

        // Açık şifre encoder'a değiştirilmeden iletilmeli.
        verify(passwordEncoder).encode(rawPassword);

        ArgumentCaptor<UserAccount> accountCaptor =
                ArgumentCaptor.forClass(UserAccount.class);

        verify(userAccountRepository)
                .saveAndFlush(accountCaptor.capture());

        // Hesapta açık şifre değil, encoder'ın ürettiği hash bulunmalı.
        assertEquals(
                passwordHash,
                accountCaptor.getValue().getPasswordHash()
        );
    }
    @Test
    void register_whenConcurrentRequestCausesDuplicateEmail_shouldTranslateException() {

        UserRegistrationRequest request =
                new UserRegistrationRequest();

        request.setFirstName("Onur");
        request.setLastName("Erkoç");
        request.setEmail("ONUR@EXAMPLE.COM");
        request.setPassword("GuvenliSifre123!");

        String normalizedEmail =
                "onur@example.com";

        when(userAccountRepository.existsByEmail(normalizedEmail))
                .thenReturn(false);

        when(customerRepository.existsByEmail(normalizedEmail))
                .thenReturn(false);

        when(passwordEncoder.encode("GuvenliSifre123!"))
                .thenReturn("test-password-hash");

        /*
         * Ön kontrol bittikten hemen sonra başka bir istek
         * aynı e-postayı kaydetmiş gibi davranıyoruz.
         */
        when(customerRepository.saveAndFlush(
                any(Customer.class)
        )).thenThrow(
                new DataIntegrityViolationException(
                        "Duplicate email"
                )
        );

        EmailAlreadyExistsException exception =
                assertThrows(
                        EmailAlreadyExistsException.class,
                        () -> userRegistrationService.register(request)
                );

        assertEquals(
                "Bu email adresi zaten kullanılıyor",
                exception.getMessage()
        );

        verify(customerRepository)
                .saveAndFlush(any(Customer.class));

        /*
         * Customer kaydı başarısız olduğundan UserAccount
         * kaydetme aşamasına geçilmemeli.
         */
        verify(userAccountRepository, never())
                .saveAndFlush(any(UserAccount.class));
    }

}