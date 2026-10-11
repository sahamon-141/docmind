package com.example.docmind.support;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context against a real pgvector container. Every test class shares one cached
 * context and container; tests isolate themselves by creating fresh, uniquely named users.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class})
public abstract class IntegrationTestBase {
    protected static final String CSRF_HEADER = "X-DocMind-CSRF";
    protected static final String REFRESH_COOKIE = "docmind_refresh";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected StubChatModel chatModel;

    @BeforeEach
    void resetChatModel() {
        chatModel.reset();
    }

    protected record Session(String username, String accessToken, String refreshToken, long userId) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    protected static String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected Session register(String username) throws Exception {
        String body = """
                {"username":"%s","email":"%s@example.com","password":"correct-horse-battery"}""".formatted(username, username);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return session(result);
    }

    protected Session session(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$.data.user.id");
        return new Session(JsonPath.read(json, "$.data.user.username"), JsonPath.read(json, "$.data.accessToken"),
                refreshCookieValue(result), id.longValue());
    }

    protected static String refreshCookieValue(MvcResult result) {
        for (String header : result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)) {
            if (header.startsWith(REFRESH_COOKIE + "=")) {
                return header.substring(REFRESH_COOKIE.length() + 1, header.indexOf(';'));
            }
        }
        return null;
    }

    protected static String setCookieHeader(MvcResult result) {
        return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(h -> h.startsWith(REFRESH_COOKIE + "=")).findFirst().orElse(null);
    }

    protected UUID uploadText(Session session, String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/v1/documents/upload").file(file)
                        .header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"));
    }

    protected static String json(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
