package com.rulecraft.decisionops.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI ruleCraftOpenApi() {
        return new OpenAPI().info(new Info()
                .title("RuleCraft DecisionOps API")
                .version("v1")
                .description("Private API contract for governed decision operations."));
    }
}
