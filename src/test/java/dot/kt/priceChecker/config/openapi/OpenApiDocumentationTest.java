package dot.kt.priceChecker.config.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTest {
    @Autowired MockMvc mvc;

    @Test
    void swaggerPageAndItsResourcesArePublic() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/v3/api-docs/swagger-config")));
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
        mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Price Checker API")));
    }

    @Test
    void openApiDocumentsEveryApiOperationAndItsAuthentication() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Price Checker API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/sellers/register'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/management/sellers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/sellers/{sellerId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/account/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/inventory/imports/{id}'].get.parameters[0].in").value("path"))
                .andExpect(jsonPath("$.components.schemas.RegisterSellerRequest.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/me'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/me'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/me/sheet'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/me/sheet'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/inventory/imports/saved'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/sellers/{sellerId}/sheet'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/me/sheet'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/inventory'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/inventory/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/inventory/{id}/item'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/catalog/domains'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/catalog/domains'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/catalog/{domain}/brands'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/catalog/{domain}/options'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/catalog/{domain}/items'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/catalog/{domain}/items/{id}/offers'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/catalog/{domain}/items/{id}/price'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/items/{id}/price-intel'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/telegram/users'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/management/items'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/inventory/imports'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/inventory/imports/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seller/inventory/imports/{id}/confirm'].post").exists())
                .andExpect(jsonPath("$.components.schemas.RegisterSellerRequest").exists())
                .andExpect(jsonPath("$.components.schemas.ImportResponse").exists());
    }

    @Test
    void protectedApiStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/seller/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/seller/inventory")).andExpect(status().isUnauthorized());
    }
}
