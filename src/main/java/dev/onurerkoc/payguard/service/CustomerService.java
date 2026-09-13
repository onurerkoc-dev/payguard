package dev.onurerkoc.payguard.service;

import dev.onurerkoc.payguard.dto.CustomerCreateRequest;
import dev.onurerkoc.payguard.dto.CustomerResponse;
import dev.onurerkoc.payguard.dto.CustomerUpdateRequest;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.exception.CustomerHasVirtualCardsException;
import dev.onurerkoc.payguard.exception.CustomerNotFoundException;
import dev.onurerkoc.payguard.exception.EmailAlreadyExistsException;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import dev.onurerkoc.payguard.repository.VirtualCardRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final VirtualCardRepository virtualCardRepository;
    private final UserAccountRepository userAccountRepository;

    public CustomerService(
            CustomerRepository customerRepository,
            VirtualCardRepository virtualCardRepository,
            UserAccountRepository userAccountRepository) {

        this.customerRepository = customerRepository;
        this.virtualCardRepository = virtualCardRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CustomerResponse createCustomer(
            CustomerCreateRequest request) {

        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "Bu email adresi zaten kullanılıyor"
            );
        }

        Customer customer = new Customer(
                request.getFirstName(),
                request.getLastName(),
                request.getEmail()
        );

        Customer savedCustomer =
                customerRepository.save(customer);

        return mapToResponse(savedCustomer);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<CustomerResponse> getAllCustomers() {

        List<Customer> customers =
                customerRepository.findAll();

        List<CustomerResponse> responses =
                new ArrayList<>();

        for (Customer customer : customers) {
            responses.add(mapToResponse(customer));
        }

        return responses;
    }

    @PreAuthorize(
            "@customerAccessPolicy.isOwner(authentication, #id)"
    )
    public CustomerResponse getCustomerById(Long id) {

        Customer customer = findCustomerById(id);

        return mapToResponse(customer);
    }

    @PreAuthorize(
            "@customerAccessPolicy.isOwner(authentication, #id)"
    )
    @Transactional
    public CustomerResponse updateCustomer(
            Long id,
            CustomerUpdateRequest request) {

        Customer customer = findCustomerById(id);

        customer.setFirstName(request.getFirstName().trim());
        customer.setLastName(request.getLastName().trim());

        /*
         * E-posta burada değiştirilmez.
         * Customer.email ve UserAccount.email aynı kalmalıdır.
         */
        Customer updatedCustomer =
                customerRepository.save(customer);

        return mapToResponse(updatedCustomer);
    }

    @PreAuthorize(
            "@customerAccessPolicy.isOwner(authentication, #id)"
    )
    @Transactional
    public void deleteCustomer(Long id) {

        Customer customer = findCustomerById(id);

        if (virtualCardRepository.existsByCustomerId(id)) {
            throw new CustomerHasVirtualCardsException(
                    "Sanal kartı bulunan müşteri silinemez"
            );
        }

        /*
         * user_accounts.customer_id, customers.id alanına bağlıdır.
         * Bu yüzden önce giriş hesabını silip veritabanına uygularız.
         */
        userAccountRepository.findByCustomerId(id)
                .ifPresent(account -> {

                    // Önce kullanıcı hesabını sil.
                    userAccountRepository.delete(account);

                    // Silme SQL'ini Customer silinmeden önce çalıştır.
                    userAccountRepository.flush();
                });

        customerRepository.delete(customer);
    }

    private Customer findCustomerById(Long id) {

        return customerRepository.findById(id)
                .orElseThrow(() ->
                        new CustomerNotFoundException(
                                "Müşteri bulunamadı: " + id
                        )
                );
    }

    private CustomerResponse mapToResponse(Customer customer) {

        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail()
        );
    }
}