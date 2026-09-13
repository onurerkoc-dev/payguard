package dev.onurerkoc.payguard.dto;

public class UserRegistrationResponse {

    private final Long accountId;
    private final Long customerId;
    private final String email;

    public UserRegistrationResponse(
            Long accountId,
            Long customerId,
            String email) {

        this.accountId = accountId;
        this.customerId = customerId;
        this.email = email;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getEmail() {
        return email;
    }
}