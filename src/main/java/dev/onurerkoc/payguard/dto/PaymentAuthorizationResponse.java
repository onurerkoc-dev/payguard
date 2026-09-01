package dev.onurerkoc.payguard.dto;

import dev.onurerkoc.payguard.entity.CardTransactionDeclineReason;
import dev.onurerkoc.payguard.entity.CardTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

/*
PayGuard'ın ödeme isteği için verdiği kararı
Postman veya React'e gönderir.

Bu sınıf Entity değildir ve MySQL'e kaydedilmez.
*/
public class PaymentAuthorizationResponse {

    private Long transactionId;
    private CardTransactionStatus status;
    private CardTransactionDeclineReason declineReason;
    private BigDecimal amount;
    private String merchantName;
    private BigDecimal remainingBalance;
    private Instant createdAt;
    private Long cardId;

    public PaymentAuthorizationResponse(
            Long transactionId,
            CardTransactionStatus status,
            CardTransactionDeclineReason declineReason,
            BigDecimal amount,
            String merchantName,
            BigDecimal remainingBalance,
            Instant createdAt,
            Long cardId) {

        this.transactionId = transactionId;
        this.status = status;
        this.declineReason = declineReason;
        this.amount = amount;
        this.merchantName = merchantName;
        this.remainingBalance = remainingBalance;
        this.createdAt = createdAt;
        this.cardId = cardId;
    }

    public Long getTransactionId() {
        return transactionId;
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

    public BigDecimal getRemainingBalance() {
        return remainingBalance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCardId() {
        return cardId;
    }
}