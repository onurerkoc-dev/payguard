package dev.onurerkoc.payguard.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualCardTest {
    private VirtualCard card;
//Her test çalışmadan hemen önce setUp() metodunu çalıştır ve o test için yeni bir kart hazırla. bu yaptığımıza ise refactoring denir.
    @BeforeEach
    void setUp() {

        Customer customer = new Customer(
                "Onur",
                "Erkoç",
                "onur@example.com"
        );

        card = new VirtualCard(
                "Test Kartı",
                "9999123456789012",
                9,
                2030,
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                customer
        );
    }

    @Test
    void newCardShouldHaveSafeInitialValues() {

        // Assert: Oluşan kartın başlangıç değerlerini kontrol ederiz.
        assertEquals(BigDecimal.ZERO, card.getBalance());
        assertFalse(card.isFrozen());
        assertTrue(card.isOnlineTransactionsEnabled());
        assertFalse(card.isInternationalTransactionsEnabled());
    }
    @Test
    void loadBalanceShouldIncreaseCardBalance() {

        // Act: Karta 250 TL yüklüyoruz.
        card.loadBalance(new BigDecimal("250.00"));

        // Assert: Yeni bakiyenin 250 TL olduğunu doğruluyoruz.
        assertEquals(
                new BigDecimal("250.00"),
                card.getBalance()
        );
    }
    @Test
    void loadBalanceShouldAccumulateMultipleLoads() {


        // Act: Karta iki ayrı bakiye yüklemesi yapılır.
        card.loadBalance(new BigDecimal("250.00"));
        card.loadBalance(new BigDecimal("100.00"));

        // Assert: 250 + 100 sonucunda bakiye 350 olmalıdır.
        assertEquals(
                new BigDecimal("350.00"),
                card.getBalance()
        );
    }
    @Test
    void deductBalanceShouldDecreaseCardBalance() {

        card.loadBalance(new BigDecimal("500.00"));

        // Act: Karttan 125 TL düşülür.
        card.deductBalance(new BigDecimal("125.00"));

        // Assert: 500 - 125 sonucunda 375 TL kalmalıdır.
        assertEquals(
                new BigDecimal("375.00"),
                card.getBalance()
        );
    }
    @Test
    void freezeShouldMarkCardAsFrozen() {

        // Kartın başlangıçta açık olduğunu da doğrularız.
        assertFalse(card.isFrozen());

        // Act: Kart dondurulur.
        card.freeze();

        // Assert: Kart artık donmuş olmalıdır.
        assertTrue(card.isFrozen());
    }
    @Test
    void unfreezeShouldMarkCardAsNotFrozen() {

        card.freeze();
        assertTrue(card.isFrozen());

        // Act: Donmuş kart tekrar açılır.
        card.unfreeze();

        // Assert: Kart artık donmuş olmamalıdır.
        assertFalse(card.isFrozen());
    }
    @Test
    void updateLimitsShouldChangeBothCardLimits() {

        // Act: Kartın limitleri güncellenir.
        card.updateLimits(
                new BigDecimal("2000.00"),
                new BigDecimal("8000.00")
        );

        // Assert: İki limitin de yeni değerleri taşıdığı doğrulanır.
        assertEquals(
                new BigDecimal("2000.00"),
                card.getSingleTransactionLimit()
        );

        assertEquals(
                new BigDecimal("8000.00"),
                card.getDailyLimit()
        );
    }
    @Test
    void updatePaymentSettingsShouldChangeTransactionPermissions() {

        // Başlangıç ayarlarını doğrularız.
        assertTrue(card.isOnlineTransactionsEnabled());
        assertFalse(card.isInternationalTransactionsEnabled());

        // Act: Online işlemler kapatılır, yurt dışı işlemleri açılır.
        card.updatePaymentSettings(false, true);

        // Assert: Yeni ayarların karta uygulandığını doğrularız.
        assertFalse(card.isOnlineTransactionsEnabled());
        assertTrue(card.isInternationalTransactionsEnabled());
    }
}