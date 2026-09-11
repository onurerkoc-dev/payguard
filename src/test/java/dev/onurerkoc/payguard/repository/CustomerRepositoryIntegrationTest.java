package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.config.MySqlTestcontainersConfiguration;
import dev.onurerkoc.payguard.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;



@DataJpaTest
@Import(MySqlTestcontainersConfiguration.class)
class CustomerRepositoryIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void save_shouldPersistCustomerInMySql() {

        Customer customer = new Customer(
                "Onur",
                "Erkoc",
                "integration@test.com"
        );

        Customer savedCustomer =
                customerRepository.saveAndFlush(customer);

        assertNotNull(savedCustomer.getId());
        assertTrue(
                customerRepository.existsByEmail("integration@test.com")
        );
    }
    @Test
    void save_whenEmailAlreadyExists_shouldRejectDuplicateEmail() {

        Customer firstCustomer = new Customer(
                "Onur",
                "Erkoc",
                "duplicate@test.com"
        );

        Customer secondCustomer = new Customer(
                "Ahmet",
                "Yilmaz",
                "duplicate@test.com"
        );

        customerRepository.saveAndFlush(firstCustomer);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> customerRepository.saveAndFlush(secondCustomer)
        );
    }
}