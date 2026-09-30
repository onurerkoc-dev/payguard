package dev.onurerkoc.payguard.service;

import dev.onurerkoc.payguard.dto.VirtualCardLimitUpdateRequest;
import dev.onurerkoc.payguard.dto.VirtualCardResponse;
import dev.onurerkoc.payguard.entity.Customer;
import dev.onurerkoc.payguard.entity.VirtualCard;
import dev.onurerkoc.payguard.exception.InvalidCardLimitException;
import dev.onurerkoc.payguard.exception.VirtualCardNotFoundException;
import dev.onurerkoc.payguard.repository.CardTransactionRepository;
import dev.onurerkoc.payguard.repository.CustomerRepository;
import dev.onurerkoc.payguard.repository.VirtualCardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualCardLimitServiceTest {

    @Mock
    private VirtualCardRepository virtualCardRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CardTransactionRepository cardTransactionRepository;
    @InjectMocks
    private VirtualCardService virtualCardService;

    @ParameterizedTest
    @CsvSource({"2000.00, 3000.00", "2000.00, 2000.00", "0.01, 0.01"})
    void updateLimits_whenValid_shouldChangeCardAndResponse(String single, String daily) {
        Customer customer = customer();
        VirtualCard card = card(customer);
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(virtualCardRepository.findByIdAndCustomerId(10L, 7L))
                .thenReturn(Optional.of(card));

        VirtualCardResponse response = virtualCardService.updateLimits(7L, 10L, request(single, daily));

        assertEquals(new BigDecimal(single), card.getSingleTransactionLimit());
        assertEquals(new BigDecimal(daily), card.getDailyLimit());
        assertEquals(new BigDecimal(single), response.getSingleTransactionLimit());
        assertEquals(new BigDecimal(daily), response.getDailyLimit());
    }

    @Test
    void updateLimits_whenDailyLimitLower_shouldLeaveBothSavedLimitsUnchanged() {
        Customer customer = customer();
        VirtualCard card = card(customer);
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(virtualCardRepository.findByIdAndCustomerId(10L, 7L))
                .thenReturn(Optional.of(card));

        assertThrows(InvalidCardLimitException.class,
                () -> virtualCardService.updateLimits(7L, 10L, request("2000.00", "1000.00")));

        // Kural hatasında iki eski limit de korunmalı.
        assertEquals(new BigDecimal("5000.00"), card.getSingleTransactionLimit());
        assertEquals(new BigDecimal("10000.00"), card.getDailyLimit());
    }

    @Test
    void updateLimits_whenCardNotOwned_shouldRejectUpdate() {
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer()));
        // Kart yalnızca kart ID'siyle değil, müşteri ID'siyle birlikte aranır.
        when(virtualCardRepository.findByIdAndCustomerId(10L, 7L))
                .thenReturn(Optional.empty());

        assertThrows(VirtualCardNotFoundException.class,
                () -> virtualCardService.updateLimits(7L, 10L, request("2000.00", "3000.00")));
    }

    private Customer customer() {
        return new Customer("Onur", "Erkoç", "onur@example.com");
    }

    private VirtualCard card(Customer customer) {
        return new VirtualCard("Test Kartı", "9999123456789012", 9, 2030,
                new BigDecimal("5000.00"), new BigDecimal("10000.00"), customer);
    }

    private VirtualCardLimitUpdateRequest request(String single, String daily) {
        VirtualCardLimitUpdateRequest request = new VirtualCardLimitUpdateRequest();
        request.setSingleTransactionLimit(new BigDecimal(single));
        request.setDailyLimit(new BigDecimal(daily));
        return request;
    }
}
