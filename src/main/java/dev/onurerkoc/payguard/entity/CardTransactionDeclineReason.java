package dev.onurerkoc.payguard.entity;

public enum CardTransactionDeclineReason {
    CARD_FROZEN("Kart dondurulmuş"),
    CARD_EXPIRED("Kartın süresi dolmuş"),
    INSUFFICIENT_BALANCE("Bakiye yetersiz"),
    SINGLE_TRANSACTION_LIMIT_EXCEEDED("Tek işlem limiti aşıldı"),
    DAILY_LIMIT_EXCEEDED("Günlük limit aşıldı"),
    ONLINE_TRANSACTIONS_DISABLED("İnternet işlemleri kapalı"),
    INTERNATIONAL_TRANSACTIONS_DISABLED("Yurt dışı işlemleri kapalı");

    private final String displayName;

    CardTransactionDeclineReason(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
