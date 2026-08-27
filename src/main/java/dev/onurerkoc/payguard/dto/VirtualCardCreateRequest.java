package dev.onurerkoc.payguard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class VirtualCardCreateRequest {

    @NotBlank(message = "Kart adı zorunludur")
    @Size(max = 50, message = "Kart adı en fazla 50 karakter olabilir")
    private String cardName;

    @NotNull(message = "Tek işlem limiti zorunludur")
    @DecimalMin(
            value = "0.01",
            message = "Tek işlem limiti sıfırdan büyük olmalıdır"
    )
    @Digits(
            integer = 17,
            fraction = 2,
            message = "Tek işlem limiti en fazla 2 ondalık basamak içerebilir"
    )
    private BigDecimal singleTransactionLimit;

    @NotNull(message = "Günlük limit zorunludur")
    @DecimalMin(
            value = "0.01",
            message = "Günlük limit sıfırdan büyük olmalıdır"
    )
    @Digits(
            integer = 17,
            fraction = 2,
            message = "Günlük limit en fazla 2 ondalık basamak içerebilir"
    )
    private BigDecimal dailyLimit;

    public String getCardName() {
        return cardName;
    }

    public void setCardName(String cardName) {
        this.cardName = cardName;
    }

    public BigDecimal getSingleTransactionLimit() {
        return singleTransactionLimit;
    }

    public void setSingleTransactionLimit(BigDecimal singleTransactionLimit) {
        this.singleTransactionLimit = singleTransactionLimit;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }
}