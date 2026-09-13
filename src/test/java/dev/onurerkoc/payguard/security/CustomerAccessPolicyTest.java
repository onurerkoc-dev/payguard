package dev.onurerkoc.payguard.security;

import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAccessPolicyTest {

    @Mock
    private Authentication authentication;

    private final CustomerAccessPolicy customerAccessPolicy =
            new CustomerAccessPolicy();

    @Test
    void isOwner_whenCustomerIdMatchesPrincipal_shouldReturnTrue() {

        // GIVEN: Hesaba bağlı müşterinin ID'si 7.
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

        PayGuardUserDetails principal =
                new PayGuardUserDetails(account);

        // Kullanıcının giriş yaptığı güvenlik durumunu taklit ediyoruz.
        when(authentication.isAuthenticated())
                .thenReturn(true);

        when(authentication.getPrincipal())
                .thenReturn(principal);

        // WHEN: Kullanıcı kendi müşteri ID'sine erişmek istiyor.
        boolean result = customerAccessPolicy.isOwner(
                authentication,
                7L
        );

        // THEN: Sahiplik kontrolü izin vermeli.
        assertTrue(result);
    }

    @Test
    void isOwner_whenCustomerIdDoesNotMatchPrincipal_shouldReturnFalse() {

        // GIVEN: Giriş yapan hesap müşteri 7'ye bağlı.
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

        PayGuardUserDetails principal =
                new PayGuardUserDetails(account);

        when(authentication.isAuthenticated())
                .thenReturn(true);

        when(authentication.getPrincipal())
                .thenReturn(principal);

        // WHEN: Kullanıcı müşteri 99'a erişmeye çalışıyor.
        boolean result = customerAccessPolicy.isOwner(
                authentication,
                99L
        );

        // THEN: ID'ler eşleşmediği için erişim reddedilmeli.
        assertFalse(result);
    }

    @Test
    void isOwner_whenUserIsNotAuthenticated_shouldReturnFalse() {

        // GIVEN: Authentication var fakat kullanıcı doğrulanmamış.
        when(authentication.isAuthenticated())
                .thenReturn(false);

        // WHEN: Kullanıcı müşteri 7'ye erişmek istiyor.
        boolean result = customerAccessPolicy.isOwner(
                authentication,
                7L
        );

        // THEN: Kimlik doğrulanmadığı için erişim reddedilmeli.
        assertFalse(result);

        // Kullanıcı doğrulanmadığı için principal'a bakılmamalı.
        verify(authentication, never()).getPrincipal();
    }

    @Test
    void isOwner_whenAuthenticationIsNull_shouldReturnFalse() {

        // WHEN: Giriş bilgisi olmadan erişim kontrolü yapılıyor.
        boolean result = customerAccessPolicy.isOwner(
                null,
                7L
        );

        // THEN: Hata vermek yerine erişim güvenli biçimde reddedilmeli.
        assertFalse(result);
    }

    @Test
    void isOwner_whenPrincipalIsNotPayGuardUserDetails_shouldReturnFalse() {

        // GIVEN: Kullanıcı doğrulanmış görünüyor.
        when(authentication.isAuthenticated())
                .thenReturn(true);

        // Fakat principal PayGuard'a ait kullanıcı tipi değil.
        when(authentication.getPrincipal())
                .thenReturn("anonymousUser");

        // WHEN: Sahiplik kontrolü yapılıyor.
        boolean result = customerAccessPolicy.isOwner(
                authentication,
                7L
        );

        // THEN: Tanınmayan principal ile erişim reddedilmeli.
        assertFalse(result);
    }
    @Test
    void isOwner_whenCustomerIdIsNull_shouldReturnFalse() {

        // GIVEN: Kullanıcı doğrulanmış durumda.
        when(authentication.isAuthenticated())
                .thenReturn(true);

        // WHEN: Kontrol edilecek müşteri ID'si null geliyor.
        boolean result = customerAccessPolicy.isOwner(
                authentication,
                null
        );

        // THEN: Eksik müşteri ID'siyle erişim reddedilmeli.
        assertFalse(result);

        // Müşteri ID'si yoksa principal'ı incelemeye gerek olmamalı.
        verify(authentication, never()).getPrincipal();
    }
}