package dev.onurerkoc.payguard.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccountTest {

    @Test
    void createAdmin_shouldCreateEnabledAdminWithoutCustomer() {
        UserAccount account = UserAccount.createAdmin(
                "admin@example.com", "test-password-hash"
        );

        assertEquals("admin@example.com", account.getEmail());
        assertEquals("test-password-hash", account.getPasswordHash());
        assertEquals(UserRole.ADMIN, account.getRole());
        assertTrue(account.isEnabled());
        assertNull(account.getCustomer());
    }

    @Test
    void newUserAccount_shouldRemainUserAndKeepItsCustomer() {
        Customer customer = new Customer(
                "Onur", "Erkoç", "onur@example.com"
        );

        UserAccount account = new UserAccount(
                "onur@example.com", "test-password-hash", customer
        );

        // Admin oluşturma metodu normal kayıtların rolünü değiştirmemeli.
        assertEquals(UserRole.USER, account.getRole());
        assertSame(customer, account.getCustomer());
        assertTrue(account.isEnabled());
    }

    @Test
    void newUserAccount_withoutCustomer_shouldStillRejectCreation() {
        assertThrows(IllegalArgumentException.class,
                () -> new UserAccount(
                        "onur@example.com", "test-password-hash", null
                ));
    }
}
