package dev.onurerkoc.payguard.dto;

import dev.onurerkoc.payguard.entity.CardTransactionDeclineReason;
import dev.onurerkoc.payguard.entity.CardTransactionStatus;
import dev.onurerkoc.payguard.entity.CardTransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public class CardTransactionResponse {

    private Long id;
    private CardTransactionType type;
    private CardTransactionStatus status;
    private BigDecimal amount;
    private String merchantName;
    private CardTransactionDeclineReason declineReason;
    private Instant createdAt;
    private Long cardId;

    public CardTransactionResponse(
            Long id,
            CardTransactionType type,
            CardTransactionStatus status,
            BigDecimal amount,
            String merchantName,
            CardTransactionDeclineReason declineReason,
            Instant createdAt,
            Long cardId) {

        this.id = id;
        this.type = type;
        this.status = status;
        this.amount = amount;
        this.merchantName = merchantName;
        this.declineReason = declineReason;
        this.createdAt = createdAt;
        this.cardId = cardId;
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

    public CardTransactionDeclineReason getDeclineReason() {
        return declineReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCardId() {
        return cardId;
    }
}