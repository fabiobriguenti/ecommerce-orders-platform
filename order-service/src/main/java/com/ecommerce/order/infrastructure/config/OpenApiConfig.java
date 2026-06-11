package com.ecommerce.order.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.1 metadata (the spec version is set via {@code springdoc.api-docs.version} in
 * application.yml). Swagger UI is served at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI orderServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Order Service API")
                .description("E-commerce order platform — orders and payments lifecycle.")
                .version("v1")
                .contact(new Contact().name("Order Platform Team"))
                .license(new License().name("MIT")));
    }
}
