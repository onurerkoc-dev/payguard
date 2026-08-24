package dev.onurerkoc.payguard.service;


import dev.onurerkoc.payguard.dto.CustomerCreateRequest;
import dev.onurerkoc.payguard.dto.CustomerResponse;
import dev.onurerkoc.payguard.exception.EmailAlreadyExistsException;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import dev.onurerkoc.payguard.entity.Customer;


@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;

    }

    @Transactional
    public CustomerResponse createCustomer(CustomerCreateRequest request) {

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
        Customer savedCustomer = customerRepository.save(customer);
        return new CustomerResponse(
                savedCustomer.getId(),
                savedCustomer.getFirstName(),
                savedCustomer.getLastName(),
                savedCustomer.getEmail()
        );
    }
}
