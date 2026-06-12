package com.example.pdfrag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class VectorStoreConfig {

    /**
     * In-memory vector store (no external DB / no Docker required).
     * It is persisted to a JSON file so embeddings survive restarts.
     */
    @Bean
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel,
                                         @Value("${app.vectorstore.path:vectorstore.json}") String path) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();

        File file = new File(path);
        if (file.exists()) {
            store.load(file);
        }
        return store;
    }
}
