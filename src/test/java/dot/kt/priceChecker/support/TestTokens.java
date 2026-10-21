package dot.kt.priceChecker.support;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class TestTokens {
    private TestTokens() { }

    public static RequestPostProcessor bearer(MockMvc mvc, JsonMapper json, String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return token(json.readTree(body).path("accessToken").asText());
    }

    public static RequestPostProcessor token(String value) {
        return request -> { request.addHeader("Authorization", "Bearer " + value); return request; };
    }
}
