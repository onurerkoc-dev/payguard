package dev.onurerkoc.payguard.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("customerAccessPolicy")
public class CustomerAccessPolicy {

    public boolean isOwner(
            Authentication authentication,
            Long customerId) {

        // Kimlik doğrulanmamışsa veya hedef müşteri belirtilmemişse reddet.
        if (authentication == null
                || !authentication.isAuthenticated()
                || customerId == null) {

            return false;
        }

        // Yalnızca bizim giriş sistemimizin oluşturduğu kimliği kabul et.
        if (!(authentication.getPrincipal()
                instanceof PayGuardUserDetails principal)) {

            return false;
        }

        // Müşteri profili olmayan hesap sahiplik kontrolünü geçemez.
        Long authenticatedCustomerId = principal.getCustomerId();

        return authenticatedCustomerId != null
                && authenticatedCustomerId.equals(customerId);
    }
}