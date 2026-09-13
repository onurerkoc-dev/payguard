package dev.onurerkoc.payguard.security;

import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.repository.CardTransactionRepository;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.VirtualCardRepository;
import dev.onurerkoc.payguard.service.VirtualCardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import dev.onurerkoc.payguard.dto.VirtualCardSummaryResponse;

import java.util.List;
import java.util.Optional;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(
        VirtualCardServiceAuthorizationTest.MethodSecurityTestConfig.class
)
class VirtualCardServiceAuthorizationTest {

    @Autowired
    private VirtualCardService virtualCardService;

    @MockitoBean
    private VirtualCardRepository virtualCardRepository;

    @MockitoBean
    private CustomerRepository customerRepository;

    @MockitoBean
    private CardTransactionRepository cardTransactionRepository;

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void getCardsByCustomerId_whenUserDoesNotOwnCustomer_shouldDenyAccess() {

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

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        SecurityContext securityContext =
                SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        // WHEN & THEN:
        // Müşteri 7, müşteri 99'un kartlarını listeleyemez.
        assertThrows(
                AccessDeniedException.class,
                () -> virtualCardService.getCardsByCustomerId(99L)
        );

        // Sınıf seviyesindeki @PreAuthorize,
        // metot çalışmadan erişimi reddetmeli.
        verifyNoInteractions(
                virtualCardRepository,
                customerRepository,
                cardTransactionRepository
        );
    }

    @Configuration
    @EnableMethodSecurity
    @Import({
            VirtualCardService.class,
            CustomerAccessPolicy.class
    })
    static class MethodSecurityTestConfig {
    }
    @Test
    void getCardsByCustomerId_whenUserOwnsCustomer_shouldReturnCards() {

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

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        SecurityContext securityContext =
                SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(customerRepository.findById(7L))
                .thenReturn(Optional.of(customer));

        // Bu testte kart eşleme davranışını değil,
        // yetkilendirmeyi test ettiğimiz için boş liste yeterli.
        when(virtualCardRepository
                .findAllByCustomerIdOrderByIdAsc(7L))
                .thenReturn(List.of());

        // WHEN: Kullanıcı kendi kartlarını istiyor.
        List<VirtualCardSummaryResponse> responses =
                virtualCardService.getCardsByCustomerId(7L);

        // THEN: Güvenlik erişime izin vermeli ve metot çalışmalı.
        assertTrue(responses.isEmpty());

        verify(customerRepository).findById(7L);

        verify(virtualCardRepository)
                .findAllByCustomerIdOrderByIdAsc(7L);

        // Bu listeleme işleminde işlem geçmişi repository'si kullanılmamalı.
        verifyNoInteractions(cardTransactionRepository);
    }
    @Test
    void everyPublicBusinessMethod_shouldDeclareCustomerIdParameter() {

        Arrays.stream(VirtualCardService.class.getDeclaredMethods())

                // Yalnızca dışarıdan çağrılabilen public metotları inceliyoruz.
                .filter(method ->
                        Modifier.isPublic(method.getModifiers())
                )

                // Java derleyicisinin oluşturabileceği yardımcı metotları almıyoruz.
                .filter(method -> !method.isSynthetic())

                .forEach(method -> {

                    boolean hasCustomerIdParameter =
                            Arrays.stream(method.getParameters())
                                    .anyMatch(parameter ->
                                            parameter.getName()
                                                    .equals("customerId")
                                    );

                    assertTrue(
                            hasCustomerIdParameter,
                            () -> method.getName()
                                    + " public metodu customerId "
                                    + "parametresi taşımalıdır"
                    );
                });
    }
}