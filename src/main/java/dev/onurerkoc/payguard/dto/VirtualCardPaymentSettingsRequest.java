package dev.onurerkoc.payguard.dto;

import jakarta.validation.constraints.NotNull;

/*
Postman veya React tarafından gönderilen
kart kullanım ayarlarını Controller'a taşır.

Bu sınıf Entity değildir ve MySQL'e kaydedilmez.
*/
public class VirtualCardPaymentSettingsRequest {

    @NotNull(message = "İnternet alışverişi ayarı zorunludur")
    private Boolean onlineTransactionsEnabled;

    @NotNull(message = "Yurt dışı alışverişi ayarı zorunludur")
    private Boolean internationalTransactionsEnabled;

    public Boolean getOnlineTransactionsEnabled() {
        return onlineTransactionsEnabled;
    }

    public void setOnlineTransactionsEnabled(
            Boolean onlineTransactionsEnabled) {
        this.onlineTransactionsEnabled = onlineTransactionsEnabled;
    }

    public Boolean getInternationalTransactionsEnabled() {
        return internationalTransactionsEnabled;
    }

    public void setInternationalTransactionsEnabled(
            Boolean internationalTransactionsEnabled) {
        this.internationalTransactionsEnabled =
                internationalTransactionsEnabled;
    }
}