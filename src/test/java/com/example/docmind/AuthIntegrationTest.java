package com.example.docmind;

import com.example.docmind.config.AppProperties;
import com.example.docmind.entity.User;
import com.example.docmind.repository.UserRepository;
import com.example.docmind.security.JwtService;
import com.example.docmind.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AppProperties appProperties;
    @Autowired
    private UserRepository userRepository;

    @Test
    void registerReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        String username = uniqueName("alice");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@example.com","password":"correct-horse-battery"}""".formatted(username, username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        String cookie = setCookieHeader(result);
        assertThat(cookie).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/v1/auth");
        Session session = session(result);

        // only the hash is stored, never the raw token
        Integer stored = jdbcTemplate.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class, session.refreshToken());
        assertThat(stored).isZero();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.email").value(username + "@example.com"));
    }

    @Test
    void passwordIsStoredAsBcryptHash() throws Exception {
        Session session = register(uniqueName("hash"));
        User user = userRepository.findById(session.userId()).orElseThrow();
        assertThat(user.getPassword()).startsWith("$2").doesNotContain("correct-horse-battery");
    }

    @Test
    void duplicateUsernameAndEmailAreRejectedWith409() throws Exception {
        String username = uniqueName("dup");
        register(username);

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"other_%s@example.com","password":"correct-horse-battery"}""".formatted(username.toUpperCase(), username)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_TAKEN"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s@EXAMPLE.com","password":"correct-horse-battery"}""".formatted(uniqueName("x"), username)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));
    }

    @Test
    void registrationInputIsValidated() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"a b","email":"not-an-email","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.username").exists())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    void loginWorksWithUsernameOrEmailAndRejectsBadCredentials() throws Exception {
        String username = uniqueName("login");
        register(username);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"correct-horse-battery\"}".formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s@example.com\",\"password\":\"correct-horse-battery\"}".formatted(username)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"wrong-password\"}".formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        // unknown users get the identical response
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody_here\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointsRequireAuthenticationAndPublicOnesDoNot() throws Exception {
        mockMvc.perform(get("/api/v1/documents")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/conversations")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/chat/search/similarity").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().string(containsString("bearerAuth")));
    }

    @Test
    void authenticatedUserWithoutAdminRoleGets403() throws Exception {
        Session session = register(uniqueName("plain"));
        mockMvc.perform(get("/api/v1/admin/anything").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void malformedAndTamperedAccessTokensAreRejected() throws Exception {
        Session session = register(uniqueName("tamper"));
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));

        String token = session.accessToken();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void expiredAccessTokenIsRejectedWithTokenExpired() throws Exception {
        Session session = register(uniqueName("expired"));
        User user = userRepository.findById(session.userId()).orElseThrow();
        // same signing secret, but issued an hour ago with a 15 minute lifetime
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String expired = new JwtService(appProperties, past).generateAccessToken(user);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TOKEN_EXPIRED"));
    }

    @Test
    void refreshRotatesTokenAndReuseOfOldTokenRevokesTheFamily() throws Exception {
        Session session = register(uniqueName("rotate"));
        String first = session.refreshToken();

        MvcResult refreshed = refresh(first).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String second = refreshCookieValue(refreshed);
        assertThat(second).isNotBlank().isNotEqualTo(first);

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session(refreshed).accessToken()))
                .andExpect(status().isOk());

        // replaying the rotated token is treated as theft: rejected, and the newer token is revoked too
        refresh(first).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
        refresh(second).andExpect(status().isUnauthorized());

        Integer active = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked_at is null", Integer.class, session.userId());
        assertThat(active).isZero();
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        Session session = register(uniqueName("logout"));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .header(CSRF_HEADER, "1")
                        .cookie(new Cookie(REFRESH_COOKIE, session.refreshToken())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(setCookieHeader(result)).contains("Max-Age=0");

        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRequiresCsrfHeaderAndAllowedOrigin() throws Exception {
        Session session = register(uniqueName("csrf"));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, session.refreshToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CSRF_REJECTED"));
        mockMvc.perform(post("/api/v1/auth/refresh").header(CSRF_HEADER, "1").header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(new Cookie(REFRESH_COOKIE, session.refreshToken())))
                .andExpect(status().isForbidden());
        // the token was not consumed by the rejected attempts
        refresh(session.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void accessAndRefreshTokensAreNotInterchangeable() throws Exception {
        Session session = register(uniqueName("swap"));
        // refresh token used as a bearer access token
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + session.refreshToken()))
                .andExpect(status().isUnauthorized());
        // access token presented as the refresh cookie
        refresh(session.accessToken()).andExpect(status().isUnauthorized());
        // missing cookie
        mockMvc.perform(post("/api/v1/auth/refresh").header(CSRF_HEADER, "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordRevokesAllSessions() throws Exception {
        Session session = register(uniqueName("pwd"));
        mockMvc.perform(post("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"another-long-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CURRENT_PASSWORD"));
        mockMvc.perform(post("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"correct-horse-battery\",\"newPassword\":\"another-long-password\"}"))
                .andExpect(status().isOk());
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"another-long-password\"}".formatted(session.username())))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .header(CSRF_HEADER, "1")
                .header(HttpHeaders.ORIGIN, appProperties.getCors().allowedOriginList().get(0))
                .cookie(new Cookie(REFRESH_COOKIE, token)));
    }
}
