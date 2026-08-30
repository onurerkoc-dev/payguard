package dev.onurerkoc.payguard.exception;


//silinmek istenen müşterinin kartı varsa Service’in işlemi durdurup anlamlı bir hata bildirmesi
public class CustomerHasVirtualCardsException extends RuntimeException {
    public CustomerHasVirtualCardsException(String message) {
        super(message);
    }
}
