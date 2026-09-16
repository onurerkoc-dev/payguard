package dev.onurerkoc.payguard;

import dev.onurerkoc.payguard.config.MySqlTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@Import(MySqlTestcontainersConfiguration.class)
class PayguardApplicationTests {

    @Test
    void contextLoads() {

    }
}