package dev.onurerkoc.payguard.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "virtual_cards")
public class VirtualCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String cardName;

    @Column(nullable = false, unique = true, length = 16)
    private String cardNumber;

    @Column(nullable = false)
    private Integer expiryMonth;

    @Column(nullable = false)
    private Integer expiryYear;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal singleTransactionLimit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyLimit;

    @Column(nullable = false)
    private boolean frozen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    public VirtualCard() {

    }

    public VirtualCard(
            String cardName,
            String cardNumber,
            Integer expiryMonth,
            Integer expiryYear,
            BigDecimal singleTransactionLimit,
            BigDecimal dailyLimit,
            Customer customer) {

        this.cardName = cardName;
        this.cardNumber = cardNumber;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.balance = BigDecimal.ZERO;
        this.singleTransactionLimit = singleTransactionLimit;
        this.dailyLimit = dailyLimit;
        this.frozen = false;
        this.customer = customer;
    }
    public void loadBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
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

    public Customer getCustomer() {
        return customer;
    }
}