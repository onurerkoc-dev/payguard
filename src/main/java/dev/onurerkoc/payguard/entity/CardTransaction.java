package dev.onurerkoc.payguard.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "card_transactions")
public class CardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardTransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private CardTransactionDeclineReason declineReason;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String merchantName;

    // Instant, dünya üzerindeki kesin bir zamanı UTC olarak temsil eder.
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Bir kartın birçok işlemi olabilir fakat her işlem yalnızca bir karta aittir.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private VirtualCard card;



    public CardTransaction() {

    }

    // id değerini MySQL oluşturacak.
    // createdAt değerini PayGuard sistemi Instant.now() ile oluşturacak.
    public CardTransaction(
            CardTransactionType type,
            CardTransactionStatus status,
            BigDecimal amount,
            String merchantName,
            CardTransactionDeclineReason declineReason,
            VirtualCard card) {

        this.type = type;
        this.status = status;
        this.amount = amount;
        this.merchantName = merchantName;
        this.declineReason = declineReason;
        this.createdAt = Instant.now();
        this.card = card;
    }

    public Long getId() {
        return id;
    }

    public CardTransactionType getType() {
        return type;
    }

    public CardTransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public VirtualCard getCard() {
        return card;
    }

    public CardTransactionDeclineReason getDeclineReason() {
        return declineReason;
    }
}