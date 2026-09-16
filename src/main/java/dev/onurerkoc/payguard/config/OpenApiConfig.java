package dev.onurerkoc.payguard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BASIC_AUTH_SCHEME = "basicAuth";

    @Bean
    public OpenAPI payGuardOpenApi() {

        return new OpenAPI()
                .info(new Info()
                        .title("PayGuard API")
                        .description(
                                "Sanal kart yönetimi ve ödeme "
                                        + "yetkilendirme REST API'si"
                        )
                        .version("v1")
                )
                .components(new Components()
                        .addSecuritySchemes(
                                BASIC_AUTH_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")
                        )
                );
    }
}