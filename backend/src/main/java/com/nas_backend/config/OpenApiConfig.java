package com.nas_backend.config;

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
    public OpenAPI nasOpenAPI() {
        return new OpenAPI()
            // Basic project information
            .info(new Info()
                    .title("Raspberry Pi NAS API")
                    .description("API documentation for the DIY NAS System (Engineering Thesis)")
                    .version("1.0.0")
                    .contact(new Contact()
                            .name("SQ Programs (Eryk Klemencki)")
                            .email("sq.programs@gmail.com"))
                    .license(new License()
                            .name("GPL-3.0")
                            .url("https://www.gnu.org/licenses/gpl-3.0.html")))

            // Security configuration (Authorize button)
            .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
            .components(new Components().addSecuritySchemes("Bearer Authentication", createAPIKeyScheme()));
    }

    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .bearerFormat("UUID") // Token format hint
            .scheme("bearer");
    }
}