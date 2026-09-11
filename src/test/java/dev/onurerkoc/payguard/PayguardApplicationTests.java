package dev.onurerkoc.payguard;

import dev.onurerkoc.payguard.config.MySqlTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MySqlTestcontainersConfiguration.class)
class PayguardApplicationTests {

    @Test
    void contextLoads() {

    }
}