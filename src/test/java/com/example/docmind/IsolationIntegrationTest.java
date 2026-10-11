package com.example.docmind;

import com.example.docmind.support.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two users upload documents with overlapping vocabulary. Every read, delete, vector search and chat
 * retrieval must only ever see the caller's own data.
 */
class IsolationIntegrationTest extends IntegrationTestBase {
    private static final String SHARED_TOPIC = "quarterly revenue forecast for the orbital telescope project";

    private Session alice;
    private Session bob;
    private UUID aliceDoc;
    private UUID bobDoc;

    @BeforeEach
    void setUp() throws Exception {
        alice = register(uniqueName("alice"));
        bob = register(uniqueName("bob"));
        aliceDoc = uploadText(alice, "alice-notes.txt", "Alice public notes. " + SHARED_TOPIC + " is twelve million.");
        bobDoc = uploadText(bob, "bob-secret.txt", "Bob confidential memo. " + SHARED_TOPIC + " is ninety million. BOBSECRET");
    }

    @Test
    void chunksAreStoredWithOwnerAndDocumentMetadata() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select metadata::jsonb ->> 'ownerId' as owner, metadata::jsonb ->> 'fileName' as file, metadata::jsonb ->> 'chunkIndex' as idx "
                        + "from vector_store where metadata::jsonb ->> 'documentId' = ?", bobDoc.toString());
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("owner")).isEqualTo(String.valueOf(bob.userId()));
            assertThat(row.get("file")).isEqualTo("bob-secret.txt");
            assertThat(row.get("idx")).isNotNull();
        });
    }

    @Test
    void listingOnlyReturnsOwnDocuments() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk()).andReturn();
        List<String> ids = JsonPath.read(json(result), "$.data[*].id");
        assertThat(ids).contains(aliceDoc.toString()).doesNotContain(bobDoc.toString());
        assertThat(json(result)).doesNotContain("bob-secret");
    }

    @Test
    void cannotReadOrDeleteAnotherUsersDocument() throws Exception {
        mockMvc.perform(get("/api/v1/documents/" + bobDoc).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/documents/" + bobDoc).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound());

        // Bob's document and its vectors are untouched
        mockMvc.perform(get("/api/v1/documents/" + bobDoc).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INDEXED"));
        assertThat(vectorCount(bobDoc)).isPositive();
    }

    @Test
    void ownerCanDeleteDocumentAndItsVectors() throws Exception {
        assertThat(vectorCount(aliceDoc)).isPositive();
        mockMvc.perform(delete("/api/v1/documents/" + aliceDoc).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk());
        assertThat(vectorCount(aliceDoc)).isZero();
        mockMvc.perform(get("/api/v1/documents/" + aliceDoc).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void similaritySearchNeverReturnsAnotherUsersChunks() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/chat/search/similarity").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"%s BOBSECRET confidential memo\",\"topK\":20}".formatted(SHARED_TOPIC)))
                .andExpect(status().isOk())
                .andReturn();
        String body = json(result);
        List<String> docIds = JsonPath.read(body, "$.data.matches[*].documentId");
        assertThat(docIds).isNotEmpty().allMatch(id -> id.equals(aliceDoc.toString()));
        String matches = JsonPath.parse(body).read("$.data.matches").toString();
        assertThat(matches).doesNotContain("BOBSECRET").doesNotContain("bob-secret");
        // internal metadata such as the owner id is not exposed
        assertThat(body).doesNotContain("ownerId");
    }

    @Test
    void documentScopedSearchEnforcesOwnership() throws Exception {
        mockMvc.perform(post("/api/v1/chat/search/similarity").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"revenue\",\"documentId\":\"%s\"}".formatted(bobDoc)))
                .andExpect(status().isNotFound());

        MvcResult own = mockMvc.perform(post("/api/v1/chat/search/similarity").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"revenue\",\"documentId\":\"%s\"}".formatted(aliceDoc)))
                .andExpect(status().isOk()).andReturn();
        List<String> ids = JsonPath.read(json(own), "$.data.matches[*].documentId");
        assertThat(ids).isNotEmpty().allMatch(id -> id.equals(aliceDoc.toString()));
    }

    @Test
    void searchWithNoIndexedDocumentsReturnsEmptyResult() throws Exception {
        Session carol = register(uniqueName("carol"));
        mockMvc.perform(post("/api/v1/chat/search/similarity").header(HttpHeaders.AUTHORIZATION, carol.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"%s\"}".formatted(SHARED_TOPIC)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMatches").value(0))
                .andExpect(jsonPath("$.data.matches").isEmpty());
    }

    @Test
    void chatRetrievalOnlyUsesOwnDocuments() throws Exception {
        mockMvc.perform(post("/api/v1/chat/query").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is the %s? BOBSECRET\"}".formatted(SHARED_TOPIC)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assistantMessage.citations[0].documentId").value(aliceDoc.toString()));

        String prompt = chatModel.lastUserText();
        assertThat(prompt).contains("alice-notes.txt").doesNotContain("BOBSECRET confidential").doesNotContain("bob-secret.txt");
    }

    @Test
    void chatScopedToAnotherUsersDocumentIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/chat/query").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"summarise\",\"documentId\":\"%s\"}".formatted(bobDoc)))
                .andExpect(status().isNotFound());
        assertThat(chatModel.prompts()).isEmpty();
    }

    @Test
    void conversationsAndMessagesAreIsolated() throws Exception {
        MvcResult asked = mockMvc.perform(post("/api/v1/chat/query").header(HttpHeaders.AUTHORIZATION, bob.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Bob private question about the memo\"}"))
                .andExpect(status().isOk()).andReturn();
        String bobConversation = JsonPath.read(json(asked), "$.data.conversation.id");

        mockMvc.perform(get("/api/v1/conversations/" + bobConversation).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/conversations/" + bobConversation + "/messages").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/conversations/" + bobConversation).header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"hijacked\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/conversations/" + bobConversation).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isNotFound());
        // posting into someone else's conversation must not create or append anything
        mockMvc.perform(post("/api/v1/chat/query").header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"sneaky\",\"conversationId\":\"%s\"}".formatted(bobConversation)))
                .andExpect(status().isNotFound());

        MvcResult list = mockMvc.perform(get("/api/v1/conversations").param("q", "private question")
                        .header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(status().isOk()).andReturn();
        assertThat(JsonPath.<List<Object>>read(json(list), "$.data.content")).isEmpty();

        mockMvc.perform(get("/api/v1/conversations/" + bobConversation).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.title").value("Bob private question about the memo"));
    }

    private int vectorCount(UUID documentId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from vector_store where metadata::jsonb ->> 'documentId' = ?", Integer.class, documentId.toString());
        return count == null ? 0 : count;
    }
}
