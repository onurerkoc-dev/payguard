package dev.onurerkoc.payguard.exception;

public class VirtualCardNotFoundException extends RuntimeException {
    public VirtualCardNotFoundException(String message) {
        super(message);
    }
}
