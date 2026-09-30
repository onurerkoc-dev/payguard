package dev.onurerkoc.payguard.config;

import dev.onurerkoc.payguard.service.AdminBootstrapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapConfigurationTest {

    private final AdminBootstrapService service = mock(AdminBootstrapService.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AdminBootstrapConfiguration.class)
            .withBean(AdminBootstrapService.class, () -> service)
            .withInitializer(context -> {
                try {
                    context.getEnvironment().getPropertySources().addLast(
                            new ResourcePropertySource("classpath:application.properties")
                    );
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });

    @Test
    void bootstrap_whenDefaultsUsed_shouldStayDisabledEvenWithCredentials() {
        contextRunner.withPropertyValues(
                "payguard.admin-bootstrap.email=bootstrap@example.com",
                "payguard.admin-bootstrap.password=TestOnlyPassword123!"
        ).run(context -> {
            assertThat(context).doesNotHaveBean(ApplicationRunner.class);
            verifyNoInteractions(service);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "yes", "1"})
    void bootstrap_whenFlagNotTrue_shouldStayDisabled(String flag) {
        contextRunner.withPropertyValues("payguard.admin-bootstrap.enabled=" + flag)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ApplicationRunner.class);
                    verifyNoInteractions(service);
                });
    }

    @Test
    void bootstrap_whenEnabled_shouldReadSettingsAndCreateAccount(CapturedOutput output) {
        when(service.createAdmin("bootstrap@example.com", "TestOnlyPassword123!"))
                .thenReturn(true);

        enabledRunner().run(context -> {
            assertThat(context).hasSingleBean(ApplicationRunner.class);
            context.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments());
            verify(service).createAdmin("bootstrap@example.com", "TestOnlyPassword123!");
        });

        assertThat(output).contains("Admin hesabı oluşturuldu.")
                .doesNotContain("bootstrap@example.com", "TestOnlyPassword123!");
    }

    @Test
    void bootstrap_whenAdminExists_shouldReportPreservedAccount(CapturedOutput output) {
        enabledRunner().run(context -> {
            context.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments());
            verify(service).createAdmin("bootstrap@example.com", "TestOnlyPassword123!");
        });

        assertThat(output).contains("Mevcut admin hesabı korundu.")
                .doesNotContain("bootstrap@example.com", "TestOnlyPassword123!");
    }

    @Test
    void bootstrap_whenServiceRejectsSettings_shouldPropagateFailure() {
        IllegalStateException failure = new IllegalStateException("Kurulum reddedildi");
        when(service.createAdmin("bootstrap@example.com", "TestOnlyPassword123!"))
                .thenThrow(failure);

        enabledRunner().run(context -> {
            IllegalStateException actual = assertThrows(IllegalStateException.class,
                    () -> context.getBean(ApplicationRunner.class)
                            .run(new DefaultApplicationArguments()));
            assertSame(failure, actual);
        });
    }

    private ApplicationContextRunner enabledRunner() {
        return contextRunner.withPropertyValues(
                "payguard.admin-bootstrap.enabled=true",
                "payguard.admin-bootstrap.email=bootstrap@example.com",
                "payguard.admin-bootstrap.password=TestOnlyPassword123!"
        );
    }
}
