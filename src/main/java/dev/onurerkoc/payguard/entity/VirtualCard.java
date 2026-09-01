package dev.onurerkoc.payguard.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

/*

Katman	Basit anlamı	Örnek
Controller	API’nin giriş kapısı	URL’den customerId almak
DTO	Gelen/giden veri paketi	Yüklenecek miktarı taşımak
Service	İş akışını yönetmek	Kartı bul, kontrol et, işlemi başlat
Entity	Gerçek veriyi ve davranışını taşımak	Bakiye artırmak, kartı dondurmak
Repository	MySQL ile konuşmak	Kartı bulmak ve kaydetmek
Exception	Hatalı durumu ifade etmek	“Sanal kart bulunamadı”
 */


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

    // Kart internet üzerinden yapılan ödemelerde kullanılabilir mi?
    @Column(nullable = false)
    private boolean onlineTransactionsEnabled;

    // Kart yurt dışı işlemlerinde kullanılabilir mi?
    @Column(nullable = false)
    private boolean internationalTransactionsEnabled;

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
        this.onlineTransactionsEnabled = true;
        this.internationalTransactionsEnabled = false;
    }
    public void loadBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
    public void freeze() {
        this.frozen = true;
    }

    public void unfreeze() {
        this.frozen = false;
    }
    public void updateLimits(
            BigDecimal singleTransactionLimit,
            BigDecimal dailyLimit) {

        this.singleTransactionLimit = singleTransactionLimit;
        this.dailyLimit = dailyLimit;
    }
    // Kartın internet ve yurt dışı ödeme izinlerini birlikte günceller.
    public void updatePaymentSettings(
            boolean onlineTransactionsEnabled,
            boolean internationalTransactionsEnabled) {

        this.onlineTransactionsEnabled = onlineTransactionsEnabled;
        this.internationalTransactionsEnabled =
                internationalTransactionsEnabled;
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

    public boolean isOnlineTransactionsEnabled() {
        return onlineTransactionsEnabled;
    }

    public boolean isInternationalTransactionsEnabled() {
        return internationalTransactionsEnabled;
    }
}