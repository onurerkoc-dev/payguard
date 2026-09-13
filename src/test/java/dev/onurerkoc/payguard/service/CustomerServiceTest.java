package dev.onurerkoc.payguard.service;

import dev.onurerkoc.payguard.dto.CustomerUpdateRequest;
import dev.onurerkoc.payguard.exception.CustomerHasVirtualCardsException;
import dev.onurerkoc.payguard.exception.CustomerNotFoundException;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.VirtualCardRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.onurerkoc.payguard.dto.CustomerCreateRequest;
import dev.onurerkoc.payguard.dto.CustomerResponse;
import dev.onurerkoc.payguard.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import org.mockito.InOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import dev.onurerkoc.payguard.exception.EmailAlreadyExistsException;

import java.util.List;
import java.util.Optional;


@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private VirtualCardRepository virtualCardRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @InjectMocks
    private CustomerService customerService;


    @Test
    void createCustomer_whenEmailIsAvailable_shouldSaveAndReturnResponse() {

        // GIVEN: API'den gelmiş gibi bir müşteri oluşturma isteği hazırlıyoruz.
        CustomerCreateRequest request = new CustomerCreateRequest();
        request.setFirstName("Onur");
        request.setLastName("Erkoç");
        request.setEmail("onur@example.com");

        // Bu e-posta daha önce kullanılmamış gibi davranmasını söylüyoruz.
        when(customerRepository.existsByEmail("onur@example.com"))
                .thenReturn(false);

        // Gerçek MySQL, save sırasında müşteriye ID verirdi.
        // Sahte repository kullandığımız için bu davranışı burada taklit ediyoruz.
        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation -> {

                    Customer customer = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            customer,
                            "id",
                            1L
                    );

                    return customer;
                });

        // WHEN: Test ettiğimiz gerçek service metodunu çalıştırıyoruz.
        CustomerResponse response =
                customerService.createCustomer(request);

        // THEN: Dönen cevabın beklediğimiz bilgilerden oluştuğunu kontrol ediyoruz.
        assertEquals(1L, response.getId());
        assertEquals("Onur", response.getFirstName());
        assertEquals("Erkoç", response.getLastName());
        assertEquals("onur@example.com", response.getEmail());

        // Repository metotlarının gerçekten çağrıldığını doğruluyoruz.
        verify(customerRepository)
                .existsByEmail("onur@example.com");

        verify(customerRepository)
                .save(any(Customer.class));
    }
    @Test
    void createCustomer_whenEmailAlreadyExists_shouldThrowException() {

        // GIVEN: Aynı e-posta ile gelen müşteri isteğini hazırlıyoruz.
        CustomerCreateRequest request = new CustomerCreateRequest();
        request.setFirstName("Onur");
        request.setLastName("Erkoç");
        request.setEmail("onur@example.com");

        // Repository bu e-postanın daha önce kullanıldığını söylüyor.
        when(customerRepository.existsByEmail("onur@example.com"))
                .thenReturn(true);

        // WHEN: createCustomer çalıştığında exception bekliyoruz.
        EmailAlreadyExistsException exception =
                assertThrows(
                        EmailAlreadyExistsException.class,
                        () -> customerService.createCustomer(request)
                );

        // THEN: Doğru hata mesajının üretildiğini kontrol ediyoruz.
        assertEquals(
                "Bu email adresi zaten kullanılıyor",
                exception.getMessage()
        );

        verify(customerRepository)
                .existsByEmail("onur@example.com");

        // E-posta kullanıldığı için save metoduna kesinlikle gidilmemeli.
        verify(customerRepository, never())
                .save(any(Customer.class));
    }
    @Test
    void getCustomerById_whenCustomerExists_shouldReturnResponse() {

        // GIVEN: Veritabanında bulunduğunu varsaydığımız müşteriyi hazırlıyoruz.
        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        ReflectionTestUtils.setField(
                customer,
                "id",
                1L
        );

        // findById(1L) çağrılınca müşteri bulunmuş gibi davranacak.
        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        // WHEN: Service üzerinden müşteriyi istiyoruz.
        CustomerResponse response =
                customerService.getCustomerById(1L);

        // THEN: Entity'nin doğru response DTO'ya dönüştüğünü kontrol ediyoruz.
        assertEquals(1L, response.getId());
        assertEquals("Onur", response.getFirstName());
        assertEquals("Erkoç", response.getLastName());
        assertEquals("onur@example.com", response.getEmail());

        verify(customerRepository).findById(1L);
    }
    @Test
    void getCustomerById_whenCustomerDoesNotExist_shouldThrowException() {

        // GIVEN: ID'si 99 olan müşteri veritabanında bulunmuyor.
        when(customerRepository.findById(99L))
                .thenReturn(Optional.empty());

        // WHEN: Service metodu çalıştırıldığında exception bekliyoruz.
        CustomerNotFoundException exception =
                assertThrows(
                        CustomerNotFoundException.class,
                        () -> customerService.getCustomerById(99L)
                );

        // THEN: Doğru hata mesajının oluştuğunu kontrol ediyoruz.
        assertEquals(
                "Müşteri bulunamadı: 99",
                exception.getMessage()
        );

        verify(customerRepository).findById(99L);
    }
    @Test
    void updateCustomer_whenRequestIsValid_shouldUpdateNamesAndKeepEmail() {

        // GIVEN: Sistemde kayıtlı müşteri.
        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        ReflectionTestUtils.setField(customer, "id", 1L);

        CustomerUpdateRequest request =
                new CustomerUpdateRequest();

        request.setFirstName(" Onur Can ");
        request.setLastName(" Erkoç ");

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(customerRepository.save(any(Customer.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        // WHEN
        CustomerResponse response =
                customerService.updateCustomer(1L, request);

        // THEN: Ad ve soyadı güncellenmeli.
        assertEquals("Onur Can", response.getFirstName());
        assertEquals("Erkoç", response.getLastName());

        // E-posta bu işlem tarafından değiştirilmemeli.
        assertEquals(
                "onur@example.com",
                response.getEmail()
        );

        assertEquals(
                "onur@example.com",
                customer.getEmail()
        );

        verify(customerRepository).findById(1L);
        verify(customerRepository).save(customer);
    }
    @Test
    void deleteCustomer_whenCustomerHasVirtualCards_shouldThrowException() {

        // GIVEN: Veritabanında bulunan müşteriyi hazırlıyoruz.
        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        ReflectionTestUtils.setField(customer, "id", 1L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        // Müşteriye bağlı en az bir sanal kart olduğunu taklit ediyoruz.
        when(virtualCardRepository.existsByCustomerId(1L))
                .thenReturn(true);

        // WHEN: Müşteri silinmeye çalışıldığında exception bekliyoruz.
        CustomerHasVirtualCardsException exception =
                assertThrows(
                        CustomerHasVirtualCardsException.class,
                        () -> customerService.deleteCustomer(1L)
                );

        // THEN: Doğru iş kuralı mesajının oluştuğunu kontrol ediyoruz.
        assertEquals(
                "Sanal kartı bulunan müşteri silinemez",
                exception.getMessage()
        );

        verify(customerRepository).findById(1L);
        verify(virtualCardRepository).existsByCustomerId(1L);

        // Müşterinin kesinlikle silinmediğini doğruluyoruz.
        verify(customerRepository, never()).delete(customer);
    }
    @Test
    void deleteCustomer_whenCustomerHasAccountAndNoCards_shouldDeleteBoth() {

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        ReflectionTestUtils.setField(customer, "id", 1L);

        UserAccount account = new UserAccount(
                "onur@example.com",
                "test-password-hash",
                customer
        );

        ReflectionTestUtils.setField(account, "id", 10L);

        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.existsByCustomerId(1L))
                .thenReturn(false);

        when(userAccountRepository.findByCustomerId(1L))
                .thenReturn(Optional.of(account));

        // WHEN
        customerService.deleteCustomer(1L);

        // THEN
        verify(customerRepository).findById(1L);
        verify(virtualCardRepository).existsByCustomerId(1L);
        verify(userAccountRepository).findByCustomerId(1L);

        /*
         * Önce UserAccount, ardından Customer silinmelidir.
         */
        InOrder deletionOrder = inOrder(
                userAccountRepository,
                customerRepository
        );

        deletionOrder.verify(userAccountRepository)
                .delete(account);

        deletionOrder.verify(userAccountRepository)
                .flush();

        deletionOrder.verify(customerRepository)
                .delete(customer);
    }
    @Test
    void getAllCustomers_whenCustomersExist_shouldReturnResponseList() {

        // GIVEN: Veritabanında bulunduğunu varsaydığımız iki müşteri.
        Customer firstCustomer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        Customer secondCustomer = new Customer(
                "Ayşe",
                "Yılmaz",
                "ayse@example.com"
        );

        ReflectionTestUtils.setField(firstCustomer, "id", 1L);
        ReflectionTestUtils.setField(secondCustomer, "id", 2L);

        when(customerRepository.findAll())
                .thenReturn(List.of(firstCustomer, secondCustomer));

        // WHEN: Bütün müşterileri istiyoruz.
        List<CustomerResponse> responses =
                customerService.getAllCustomers();

        // THEN: İki müşterinin de doğru DTO'ya dönüştürüldüğünü kontrol ediyoruz.
        assertEquals(2, responses.size());

        assertEquals(1L, responses.get(0).getId());
        assertEquals("Onur", responses.get(0).getFirstName());
        assertEquals("Erkoç", responses.get(0).getLastName());
        assertEquals("onur@example.com", responses.get(0).getEmail());

        assertEquals(2L, responses.get(1).getId());
        assertEquals("Ayşe", responses.get(1).getFirstName());
        assertEquals("Yılmaz", responses.get(1).getLastName());
        assertEquals("ayse@example.com", responses.get(1).getEmail());

        verify(customerRepository).findAll();
    }
    @Test
    void getAllCustomers_whenNoCustomersExist_shouldReturnEmptyList() {

        // GIVEN: Veritabanında hiç müşteri bulunmuyor.
        when(customerRepository.findAll())
                .thenReturn(List.of());

        // WHEN: Bütün müşterileri istiyoruz.
        List<CustomerResponse> responses =
                customerService.getAllCustomers();

        // THEN: Gelen listenin boş olduğunu doğruluyoruz.
        assertTrue(responses.isEmpty());

        verify(customerRepository).findAll();
    }
    @Test
    void updateCustomer_whenCustomerDoesNotExist_shouldThrowException() {

        CustomerUpdateRequest request =
                new CustomerUpdateRequest();

        request.setFirstName("Onur");
        request.setLastName("Erkoç");

        when(customerRepository.findById(99L))
                .thenReturn(Optional.empty());

        CustomerNotFoundException exception =
                assertThrows(
                        CustomerNotFoundException.class,
                        () -> customerService.updateCustomer(
                                99L,
                                request
                        )
                );

        assertEquals(
                "Müşteri bulunamadı: 99",
                exception.getMessage()
        );

        verify(customerRepository).findById(99L);

        verify(customerRepository, never())
                .save(any(Customer.class));
    }
    @Test
    void deleteCustomer_whenCustomerDoesNotExist_shouldThrowException() {

        // GIVEN: ID'si 99 olan müşteri bulunmuyor.
        when(customerRepository.findById(99L))
                .thenReturn(Optional.empty());

        // WHEN: Müşteri silinmek istendiğinde exception bekliyoruz.
        CustomerNotFoundException exception =
                assertThrows(
                        CustomerNotFoundException.class,
                        () -> customerService.deleteCustomer(99L)
                );

        // THEN: Doğru hata mesajının oluştuğunu kontrol ediyoruz.
        assertEquals(
                "Müşteri bulunamadı: 99",
                exception.getMessage()
        );

        verify(customerRepository).findById(99L);


        verifyNoInteractions(
                virtualCardRepository,
                userAccountRepository
        );

        // Silme işlemi kesinlikle yapılmamalı.
        verify(customerRepository, never())
                .delete(any(Customer.class));
    }
    @Test
    void deleteCustomer_whenCustomerHasNoAccount_shouldDeleteCustomer() {

        Customer customer = new Customer(
                "Ayşe",
                "Yılmaz",
                "ayse@example.com"
        );

        ReflectionTestUtils.setField(customer, "id", 2L);

        when(customerRepository.findById(2L))
                .thenReturn(Optional.of(customer));

        when(virtualCardRepository.existsByCustomerId(2L))
                .thenReturn(false);

        // Admin tarafından oluşturulmuş hesapsız müşteri olabilir.
        when(userAccountRepository.findByCustomerId(2L))
                .thenReturn(Optional.empty());

        customerService.deleteCustomer(2L);

        verify(userAccountRepository)
                .findByCustomerId(2L);

        verify(userAccountRepository, never())
                .delete(any(UserAccount.class));

        verify(userAccountRepository, never())
                .flush();

        verify(customerRepository).delete(customer);
    }
}
