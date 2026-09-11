package dev.onurerkoc.payguard.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {

        return new MySQLContainer("mysql:8.4")
                .withDatabaseName("payguard_test")
                .withUsername("test")
                .withPassword("test");
    }
}