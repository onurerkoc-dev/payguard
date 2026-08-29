package dev.onurerkoc.payguard.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class VirtualCardBalanceLoadRequest {

    @NotNull(message = "Yüklenecek bakiye zorunludur")
    @DecimalMin(
            value = "0.01",
            message = "Yüklenecek bakiye sıfırdan büyük olmalıdır"
    )
    @Digits(
            integer = 17,
            fraction = 2,
            message = "Yüklenecek bakiye en fazla 2 ondalık basamak içerebilir"
    )
    private BigDecimal amount;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}