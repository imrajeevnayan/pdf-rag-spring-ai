package com.example.pdfrag.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RagService {

    public record Citation(String source, Integer page, String snippet) {}
    public record AskResponse(String answer, List<Citation> citations, String confidence, int usedChunksCount) {}

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    private final int topK;
    private final int maxSnippetChars;

    public RagService(VectorStore vectorStore,
                      ChatClient.Builder chatClientBuilder,
                      @Value("${app.rag.top-k:5}") int topK,
                      @Value("${app.rag.max-snippet-chars:350}") int maxSnippetChars) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.topK = topK;
        this.maxSnippetChars = maxSnippetChars;
    }

    public AskResponse ask(String documentId, String question) {
        // Retrieve a wider set, then filter by documentId in Java.
        // (SimpleVectorStore does not support metadata filter expressions.)
        SearchRequest req = SearchRequest.builder()
                .query(question)
                .topK(Math.max(topK * 4, 20))
                .build();

        List<Document> all = vectorStore.similaritySearch(req);
        if (all == null) all = List.of();

        List<Document> results = new ArrayList<>();
        for (Document d : all) {
            Object docId = d.getMetadata() == null ? null : d.getMetadata().get("documentId");
            if (Objects.equals(documentId, docId == null ? null : docId.toString())) {
                results.add(d);
            }
            if (results.size() >= topK) break;
        }

        if (results.isEmpty()) {
            return new AskResponse(
                    "I don't have enough information in the provided document.",
                    List.of(),
                    "low",
                    0
            );
        }

        StringBuilder ctx = new StringBuilder();
        List<Citation> citations = new ArrayList<>();

        for (Document d : results) {
            Map<String, Object> md = d.getMetadata();
            String source = md != null && md.get("source") != null ? md.get("source").toString() : "unknown";

            Integer page = null;
            Object pageObj = md != null ? md.get("page_number") : null;
            if (pageObj instanceof Number n) page = n.intValue();

            String text = d.getText() == null ? "" : d.getText();
            String snippet = text.length() > maxSnippetChars ? text.substring(0, maxSnippetChars) + "..." : text;
            citations.add(new Citation(source, page, snippet));

            ctx.append("SOURCE: ").append(source);
            if (page != null) ctx.append(" (page ").append(page).append(")");
            ctx.append("\n").append(text).append("\n\n---\n\n");
        }

        String prompt = """
                You are a helpful assistant. Answer the QUESTION using ONLY the CONTEXT.
                If the answer is not in the context, say: "I don't have enough information in the provided document."
                Keep the answer concise and factual.

                CONTEXT:
                %s

                QUESTION:
                %s
                """.formatted(ctx.toString(), question);

        String answer = chatClient.prompt(prompt).call().content();

        String confidence = "medium";
        if (answer != null && answer.toLowerCase().contains("don't have enough information")) {
            confidence = "low";
        }

        return new AskResponse(answer, citations, confidence, results.size());
    }
}
