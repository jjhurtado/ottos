package org.jobits.ottos.identity.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html; contract at /v3/api-docs (source for the Flutter Dart client). */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Ottos API", version = "v1", description = "API for the Ottos remittance platform"))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfig {
}
