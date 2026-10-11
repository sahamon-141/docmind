package com.example.docmind.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Test double for the chat model: records every prompt and can be switched into failure mode.
public class StubChatModel implements ChatModel {
    public static final String ANSWER_WITH_SOURCES = "According to your documents the answer is here [1].";
    public static final String ANSWER_WITHOUT_SOURCES = "I could not find this in your documents.";

    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    public void reset() {
        prompts.clear();
        failing = false;
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public List<Prompt> prompts() {
        return prompts;
    }

    public String lastUserText() {
        Prompt last = prompts.get(prompts.size() - 1);
        return last.getInstructions().get(last.getInstructions().size() - 1).getText();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        if (failing) throw new IllegalStateException("simulated model outage");
        return response(answerFor(prompt));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        prompts.add(prompt);
        if (failing) return Flux.error(new IllegalStateException("simulated model outage"));
        String answer = answerFor(prompt);
        int mid = answer.length() / 2;
        return Flux.just(answer.substring(0, mid), answer.substring(mid)).map(StubChatModel::response);
    }

    private static String answerFor(Prompt prompt) {
        String text = prompt.getInstructions().get(prompt.getInstructions().size() - 1).getText();
        return text.contains("<source id=\"1\"") ? ANSWER_WITH_SOURCES : ANSWER_WITHOUT_SOURCES;
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
