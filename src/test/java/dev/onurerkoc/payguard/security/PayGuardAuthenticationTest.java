package dev.onurerkoc.payguard.security;

import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.security.authentication.DisabledException;

@ExtendWith(MockitoExtension.class)
class PayGuardAuthenticationTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    private PasswordEncoder passwordEncoder;
    private DaoAuthenticationProvider authenticationProvider;

    @BeforeEach
    void setUp() {

        // Mock değil; gerçek BCrypt kullanıyoruz.
        passwordEncoder = new BCryptPasswordEncoder();

        // Gerçek kullanıcı yükleme servisimiz.
        PayGuardUserDetailsService userDetailsService =
                new PayGuardUserDetailsService(userAccountRepository);

        // Gerçek şifre ve hesap durumu doğrulayıcısı.
        authenticationProvider =
                new DaoAuthenticationProvider(userDetailsService);

        authenticationProvider.setPasswordEncoder(passwordEncoder);
    }

    @Test
    void authenticate_whenPasswordIsCorrect_shouldAuthenticateUser() {

        // GIVEN: Hesapta gerçek BCrypt hash'i bulunuyor.
        String rawPassword = "GuvenliSifre123!";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        ReflectionTestUtils.setField(customer, "id", 7L);

        UserAccount account = new UserAccount(
                "onur@example.com",
                passwordEncoder.encode(rawPassword),
                customer
        );

        ReflectionTestUtils.setField(account, "id", 3L);

        when(userAccountRepository.findByEmail("onur@example.com"))
                .thenReturn(Optional.of(account));

        // Henüz doğrulanmamış giriş isteği.
        UsernamePasswordAuthenticationToken loginRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "onur@example.com",
                        rawPassword
                );

        // WHEN: Gerçek sağlayıcıyla kimlik doğrulaması yapıyoruz.
        Authentication result =
                authenticationProvider.authenticate(loginRequest);

        // THEN: Doğru şifre kabul edilmeli.
        assertTrue(result.isAuthenticated());

        PayGuardUserDetails principal = assertInstanceOf(
                PayGuardUserDetails.class,
                result.getPrincipal()
        );

        assertEquals("onur@example.com", principal.getUsername());
        assertEquals(3L, principal.getAccountId());
        assertEquals(7L, principal.getCustomerId());

        assertTrue(
                result.getAuthorities().stream()
                        .anyMatch(authority ->
                                authority.getAuthority().equals("ROLE_USER")
                        )
        );

        verify(userAccountRepository).findByEmail("onur@example.com");
    }
    @Test
    void authenticate_whenPasswordIsIncorrect_shouldRejectAuthentication() {

        // GIVEN: Hesapta doğru şifrenin BCrypt hash'i bulunuyor.
        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        UserAccount account = new UserAccount(
                "onur@example.com",
                passwordEncoder.encode("GuvenliSifre123!"),
                customer
        );

        when(userAccountRepository.findByEmail("onur@example.com"))
                .thenReturn(Optional.of(account));

        // Kullanıcı kayıtlı şifreden farklı bir şifre gönderiyor.
        UsernamePasswordAuthenticationToken loginRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "onur@example.com",
                        "YanlisSifre123!"
                );

        // WHEN + THEN: Gerçek doğrulayıcı yanlış şifreyi reddetmeli.
        assertThrows(
                BadCredentialsException.class,
                () -> authenticationProvider.authenticate(loginRequest)
        );

        // Hesap bulunmuş olmasına rağmen şifre uyuşmadığı için giriş reddedildi.
        verify(userAccountRepository).findByEmail("onur@example.com");
    }
    @Test
    void authenticate_whenAccountIsDisabled_shouldRejectAuthentication() {

        // GIVEN: Şifresi doğru fakat hesabı pasif olan kullanıcı.
        String rawPassword = "GuvenliSifre123!";

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        UserAccount account = new UserAccount(
                "onur@example.com",
                passwordEncoder.encode(rawPassword),
                customer
        );

        // Test verisini pasif hesap durumuna getiriyoruz.
        ReflectionTestUtils.setField(account, "enabled", false);

        when(userAccountRepository.findByEmail("onur@example.com"))
                .thenReturn(Optional.of(account));

        UsernamePasswordAuthenticationToken loginRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "onur@example.com",
                        rawPassword
                );

        // WHEN + THEN: Doğru şifre hesabın pasifliğini aşamamalı.
        assertThrows(
                DisabledException.class,
                () -> authenticationProvider.authenticate(loginRequest)
        );

        verify(userAccountRepository).findByEmail("onur@example.com");
    }
    @Test
    void authenticate_whenAccountDoesNotExist_shouldRejectWithBadCredentials() {

        // GIVEN: Bu e-postaya ait hesap bulunmuyor.
        when(userAccountRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        UsernamePasswordAuthenticationToken loginRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "missing@example.com",
                        "GuvenliSifre123!"
                );

        // WHEN + THEN: Hesabın bulunmadığını açıklayan özel hata
        // yerine genel kimlik doğrulama hatası verilmeli.
        assertThrows(
                BadCredentialsException.class,
                () -> authenticationProvider.authenticate(loginRequest)
        );

        verify(userAccountRepository).findByEmail("missing@example.com");
    }
}