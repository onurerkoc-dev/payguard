package dev.onurerkoc.payguard.repository;

import dev.onurerkoc.payguard.config.MySqlTestcontainersConfiguration;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertFalse;

@DataJpaTest
@Import({
        MySqlTestcontainersConfiguration.class,
        CustomerService.class
})
class CustomerAccountLifecycleIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CustomerService customerService;

    @Test
    void deleteCustomer_whenAccountExists_shouldDeleteAccountAndCustomer() {

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "account-delete@integration.test"
        );

        Customer savedCustomer =
                customerRepository.saveAndFlush(customer);

        UserAccount account = new UserAccount(
                "account-delete@integration.test",
                "test-password-hash",
                savedCustomer
        );

        UserAccount savedAccount =
                userAccountRepository.saveAndFlush(account);

        Long customerId = savedCustomer.getId();
        Long accountId = savedAccount.getId();

        // WHEN
        customerService.deleteCustomer(customerId);

        // Customer silme SQL'ini hemen çalıştırır.
        customerRepository.flush();

        // THEN
        assertFalse(
                userAccountRepository.existsById(accountId)
        );

        assertFalse(
                customerRepository.existsById(customerId)
        );
    }
}