package dev.onurerkoc.payguard.security;

import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class PayGuardUserDetailsServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private PayGuardUserDetailsService userDetailsService;

    @Test
    void loadUserByUsername_whenAccountExists_shouldReturnUserDetails() {

        // GIVEN: Veritabanında bulunan müşteriyi ve hesabı hazırlıyoruz.
        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        ReflectionTestUtils.setField(customer, "id", 7L);

        UserAccount account = new UserAccount(
                "onur@example.com",
                "test-password-hash",
                customer
        );

        ReflectionTestUtils.setField(account, "id", 3L);

        when(userAccountRepository.findByEmail("onur@example.com"))
                .thenReturn(Optional.of(account));

        // WHEN: E-postayı büyük harf ve kenar boşluklarıyla gönderiyoruz.
        UserDetails result = userDetailsService.loadUserByUsername(
                " ONUR@EXAMPLE.COM "
        );

        // THEN: Bizim özel kullanıcı bilgisi sınıfımız dönmeli.
        PayGuardUserDetails principal = assertInstanceOf(
                PayGuardUserDetails.class,
                result
        );

        assertEquals("onur@example.com", principal.getUsername());
        assertEquals("test-password-hash", principal.getPassword());
        assertEquals(3L, principal.getAccountId());
        assertEquals(7L, principal.getCustomerId());
        assertTrue(principal.isEnabled());

        // USER rolü, Spring Security'nin ROLE_USER yetkisine dönüşmeli.
        assertEquals(1, principal.getAuthorities().size());

        assertTrue(
                principal.getAuthorities().stream()
                        .anyMatch(authority ->
                                authority.getAuthority().equals("ROLE_USER")
                        )
        );

        // Repository'ye normalize edilmiş e-posta gönderilmiş olmalı.
        verify(userAccountRepository).findByEmail("onur@example.com");
    }
    @Test
    void loadUserByUsername_whenAccountDoesNotExist_shouldThrowException() {

        // GIVEN: Gönderilen e-postaya ait hesap bulunmuyor.
        when(userAccountRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        // WHEN + THEN: Kullanıcı bilgisi oluşturmak yerine hata verilmeli.
        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(
                        " MISSING@EXAMPLE.COM "
                )
        );

        assertEquals(
                "Giriş bilgileri geçersiz",
                exception.getMessage()
        );

        // Bulunamayan hesapta da e-posta normalize edilmeli.
        verify(userAccountRepository).findByEmail("missing@example.com");
    }
    @Test
    void loadUserByUsername_whenAccountIsDisabled_shouldReturnDisabledUserDetails() {

        // GIVEN: Veritabanında bulunan fakat pasif bir hesap.
        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        UserAccount account = new UserAccount(
                "onur@example.com",
                "test-password-hash",
                customer
        );

        // Test için hesabın pasif durumunu hazırlıyoruz.
        ReflectionTestUtils.setField(account, "enabled", false);

        when(userAccountRepository.findByEmail("onur@example.com"))
                .thenReturn(Optional.of(account));

        // WHEN: Hesabın giriş bilgilerini yüklüyoruz.
        UserDetails result = userDetailsService.loadUserByUsername(
                "onur@example.com"
        );

        // THEN: Pasiflik bilgisi kaybolmadan aktarılmalı.
        assertFalse(result.isEnabled());

        verify(userAccountRepository).findByEmail("onur@example.com");
    }
}