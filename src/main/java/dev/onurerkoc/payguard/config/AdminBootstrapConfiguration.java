package dev.onurerkoc.payguard.config;

import dev.onurerkoc.payguard.service.AdminBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "payguard.admin-bootstrap",
        name = "enabled",
        havingValue = "true"
)
public class AdminBootstrapConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfiguration.class);

    @Bean
    public ApplicationRunner adminBootstrapRunner(
            AdminBootstrapService adminBootstrapService,
            @Value("${payguard.admin-bootstrap.email}") String email,
            @Value("${payguard.admin-bootstrap.password}") String password) {

        return args -> {
            boolean created = adminBootstrapService.createAdmin(email, password);
            // Kurulum sonucu yazılır; e-posta, şifre ve hash loglanmaz.
            if (created) {
                log.info("Admin hesabı oluşturuldu.");
            } else {
                log.info("Mevcut admin hesabı korundu.");
            }
        };
    }
}
