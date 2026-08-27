package dev.onurerkoc.payguard.exception;

public class InvalidCardLimitException extends RuntimeException {

    public InvalidCardLimitException(String message) {
        super(message);
    }
}