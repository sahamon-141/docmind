package com.example.docmind;

import com.example.docmind.support.IntegrationTestBase;
import com.example.docmind.support.StubChatModel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatIntegrationTest extends IntegrationTestBase {

    private Session user;
    private UUID document;

    @BeforeEach
    void setUp() throws Exception {
        user = register(uniqueName("chat"));
        document = uploadText(user, "handbook.txt", "The employee handbook says vacation requests need two weeks notice.");
    }

    @Test
    void askPersistsConversationMessagesAndCitations() throws Exception {
        MvcResult result = ask("{\"question\":\"How much notice do vacation requests need?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.data.assistantMessage.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.assistantMessage.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.assistantMessage.content").value(StubChatModel.ANSWER_WITH_SOURCES))
                .andExpect(jsonPath("$.data.assistantMessage.responseTimeMs").isNumber())
                .andExpect(jsonPath("$.data.assistantMessage.citations[0].position").value(1))
                .andExpect(jsonPath("$.data.assistantMessage.citations[0].fileName").value("handbook.txt"))
                .andExpect(jsonPath("$.data.assistantMessage.citations[0].documentId").value(document.toString()))
                .andReturn();
        String conversationId = JsonPath.read(json(result), "$.data.conversation.id");
        long responseTime = ((Number) JsonPath.read(json(result), "$.data.assistantMessage.responseTimeMs")).longValue();
        assertThat(responseTime).isNotNegative();

        // a follow-up in the same conversation sees the previous turn as history
        ask("{\"question\":\"And for sick leave?\",\"conversationId\":\"%s\"}".formatted(conversationId)).andExpect(status().isOk());
        assertThat(chatModel.prompts().get(1).getInstructions()).hasSizeGreaterThanOrEqualTo(3);

        // reloaded from the database, in chronological order, citations attached to the assistant reply
        mockMvc.perform(get("/api/v1/conversations/" + conversationId).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(4))
                .andExpect(jsonPath("$.data.messages[0].content").value("How much notice do vacation requests need?"))
                .andExpect(jsonPath("$.data.messages[1].citations[0].fileName").value("handbook.txt"))
                .andExpect(jsonPath("$.data.messages[1].replyToId").isNotEmpty())
                .andExpect(jsonPath("$.data.messages[2].content").value("And for sick leave?"))
                .andExpect(jsonPath("$.data.messages[0].citations").isEmpty());

        Integer rows = jdbcTemplate.queryForObject("select count(*) from messages where conversation_id = ?::uuid", Integer.class, conversationId);
        assertThat(rows).isEqualTo(4);

        mockMvc.perform(get("/api/v1/conversations/" + conversationId + "/messages").param("page", "1").param("size", "3")
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(4));
    }

    @Test
    void conversationTitleIsDerivedFromFirstQuestionAndCanBeRenamedSearchedAndDeleted() throws Exception {
        MvcResult result = ask("{\"question\":\"Summarise the vacation policy please\"}").andExpect(status().isOk()).andReturn();
        String id = JsonPath.read(json(result), "$.data.conversation.id");
        assertThat(JsonPath.<String>read(json(result), "$.data.conversation.title")).isEqualTo("Summarise the vacation policy please");

        mockMvc.perform(patch("/api/v1/conversations/" + id).header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Leave policy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Leave policy"));

        // search matches the new title and the message content
        mockMvc.perform(get("/api/v1/conversations").param("q", "LEAVE POL").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(jsonPath("$.data.content[0].id").value(id));
        mockMvc.perform(get("/api/v1/conversations").param("q", "vacation policy").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(jsonPath("$.data.content[0].id").value(id));
        mockMvc.perform(get("/api/v1/conversations").param("q", "100%_").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(jsonPath("$.data.content").isEmpty());

        mockMvc.perform(delete("/api/v1/conversations/" + id).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/conversations/" + id).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isNotFound());
        Integer orphans = jdbcTemplate.queryForObject("select count(*) from messages where conversation_id = ?::uuid", Integer.class, id);
        assertThat(orphans).isZero();
    }

    @Test
    void emptyConversationCanBeCreatedAndListed() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/conversations").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("New conversation"))
                .andReturn();
        String id = JsonPath.read(json(created), "$.data.id");
        mockMvc.perform(get("/api/v1/conversations").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(jsonPath("$.data.content[0].id").value(id));
    }

    @Test
    void failedGenerationKeepsTheQuestionAndCanBeRetried() throws Exception {
        chatModel.setFailing(true);
        MvcResult failed = ask("{\"question\":\"What does the handbook say?\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("AI_GENERATION_FAILED"))
                .andExpect(jsonPath("$.data.assistantMessage.status").value("FAILED"))
                .andReturn();
        String conversationId = JsonPath.read(json(failed), "$.data.conversation.id");
        String userMessageId = JsonPath.read(json(failed), "$.data.userMessage.id");
        assertThat(json(failed)).doesNotContain("simulated model outage");

        chatModel.setFailing(false);
        ask("{\"conversationId\":\"%s\",\"retryOfMessageId\":\"%s\"}".formatted(conversationId, userMessageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userMessage.id").value(userMessageId))
                .andExpect(jsonPath("$.data.assistantMessage.status").value("COMPLETE"));

        // the failed reply was replaced, the question was not duplicated
        mockMvc.perform(get("/api/v1/conversations/" + conversationId).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[1].status").value("COMPLETE"));

        // a message that already has an answer cannot be retried again
        ask("{\"conversationId\":\"%s\",\"retryOfMessageId\":\"%s\"}".formatted(conversationId, userMessageId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_ANSWERED"));
    }

    @Test
    void duplicateClientMessageIdIsRejected() throws Exception {
        MvcResult first = ask("{\"question\":\"hello\",\"clientMessageId\":\"%s\"}".formatted(UUID.randomUUID()))
                .andExpect(status().isOk()).andReturn();
        String conversationId = JsonPath.read(json(first), "$.data.conversation.id");
        String key = UUID.randomUUID().toString();
        ask("{\"question\":\"again\",\"conversationId\":\"%s\",\"clientMessageId\":\"%s\"}".formatted(conversationId, key))
                .andExpect(status().isOk());
        ask("{\"question\":\"again\",\"conversationId\":\"%s\",\"clientMessageId\":\"%s\"}".formatted(conversationId, key))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_MESSAGE"));
    }

    @Test
    void questionWithoutRelevantContextIsAnsweredWithoutCitations() throws Exception {
        Session fresh = register(uniqueName("empty"));
        mockMvc.perform(post("/api/v1/chat/query").header(HttpHeaders.AUTHORIZATION, fresh.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"What is in my documents?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assistantMessage.content").value(StubChatModel.ANSWER_WITHOUT_SOURCES))
                .andExpect(jsonPath("$.data.assistantMessage.citations").isEmpty());
        assertThat(chatModel.lastUserText()).contains("No passages from the user's documents matched");
    }

    @Test
    void blankQuestionIsRejected() throws Exception {
        ask("{\"question\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void streamingEmitsEventsAndPersistsTheAnswer() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/chat/stream").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"question\":\"Stream the vacation rule\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());

        String body = started.getResponse().getContentAsString();
        assertThat(body).contains("event:meta").contains("event:sources").contains("event:token").contains("event:done");
        assertThat(body.indexOf("event:meta")).isLessThan(body.indexOf("event:token"));
        assertThat(body.indexOf("event:token")).isLessThan(body.indexOf("event:done"));

        Integer complete = jdbcTemplate.queryForObject(
                "select count(*) from messages m join conversations c on c.id = m.conversation_id "
                        + "where c.owner_id = ? and m.role = 'ASSISTANT' and m.status = 'COMPLETE' and m.content = ?",
                Integer.class, user.userId(), StubChatModel.ANSWER_WITH_SOURCES);
        assertThat(complete).isEqualTo(1);
    }

    @Test
    void streamingRequestRejectedBeforeStreamingReturnsJsonError() throws Exception {
        Session other = register(uniqueName("other"));
        UUID foreignDocument = uploadText(other, "foreign.txt", "Someone else's private notes.");
        mockMvc.perform(post("/api/v1/chat/stream").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.ACCEPT, "text/event-stream, application/json")
                        .content("{\"question\":\"q\",\"documentId\":\"%s\"}".formatted(foreignDocument)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void streamingFailureEmitsErrorEventAndPersistsFailedMessage() throws Exception {
        chatModel.setFailing(true);
        MvcResult started = mockMvc.perform(post("/api/v1/chat/stream").header(HttpHeaders.AUTHORIZATION, user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"This will fail\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started));
        assertThat(started.getResponse().getContentAsString()).contains("event:error").doesNotContain("event:done");
        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from messages m join conversations c on c.id = m.conversation_id "
                        + "where c.owner_id = ? and m.status = 'FAILED'", Integer.class, user.userId());
        assertThat(failed).isEqualTo(1);
    }

    @Test
    void unsupportedAndInvalidUploadsAreRejectedSafely() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        upload("image.png", png).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_DOCUMENT"));
        // binary content disguised with a .pdf extension
        upload("fake.pdf", png).andExpect(status().isUnsupportedMediaType());
        upload("empty.txt", new byte[0]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("EMPTY_FILE"));

        // readable type but no text: recorded as FAILED, never as INDEXED
        upload("blank.txt", "   \n\n   ".getBytes()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("DOCUMENT_PROCESSING_FAILED"))
                .andExpect(jsonPath("$.data.status").value("FAILED"));
        MvcResult list = mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, user.bearer())).andReturn();
        List<String> statuses = JsonPath.read(json(list), "$.data[?(@.filename == 'blank.txt')].status");
        assertThat(statuses).containsExactly("FAILED");
    }

    @Test
    void multiUploadReportsPerFileResults() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents/upload-multiple")
                        .file(new org.springframework.mock.web.MockMultipartFile("files", "a.md", "text/markdown", "# Title\nSome markdown content here.".getBytes()))
                        .file(new org.springframework.mock.web.MockMultipartFile("files", "b.exe", "application/octet-stream", new byte[]{'M', 'Z', 0, 0}))
                        .header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("INDEXED"))
                .andExpect(jsonPath("$.data[1].status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("1 of 2 documents failed"));
    }

    private org.springframework.test.web.servlet.ResultActions ask(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/chat/query").header(HttpHeaders.AUTHORIZATION, user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions upload(String name, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/v1/documents/upload")
                .file(new org.springframework.mock.web.MockMultipartFile("file", name, "application/octet-stream", content))
                .header(HttpHeaders.AUTHORIZATION, user.bearer()));
    }
}
