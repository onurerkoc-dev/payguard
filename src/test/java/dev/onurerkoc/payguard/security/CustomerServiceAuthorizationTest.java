package dev.onurerkoc.payguard.security;

import dev.onurerkoc.payguard.dto.CustomerResponse;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.service.CustomerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import dev.onurerkoc.payguard.repository.VirtualCardRepository;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import dev.onurerkoc.payguard.entity.UserRole;
import dev.onurerkoc.payguard.dto.CustomerCreateRequest;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(
        CustomerServiceAuthorizationTest.MethodSecurityTestConfig.class
)
class CustomerServiceAuthorizationTest {

    @Autowired
    private CustomerService customerService;

    @MockitoBean
    private CustomerRepository customerRepository;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private VirtualCardRepository virtualCardRepository;
    @AfterEach
    void clearSecurityContext() {

        // Bir testte oluşturulan kullanıcı bilgisi
        // sonraki teste taşınmasın.
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCustomerById_whenUserOwnsCustomer_shouldReturnCustomer() {

        // GIVEN: ID değeri 7 olan müşteri.
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

        // Spring Security'ye giriş yapmış kullanıcıyı yerleştiriyoruz.
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

        // WHEN: Kullanıcı kendi müşteri kaydını istiyor.
        CustomerResponse response =
                customerService.getCustomerById(7L);

        // THEN: @PreAuthorize erişime izin vermeli.
        assertEquals(7L, response.getId());
        assertEquals("onur@example.com", response.getEmail());

        verify(customerRepository).findById(7L);
    }

    @Configuration
    @EnableMethodSecurity
    @Import({
            CustomerService.class,
            CustomerAccessPolicy.class
    })
    static class MethodSecurityTestConfig {

        // Test için sadece gerekli Spring bileşenleri yüklenir.
    }
    @Test
    void getCustomerById_whenUserDoesNotOwnCustomer_shouldDenyAccess() {

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
        // Müşteri 7 olarak giriş yapan kullanıcı,
        // müşteri 99'un bilgilerini istemeye çalışıyor.
        assertThrows(
                AccessDeniedException.class,
                () -> customerService.getCustomerById(99L)
        );

        // @PreAuthorize metot çalışmadan erişimi reddettiği için
        // repository katmanına hiçbir çağrı yapılmamalı.
        verifyNoInteractions(
                customerRepository,
                virtualCardRepository
        );
    }
    @Test
    void updateCustomer_whenUserDoesNotOwnCustomer_shouldDenyAccess() {

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
        // Müşteri 7, müşteri 99'u güncellemeye çalışıyor.
        assertThrows(
                AccessDeniedException.class,
                () -> customerService.updateCustomer(
                        99L,
                        null
                )
        );

        // Yetkilendirme metottan önce çalıştığı için
        // hiçbir repository çağrılmamalı.
        verifyNoInteractions(
                customerRepository,
                virtualCardRepository
        );
    }
    @Test
    void deleteCustomer_whenUserDoesNotOwnCustomer_shouldDenyAccess() {

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

        // Müşteri 7, müşteri 99'u silmeye çalışıyor.
        assertThrows(
                AccessDeniedException.class,
                () -> customerService.deleteCustomer(99L)
        );

        // Yetkilendirme reddedildiği için servis gövdesi çalışmamalı.
        verifyNoInteractions(
                customerRepository,
                virtualCardRepository
        );
    }
    @Test
    void getAllCustomers_whenUserHasUserRole_shouldDenyAccess() {

        // GIVEN: Normal USER rolüne sahip bir müşteri hesabı.
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
        // USER rolündeki kullanıcı bütün müşterileri listeleyemez.
        assertThrows(
                AccessDeniedException.class,
                () -> customerService.getAllCustomers()
        );

        // Yetki kontrolü servis metodundan önce erişimi reddetmeli.
        verifyNoInteractions(
                customerRepository,
                virtualCardRepository
        );
    }
    @Test
    void getAllCustomers_whenUserHasAdminRole_shouldReturnCustomers() {

        // GIVEN: Sistemde bulunan örnek bir müşteri.
        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        ReflectionTestUtils.setField(customer, "id", 7L);

        // Admin hesabının bir müşteriye bağlı olması gerekmiyor.
        UserAccount adminAccount = new UserAccount(
                "admin@payguard.com",
                "test-password-hash",
                customer
        );

        // Kayıt sırasında varsayılan rol USER olduğu için
        // testte hesabın rolünü ADMIN olarak ayarlıyoruz.
        ReflectionTestUtils.setField(
                adminAccount,
                "role",
                UserRole.ADMIN
        );

        PayGuardUserDetails principal =
                new PayGuardUserDetails(adminAccount);

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

        when(customerRepository.findAll())
                .thenReturn(List.of(customer));

        // WHEN: Admin bütün müşterileri istiyor.
        List<CustomerResponse> responses =
                customerService.getAllCustomers();

        // THEN: Yetkilendirme izin vermeli ve müşteri dönmeli.
        assertEquals(1, responses.size());
        assertEquals(7L, responses.getFirst().getId());
        assertEquals(
                "onur@example.com",
                responses.getFirst().getEmail()
        );

        verify(customerRepository).findAll();
    }
    @Test
    void createCustomer_whenUserHasUserRole_shouldDenyAccess() {

        // GIVEN: Normal USER rolüne sahip hesap.
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
        // Normal kullanıcı yönetim amaçlı müşteri oluşturamaz.
        assertThrows(
                AccessDeniedException.class,
                () -> customerService.createCustomer(null)
        );

        // Yetkilendirme metottan önce erişimi reddettiği için
        // repository katmanına gidilmemeli.
        verifyNoInteractions(
                customerRepository,
                virtualCardRepository
        );
    }
    @Test
    void createCustomer_whenUserHasAdminRole_shouldCreateCustomer() {

        // GIVEN: ADMIN rolüne dönüştürülecek kullanıcı hesabı.
        Customer adminCustomer = new Customer(
                "Sistem",
                "Yöneticisi",
                "admin@payguard.com"
        );

        ReflectionTestUtils.setField(adminCustomer, "id", 1L);

        UserAccount adminAccount = new UserAccount(
                "admin@payguard.com",
                "test-password-hash",
                adminCustomer
        );

        ReflectionTestUtils.setField(
                adminAccount,
                "role",
                UserRole.ADMIN
        );

        PayGuardUserDetails principal =
                new PayGuardUserDetails(adminAccount);

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

        // Oluşturulacak müşteri isteğini mock'luyoruz.
        CustomerCreateRequest request =
                mock(CustomerCreateRequest.class);

        when(request.getFirstName())
                .thenReturn("Ayşe");

        when(request.getLastName())
                .thenReturn("Yılmaz");

        when(request.getEmail())
                .thenReturn("ayse@example.com");

        when(customerRepository.existsByEmail("ayse@example.com"))
                .thenReturn(false);

        Customer savedCustomer = new Customer(
                "Ayşe",
                "Yılmaz",
                "ayse@example.com"
        );

        ReflectionTestUtils.setField(savedCustomer, "id", 8L);

        when(customerRepository.save(any(Customer.class)))
                .thenReturn(savedCustomer);

        // WHEN: ADMIN yeni müşteri oluşturuyor.
        CustomerResponse response =
                customerService.createCustomer(request);

        // THEN: Yetkilendirme izin vermeli ve kayıt oluşturulmalı.
        assertEquals(8L, response.getId());
        assertEquals("Ayşe", response.getFirstName());
        assertEquals("Yılmaz", response.getLastName());
        assertEquals("ayse@example.com", response.getEmail());

        verify(customerRepository)
                .existsByEmail("ayse@example.com");

        verify(customerRepository)
                .save(any(Customer.class));
    }
}