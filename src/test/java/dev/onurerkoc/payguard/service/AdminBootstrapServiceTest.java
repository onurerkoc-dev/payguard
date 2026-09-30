package dev.onurerkoc.payguard.service;

import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.entity.UserRole;
import dev.onurerkoc.payguard.exception.InvalidPasswordException;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    private static ValidatorFactory validatorFactory;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private CustomerRepository customerRepository;
    private PasswordEncoder passwordEncoder;
    private AdminBootstrapService adminBootstrapService;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        // Gerçek BCrypt ile üretilen hash'in girişte kullanılabileceğini doğrularız.
        passwordEncoder = spy(new BCryptPasswordEncoder());
        adminBootstrapService = new AdminBootstrapService(
                userAccountRepository, customerRepository,
                passwordEncoder, validatorFactory.getValidator()
        );
    }

    @ParameterizedTest
    @MethodSource("validPasswords")
    void createAdmin_whenValid_shouldNormalizeEmailAndSaveHash(String rawPassword) {
        when(userAccountRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.empty());

        boolean created = adminBootstrapService.createAdmin(
                " ADMIN@EXAMPLE.COM ", rawPassword
        );

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).saveAndFlush(captor.capture());
        UserAccount admin = captor.getValue();
        assertTrue(created);
        assertEquals("admin@example.com", admin.getEmail());
        assertEquals(UserRole.ADMIN, admin.getRole());
        assertTrue(admin.isEnabled());
        assertNull(admin.getCustomer());
        assertNotEquals(rawPassword, admin.getPasswordHash());
        assertTrue(passwordEncoder.matches(rawPassword, admin.getPasswordHash()));
        verify(passwordEncoder).encode(rawPassword);
    }

    static Stream<String> validPasswords() {
        return Stream.of("a".repeat(12), "a".repeat(72), "é".repeat(36), " GuvenliSifre123! ");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid", "admin@", "@example.com"})
    void createAdmin_whenEmailInvalid_shouldRejectBeforeDatabaseAccess(String email) {
        assertThrows(IllegalArgumentException.class,
                () -> adminBootstrapService.createAdmin(email, "GuvenliSifre123!"));
        verifyNoInteractions(userAccountRepository, customerRepository, passwordEncoder);
    }

    @Test
    void createAdmin_whenEmailTooLong_shouldRejectBeforeDatabaseAccess() {
        String email = "a".repeat(60) + "@" + "b".repeat(60) + "." + "c".repeat(30);
        assertThrows(IllegalArgumentException.class,
                () -> adminBootstrapService.createAdmin(email, "GuvenliSifre123!"));
        verifyNoInteractions(userAccountRepository, customerRepository, passwordEncoder);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"            ", "short"})
    void createAdmin_whenPasswordInvalid_shouldRejectBeforeDatabaseAccess(String password) {
        assertThrows(IllegalArgumentException.class,
                () -> adminBootstrapService.createAdmin("admin@example.com", password));
        verifyNoInteractions(userAccountRepository, customerRepository, passwordEncoder);
    }

    @Test
    void createAdmin_whenPasswordTooManyCharacters_shouldRejectBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> adminBootstrapService.createAdmin("admin@example.com", "a".repeat(73)));
        verifyNoInteractions(userAccountRepository, customerRepository, passwordEncoder);
    }

    @Test
    void createAdmin_whenPasswordTooManyBytes_shouldRejectBeforeDatabaseAccess() {
        assertThrows(InvalidPasswordException.class,
                () -> adminBootstrapService.createAdmin("admin@example.com", "é".repeat(37)));
        verifyNoInteractions(userAccountRepository, customerRepository, passwordEncoder);
    }

    @Test
    void createAdmin_whenAdminExists_shouldKeepItsOriginalPasswordAndSkipSave() {
        UserAccount existingAdmin = UserAccount.createAdmin("admin@example.com", "original-hash");
        when(userAccountRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(existingAdmin));

        boolean created = adminBootstrapService.createAdmin("ADMIN@EXAMPLE.COM", "DifferentPassword123!");

        assertFalse(created);
        assertEquals("original-hash", existingAdmin.getPasswordHash());
        assertEquals(UserRole.ADMIN, existingAdmin.getRole());
        verify(userAccountRepository, never()).saveAndFlush(any());
        verifyNoInteractions(customerRepository, passwordEncoder);
    }

    @Test
    void createAdmin_whenUserExists_shouldNeverPromoteIt() {
        Customer customer = new Customer("Onur", "Erkoç", "admin@example.com");
        UserAccount user = new UserAccount("admin@example.com", "original-hash", customer);
        when(userAccountRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class,
                () -> adminBootstrapService.createAdmin("admin@example.com", "GuvenliSifre123!"));

        assertEquals(UserRole.USER, user.getRole());
        assertEquals("original-hash", user.getPasswordHash());
        assertSame(customer, user.getCustomer());
        verify(userAccountRepository, never()).saveAndFlush(any());
        verifyNoInteractions(customerRepository, passwordEncoder);
    }

    @Test
    void createAdmin_whenAdminDisabled_shouldNeverReactivateIt() {
        UserAccount admin = UserAccount.createAdmin("admin@example.com", "original-hash");
        ReflectionTestUtils.setField(admin, "enabled", false);
        when(userAccountRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(admin));

        assertThrows(IllegalStateException.class,
                () -> adminBootstrapService.createAdmin("admin@example.com", "GuvenliSifre123!"));

        assertFalse(admin.isEnabled());
        assertEquals("original-hash", admin.getPasswordHash());
        verify(userAccountRepository, never()).saveAndFlush(any());
        verifyNoInteractions(customerRepository, passwordEncoder);
    }

    @Test
    void createAdmin_whenCustomerEmailExists_shouldRejectCreation() {
        when(userAccountRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.empty());
        when(customerRepository.existsByEmail("admin@example.com")).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> adminBootstrapService.createAdmin("admin@example.com", "GuvenliSifre123!"));

        verify(userAccountRepository, never()).saveAndFlush(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void createAdmin_whenDatabaseRejectsConcurrentDuplicate_shouldNotReportSuccess() {
        when(userAccountRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.empty());
        when(userAccountRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertThrows(DataIntegrityViolationException.class,
                () -> adminBootstrapService.createAdmin("admin@example.com", "GuvenliSifre123!"));
    }
}
