package com.rulecraft.decisionops.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI ruleCraftOpenApi() {
        return new OpenAPI().components(new Components().addSecuritySchemes("decisionApiKey",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("RuleCraft API key"))).info(new Info()
                .title("RuleCraft DecisionOps API")
                .version("v1")
                .description("Private API contract for governed decision operations."));
    }
}
