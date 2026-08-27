package dev.onurerkoc.payguard.dto;

import java.math.BigDecimal;

public class VirtualCardResponse {
// burası json ile kullanıcıya gidilecek kısım burda o kodlanıyor.
// DB ile bi alakası yok o yüzden tekrar oluşturuyoruz json ile yollanacak kısımları
//service tarafından oluşturulup responseye verilcek yani buraya
    private Long id;
    private String cardName;
    private String cardNumber;
    private Integer expiryMonth;
    private Integer expiryYear;
    private BigDecimal balance;
    private BigDecimal singleTransactionLimit;
    private BigDecimal dailyLimit;
    private boolean frozen;
    private Long customerId;

    public VirtualCardResponse(
            Long id,
            String cardName,
            String cardNumber,
            Integer expiryMonth,
            Integer expiryYear,
            BigDecimal balance,
            BigDecimal singleTransactionLimit,
            BigDecimal dailyLimit,
            boolean frozen,
            Long customerId) {

        this.id = id;
        this.cardName = cardName;
        this.cardNumber = cardNumber;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.balance = balance;
        this.singleTransactionLimit = singleTransactionLimit;
        this.dailyLimit = dailyLimit;
        this.frozen = frozen;
        this.customerId = customerId;
    }

    public Long getId() {
        return id;
    }

    public String getCardName() {
        return cardName;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public Integer getExpiryMonth() {
        return expiryMonth;
    }

    public Integer getExpiryYear() {
        return expiryYear;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getSingleTransactionLimit() {
        return singleTransactionLimit;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Long getCustomerId() {
        return customerId;
    }
}