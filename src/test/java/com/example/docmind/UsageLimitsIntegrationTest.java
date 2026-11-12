package com.example.docmind;

import com.example.docmind.service.KeepAliveService;
import com.example.docmind.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Small limits so the boundaries are cheap to reach; runs in its own application context.
@TestPropertySource(properties = {
        "app.documents.max-file-size=1MB",
        "app.documents.max-documents-per-user=3",
        "app.rate-limit.chat.requests=2",
        "app.rate-limit.search.requests=2"})
class UsageLimitsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ApplicationContext context;

    @Test
    void filesAboveTheSizeLimitAreRejectedBeforeProcessing() throws Exception {
        Session user = register(uniqueName("big"));
        byte[] tooLarge = new byte[1024 * 1024 + 1];
        java.util.Arrays.fill(tooLarge, (byte) 'a');
        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(new MockMultipartFile("file", "big.txt", "text/plain", tooLarge))
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Files larger than 1 MB are not accepted"));
        Integer rows = jdbcTemplate.queryForObject("select count(*) from documents where owner_id = ?", Integer.class, user.userId());
        assertThat(rows).isZero();
    }

    @Test
    void eachUserCanKeepAtMostThreeDocuments() throws Exception {
        Session user = register(uniqueName("quota"));
        UUID first = uploadText(user, "one.txt", "First document about apples.");
        uploadText(user, "two.txt", "Second document about pears.");
        uploadText(user, "three.txt", "Third document about plums.");

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(new MockMultipartFile("file", "four.txt", "text/plain", "Fourth document.".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("DOCUMENT_LIMIT_REACHED"));

        mockMvc.perform(get("/api/v1/users/me/limits").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxDocuments").value(3))
                .andExpect(jsonPath("$.data.documentsUsed").value(3))
                .andExpect(jsonPath("$.data.maxFileSizeBytes").value(1024 * 1024));

        // the quota is per user: someone else can still upload
        uploadText(register(uniqueName("other")), "mine.txt", "Another user's document.");

        // deleting a document frees a slot
        mockMvc.perform(delete("/api/v1/documents/" + first).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk());
        uploadText(user, "four.txt", "Fourth document, now allowed.");
    }

    @Test
    void chatIsRateLimitedPerUserBeforeAnythingIsSaved() throws Exception {
        Session user = register(uniqueName("chatty"));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/chat/query").header(HttpHeaders.AUTHORIZATION, user.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"question " + i + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/chat/query").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"one too many\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
        mockMvc.perform(post("/api/v1/chat/stream").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .header(HttpHeaders.ACCEPT, "text/event-stream, application/json")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"streamed\"}"))
                .andExpect(status().isTooManyRequests());

        // the rejected questions were not persisted and the model was not called for them
        Integer questions = jdbcTemplate.queryForObject(
                "select count(*) from messages m join conversations c on c.id = m.conversation_id where c.owner_id = ? and m.role = 'USER'",
                Integer.class, user.userId());
        assertThat(questions).isEqualTo(2);
        assertThat(chatModel.prompts()).hasSize(2);

        // another user has an independent budget
        Session other = register(uniqueName("quiet"));
        mockMvc.perform(post("/api/v1/chat/query").header(HttpHeaders.AUTHORIZATION, other.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hello\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void searchIsRateLimited() throws Exception {
        Session user = register(uniqueName("searcher"));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/chat/search/similarity").header(HttpHeaders.AUTHORIZATION, user.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"anything\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/chat/search/similarity").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"anything\"}"))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/api/v1/users/me/limits").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(jsonPath("$.data.search.remaining").value(0))
                .andExpect(jsonPath("$.data.chat.remaining").value(2));
    }

    @Test
    void keepAliveJobIsOffWhenNoUrlIsConfigured() {
        assertThat(context.getBeanNamesForType(KeepAliveService.class)).isEmpty();
    }
}
