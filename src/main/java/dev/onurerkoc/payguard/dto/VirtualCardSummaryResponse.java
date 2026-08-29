package dev.onurerkoc.payguard.dto;

import java.math.BigDecimal;
/*
Sınıf	Parametresiz constructor
VirtualCard entity	Gerekli; JPA nesneyi oluşturur
VirtualCardCreateRequest	Gerekli; Jackson JSON’u nesneye çevirir
VirtualCardSummaryResponse	Gerekli değil; Service kendisi oluşturur
 */
public class VirtualCardSummaryResponse {
    private Long id;
    private String cardName;
    private String maskedCardNumber;
    private BigDecimal balance;
    private boolean frozen;

    public VirtualCardSummaryResponse(Long id, String cardName, String maskedCardNumber, BigDecimal balance, boolean frozen) {
        this.id = id;
        this.cardName = cardName;
        this.maskedCardNumber = maskedCardNumber;
        this.balance = balance;
        this.frozen = frozen;
    }

    public Long getId() {
        return id;
    }

    public String getCardName() {
        return cardName;
    }

    public String getMaskedCardNumber() {
        return maskedCardNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public boolean isFrozen() {
        return frozen;
    }
}



