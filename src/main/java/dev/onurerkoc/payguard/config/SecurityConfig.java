package dev.onurerkoc.payguard.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import dev.onurerkoc.payguard.security.PayGuardUserDetailsService;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/auth/register")
                        .permitAll()
                        .anyRequest().authenticated()
                )

                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults());

        // Provider bean'i Spring tarafından otomatik bağlanır.
        // CSRF koruması açık kalır.
        return http.build();
    }
    @Bean
    public PasswordEncoder passwordEncoder() {

        // Şifrelerin BCrypt ile hashlenmesini ve doğrulanmasını sağlar.
        return new BCryptPasswordEncoder();
    }
    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            PayGuardUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        // Hesabı bizim servisimiz üzerinden yükler.
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        // Şifreyi kayıt sırasında kullanılan encoder ile doğrular.
        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }
}