package dev.onurerkoc.payguard.security;

import dev.onurerkoc.payguard.entity.UserAccount;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

public class PayGuardUserDetails extends User {

    private static final long serialVersionUID = 1L;

    private final Long accountId;
    private final Long customerId;

    public PayGuardUserDetails(UserAccount account) {

        // Spring Security'nin ihtiyaç duyduğu giriş bilgilerini iletiriz.
        super(
                account.getEmail(),
                account.getPasswordHash(),
                account.isEnabled(),
                true, // Hesabın süresi dolmamış.
                true, // Şifrenin süresi dolmamış.
                true, // Hesap kilitlenmemiş.
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" + account.getRole().name()
                        )
                )
        );

        this.accountId = account.getId();

        // Müşteri profili olmayan yönetici hesabında null olabilir.
        this.customerId = account.getCustomer() == null
                ? null
                : account.getCustomer().getId();
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getCustomerId() {
        return customerId;
    }
}