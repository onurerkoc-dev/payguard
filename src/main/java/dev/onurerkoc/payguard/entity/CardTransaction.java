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

    // Aynı ödeme isteğinin birden fazla kez işlenmesini engellemek için
    // istemci tarafından gönderilen benzersiz anahtarı saklar.
    @Column(
            name = "idempotency_key",
            unique = true,
            length = 100,
            updatable = false
    )
    private String idempotencyKey;

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

    // Ödemenin internet üzerinden yapılıp yapılmadığını saklar.
    // Bakiye yükleme işlemlerinde uygulanmadığı için null olabilir.
    @Column(name = "online_transaction")
    private Boolean onlineTransaction;

    // Ödemenin yurt dışı işlemi olup olmadığını saklar.
    // Bakiye yükleme işlemlerinde uygulanmadığı için null olabilir.
    @Column(name = "international_transaction")
    private Boolean internationalTransaction;

    // İşlem tamamlandıktan hemen sonraki kart bakiyesini saklar.
    // Böylece idempotent tekrar isteğinde orijinal bakiye döndürülebilir.
    @Column(
            name = "balance_after_transaction",
            precision = 19,
            scale = 2
    )
    private BigDecimal balanceAfterTransaction;

    // Instant, dünya üzerindeki kesin bir zamanı UTC olarak temsil eder.
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Bir kartın birçok işlemi olabilir fakat her işlem yalnızca bir karta aittir.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private VirtualCard card;

    // JPA entity oluştururken bu constructor'ı kullanır.
    public CardTransaction() {

    }

    // Bakiye yükleme gibi idempotency anahtarı gerektirmeyen işlemler
    // bu constructor'ı kullanır.
    public CardTransaction(
            CardTransactionType type,
            CardTransactionStatus status,
            BigDecimal amount,
            String merchantName,
            CardTransactionDeclineReason declineReason,
            VirtualCard card) {

        this(
                type,
                status,
                amount,
                merchantName,
                declineReason,
                null,
                null,
                null,
                null,
                card
        );
    }

    // Eski ödeme oluşturma kullanımlarının bozulmaması için
    // yeni ana constructor'a yönlendirir.
    public CardTransaction(
            CardTransactionType type,
            CardTransactionStatus status,
            BigDecimal amount,
            String merchantName,
            CardTransactionDeclineReason declineReason,
            String idempotencyKey,
            VirtualCard card) {

        this(
                type,
                status,
                amount,
                merchantName,
                declineReason,
                idempotencyKey,
                null,
                null,
                null,
                card
        );
    }

    // Ödeme işleminin idempotency ve işlem anındaki bütün bilgilerini alır.
    public CardTransaction(
            CardTransactionType type,
            CardTransactionStatus status,
            BigDecimal amount,
            String merchantName,
            CardTransactionDeclineReason declineReason,
            String idempotencyKey,
            Boolean onlineTransaction,
            Boolean internationalTransaction,
            BigDecimal balanceAfterTransaction,
            VirtualCard card) {

        this.type = type;
        this.status = status;
        this.amount = amount;
        this.merchantName = merchantName;
        this.declineReason = declineReason;
        this.idempotencyKey = idempotencyKey;
        this.onlineTransaction = onlineTransaction;
        this.internationalTransaction = internationalTransaction;
        this.balanceAfterTransaction = balanceAfterTransaction;
        this.createdAt = Instant.now();
        this.card = card;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public CardTransactionType getType() {
        return type;
    }

    public CardTransactionStatus getStatus() {
        return status;
    }

    public CardTransactionDeclineReason getDeclineReason() {
        return declineReason;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public Boolean getOnlineTransaction() {
        return onlineTransaction;
    }

    public Boolean getInternationalTransaction() {
        return internationalTransaction;
    }

    public BigDecimal getBalanceAfterTransaction() {
        return balanceAfterTransaction;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public VirtualCard getCard() {
        return card;
    }
}