package com.dayo.capstone.cadence.security.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI configuration.
 *
 * Once running, the interactive UI is available at:
 *   http://localhost:8080/swagger-ui.html
 *
 * The raw OpenAPI JSON spec is available at:
 *   http://localhost:8080/v3/api-docs
 *
 * Registers a "bearerAuth" security scheme so endpoints protected by the
 * JWT filter (Section 18.1) can be exercised directly from the Swagger UI —
 * click "Authorize", paste a token obtained from the login endpoint, and
 * every subsequent request in the UI carries it automatically.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI cadenceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cadence — Academic Operations Platform API")
                        .description("""
                                Capstone project — Semicolon Africa.
                                Attendance verification, assignment lifecycle, assessments,
                                guardian access, feedback, disputes, and analytics —
                                see the PRD for full domain design (32 entities, 19 sections).
                                """)
                        .version("v0.0.1")
                        .contact(new Contact()
                                .name("Semicolon Africa Capstone")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}






