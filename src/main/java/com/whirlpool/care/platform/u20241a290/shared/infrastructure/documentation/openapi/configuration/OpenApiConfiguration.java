package com.whirlpool.care.platform.u20241a290.shared.infrastructure.documentation.openapi.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures the OpenAPI specification exposed by the platform (Swagger UI).
 *
 * @author Jose Fernando Flores Pinchi
 */
@Configuration
public class OpenApiConfiguration {

    @Value("${spring.application.name}")
    String applicationName;

    @Value("${documentation.application.description}")
    String applicationDescription;

    @Value("${documentation.application.version}")
    String applicationVersion;

    @Bean
    public OpenAPI whirlpoolCareOpenApi() {
        var openApi = new OpenAPI();
        openApi.info(new Info()
                .title(this.applicationName)
                .description(this.applicationDescription)
                .version(this.applicationVersion)
                .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html")));
        openApi.servers(List.of(
                new Server().url("http://localhost:8290").description("Local Development Environment")
        ));
        return openApi;
    }
}
