package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

/*
POST /api/customers
        ↓
CustomerController
        ↓
@Valid ile CustomerCreateRequest kontrolü
        ↓
CustomerService.createCustomer(...)
        ↓
Email daha önce kullanılmış mı?
        ↓
CustomerRepository.save(...)
        ↓
MySQL
        ↓
CustomerResponse
        ↓
JSON cevap
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    boolean existsByEmail(String email);
}
