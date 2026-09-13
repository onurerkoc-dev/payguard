package dev.onurerkoc.payguard.security;

import dev.onurerkoc.payguard.entity.UserAccount;
import dev.onurerkoc.payguard.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class PayGuardUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public PayGuardUserDetailsService(
            UserAccountRepository userAccountRepository) {

        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        // Kayıt sırasında kullandığımız e-posta normalizasyonuyla aynı.
        String normalizedEmail = username
                .trim()
                .toLowerCase(Locale.ROOT);

        UserAccount account = userAccountRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Giriş bilgileri geçersiz"
                ));

        return new PayGuardUserDetails(account);
    }
}