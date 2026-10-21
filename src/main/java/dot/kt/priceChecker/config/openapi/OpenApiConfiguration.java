package dot.kt.priceChecker.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI priceCheckerOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Price Checker API").version("v1")
                        .description("Seller accounts, inventory, and Google Sheets imports. "
                                + "Register a seller, sign in at /api/v1/auth/login, and paste accessToken into Authorize. "
                                + "ADMIN can read/write across accounts; MANAGER can read across accounts; "
                                + "SELLER and USER can access only their permitted account operations."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Access token returned by /api/v1/auth/login.")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
