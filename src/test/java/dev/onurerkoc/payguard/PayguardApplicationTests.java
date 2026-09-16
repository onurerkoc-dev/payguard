package dev.onurerkoc.payguard;

import dev.onurerkoc.payguard.config.MySqlTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestcontainersConfiguration.class)
class PayguardApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {

    }

    @Test
    void openApiDocumentationShouldBePubliclyAccessible()
            throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title")
                        .value("PayGuard API"))
                .andExpect(jsonPath("$.info.version")
                        .value("v1"))
                .andExpect(jsonPath(
                        "$['paths']['/api/auth/register']"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.securitySchemes.basicAuth.scheme"
                ).value("basic"));
    }

    @Test
    void swaggerUiShouldBePubliclyAccessible()
            throws Exception {

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }
}