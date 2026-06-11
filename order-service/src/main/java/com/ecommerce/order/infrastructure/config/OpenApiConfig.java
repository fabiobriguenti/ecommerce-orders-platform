package com.ecommerce.order.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.1 metadata (the spec version is set via {@code springdoc.api-docs.version} in
 * application.yml). Swagger UI is served at {@code /swagger-ui.html} and exposes a Bearer-JWT
 * "Authorize" button matching the resource server (ADR-15).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    OpenAPI orderServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .description("E-commerce order platform — orders and payments lifecycle. "
                                + "Obtain a JWT from POST /api/v1/auth/token (dev only) and authorize.")
                        .version("v1")
                        .contact(new Contact().name("Order Platform Team"))
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("RSA-signed JWT carrying the required scopes")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
