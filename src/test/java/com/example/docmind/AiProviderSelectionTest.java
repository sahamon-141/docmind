package com.example.docmind;

import com.example.docmind.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI_PROVIDER selects which provider serves chat and embeddings. Uses dummy keys: no model is called,
 * only the wiring is checked (exactly one ChatModel and one EmbeddingModel, of the selected provider).
 */
class AiProviderSelectionTest {

    @Nested
    @SpringBootTest(properties = {
            "spring.ai.model.chat=google-genai",
            "spring.ai.model.embedding=google-genai",
            "spring.ai.model.embedding.text=google-genai",
            "GEMINI_API_KEY=dummy-gemini-key"})
    @ActiveProfiles("test")
    @Import(TestcontainersConfiguration.class)
    class Gemini {
        @Autowired
        ApplicationContext context;
        @Autowired
        GoogleGenAiTextEmbeddingProperties embeddingProperties;

        @Test
        void usesGeminiForChatAndEmbeddingsWithSchemaCompatibleDimensions() {
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(GoogleGenAiChatModel.class);
            assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(GoogleGenAiTextEmbeddingModel.class);
            assertThat(context.getBeanNamesForType(OpenAiChatModel.class)).isEmpty();
            assertThat(embeddingProperties.getDimensions()).isEqualTo(1536);
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "spring.ai.model.chat=openai",
            "spring.ai.model.embedding=openai",
            "spring.ai.model.embedding.text=openai",
            "OPENAI_API_KEY=dummy-openai-key"})
    @ActiveProfiles("test")
    @Import(TestcontainersConfiguration.class)
    class OpenAi {
        @Autowired
        ApplicationContext context;

        @Test
        void usesOpenAiByDefaultWithoutAGeminiKey() {
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);
            assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(OpenAiEmbeddingModel.class);
            assertThat(context.getBeanNamesForType(GoogleGenAiChatModel.class)).isEmpty();
        }
    }
}
