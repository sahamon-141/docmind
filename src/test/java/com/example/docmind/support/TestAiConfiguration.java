package com.example.docmind.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Replaces the paid OpenAI models in tests only.
@TestConfiguration(proxyBeanMethods = false)
public class TestAiConfiguration {

    @Bean
    EmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel();
    }

    @Bean
    StubChatModel chatModel() {
        return new StubChatModel();
    }

    /**
     * Deterministic bag-of-words embedding (feature hashing into 1536 dimensions, L2-normalised), so texts
     * sharing words are close in cosine distance. Enough to exercise real pgvector search and filtering.
     */
    static class HashingEmbeddingModel implements EmbeddingModel {
        static final int DIMENSIONS = 1536;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> inputs = request.getInstructions();
            for (int i = 0; i < inputs.size(); i++) {
                embeddings.add(new Embedding(vector(inputs.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        static float[] vector(String text) {
            float[] v = new float[DIMENSIONS];
            for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (token.isEmpty()) continue;
                v[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1f;
            }
            double norm = 0;
            for (float f : v) norm += f * f;
            if (norm == 0) {
                v[0] = 1f;
                return v;
            }
            float inv = (float) (1 / Math.sqrt(norm));
            for (int i = 0; i < v.length; i++) v[i] *= inv;
            return v;
        }
    }
}
