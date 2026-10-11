package com.example.docmind.service;

import com.example.docmind.entity.MessageRole;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the grounded prompt and calls the model through the shared ChatClient.
 * Retrieved text goes into the user turn as numbered, delimited sources; the system prompt
 * (ProjectConfig) tells the model to treat them as data and cite them by number.
 * Messages are passed as Message objects so document text is never run through a template engine.
 */
@Service
@RequiredArgsConstructor
public class RagService {
    private static final int MAX_HISTORY_CHARS = 2000;

    private final ChatClient chatClient;

    public record HistoryTurn(MessageRole role, String content) {
    }

    public String generate(String question, List<Document> sources, List<HistoryTurn> history) {
        return chatClient.prompt()
                .messages(buildMessages(question, sources, history))
                .call()
                .content();
    }

    public Flux<String> stream(String question, List<Document> sources, List<HistoryTurn> history) {
        return chatClient.prompt()
                .messages(buildMessages(question, sources, history))
                .stream()
                .content();
    }

    List<Message> buildMessages(String question, List<Document> sources, List<HistoryTurn> history) {
        List<Message> messages = new ArrayList<>();
        for (HistoryTurn turn : history) {
            String content = truncate(turn.content());
            messages.add(turn.role() == MessageRole.USER ? new UserMessage(content) : new AssistantMessage(content));
        }
        messages.add(new UserMessage(buildPrompt(question, sources)));
        return messages;
    }

    static String buildPrompt(String question, List<Document> sources) {
        StringBuilder sb = new StringBuilder();
        if (sources.isEmpty()) {
            sb.append("No passages from the user's documents matched this question.\n\n");
        } else {
            sb.append("Sources retrieved from the user's documents:\n<sources>\n");
            for (int i = 0; i < sources.size(); i++) {
                Document doc = sources.get(i);
                Map<String, Object> meta = doc.getMetadata();
                sb.append("<source id=\"").append(i + 1).append("\" file=\"")
                        .append(attribute(meta.getOrDefault(VectorMetadata.FILE_NAME, "unknown")))
                        .append('"');
                Object page = meta.get(VectorMetadata.PAGE_NUMBER);
                if (page != null) sb.append(" page=\"").append(attribute(page)).append('"');
                sb.append(">\n").append(neutralize(doc.getText())).append("\n</source>\n");
            }
            sb.append("</sources>\n\n");
        }
        sb.append("Question: ").append(question.strip());
        return sb.toString();
    }

    // stop document text from closing or opening source tags and escaping its delimiter
    private static String neutralize(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)<\\s*/?\\s*sources?\\b", "‹source");
    }

    private static String attribute(Object value) {
        return String.valueOf(value).replace("\"", "'").replace("<", "‹").replace(">", "›");
    }

    private static String truncate(String text) {
        return text.length() <= MAX_HISTORY_CHARS ? text : text.substring(0, MAX_HISTORY_CHARS) + "…";
    }
}
