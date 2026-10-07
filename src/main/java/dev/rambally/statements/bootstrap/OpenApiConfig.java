package dev.rambally.statements.bootstrap;

import java.util.List;

import dev.rambally.statements.application.Principal;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearer";

    static {
        // Principal is resolved from the verified JWT by JwtPrincipalResolver, never from the request. Without this,
        // springdoc renders its fields (customerId, admin) as query parameters and Swagger UI sends placeholder values.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Principal.class);
    }

    @Bean
    OpenAPI openApi(AppProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title("Secure Statement Delivery API")
                        .version("v1")
                        .description("Stores customer statements as encrypted PDFs and issues secure, time-limited, "
                                + "single-use download links. Authenticate with a bearer JWT; in the dev profile mint "
                                + "one at POST /dev/token."))
                .servers(List.of(new Server().url(properties.publicBaseUrl().toString())))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
