package com.zenith.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI zenithOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Zenith Settlement Engine API")
                        .description("High-Performance FinTech Settlement Engine API")
                        .version("v1.0.0")
                        .contact(new Contact().name("Zenith Engineering Team").url("https://zenith.example.com"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Zenith-Key")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"));
    }
}
