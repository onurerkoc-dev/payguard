package dev.onurerkoc.payguard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/*
Mağaza veya ödeme ağı tarafından gönderildiğini varsaydığımız
ödeme bilgilerini Controller'a taşır.

Bu sınıf Entity değildir ve doğrudan MySQL'e kaydedilmez.
*/
public class PaymentAuthorizationRequest {

    @NotNull(message = "Ödeme tutarı zorunludur")
    @DecimalMin(
            value = "0.01",
            message = "Ödeme tutarı sıfırdan büyük olmalıdır"
    )
    @Digits(
            integer = 17,
            fraction = 2,
            message = "Ödeme tutarı en fazla 2 ondalık basamak içerebilir"
    )
    private BigDecimal amount;

    @NotBlank(message = "Mağaza adı zorunludur")
    @Size(
            max = 100,
            message = "Mağaza adı en fazla 100 karakter olabilir"
    )
    private String merchantName;

    @NotNull(message = "İnternet işlemi bilgisi zorunludur")
    private Boolean onlineTransaction;

    @NotNull(message = "Yurt dışı işlemi bilgisi zorunludur")
    private Boolean internationalTransaction;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public Boolean getOnlineTransaction() {
        return onlineTransaction;
    }

    public void setOnlineTransaction(Boolean onlineTransaction) {
        this.onlineTransaction = onlineTransaction;
    }

    public Boolean getInternationalTransaction() {
        return internationalTransaction;
    }

    public void setInternationalTransaction(
            Boolean internationalTransaction) {
        this.internationalTransaction = internationalTransaction;
    }
}