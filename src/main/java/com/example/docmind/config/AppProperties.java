package com.example.docmind.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("app")
@Getter
@Setter
public class AppProperties {
    private RagProperties rag = new RagProperties(); //make sure the name for object is same as in the yaml file
    private CorsProperties cors = new CorsProperties();

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CorsProperties{
        private String allowedOrigins = "*";
        private String allowedMethods = "GET,POST,PUT,DELETE,OPTIONS";
        private String allowedHeaders = "*";
    }
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RagProperties{
        private int chunkSize = 600;
        private int minChunkSizeChars = 350;
        private int minChunkLengthToEmbed = 5;
        private int maxNumChunks = 10000;
        //private int chunkOverlap = 100;
        private int topK = 5; // represents top k similar chunks to be chosen
        private double similarityThreshold = 0.0; // don't reject based on the similarity score
    }
}
