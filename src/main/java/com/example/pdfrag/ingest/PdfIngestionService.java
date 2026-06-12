package com.example.pdfrag.ingest;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class PdfIngestionService {

    private final SimpleVectorStore vectorStore;
    private final String vectorStorePath;

    public PdfIngestionService(SimpleVectorStore vectorStore,
                               @Value("${app.vectorstore.path:./data/vectorstore.json}") String vectorStorePath) {
        this.vectorStore = vectorStore;
        this.vectorStorePath = vectorStorePath;
    }

    /**
     * Reads a PDF, splits it into chunks, embeds them, and stores them in the vector store.
     *
     * @return the number of chunks that were indexed
     */
    public int ingest(Resource pdf, String documentId, String filename) {
        // 1) Read the PDF into Documents (one per page)
        PagePdfDocumentReader reader = new PagePdfDocumentReader(pdf);
        List<Document> docs = reader.get();

        // 2) Split into chunks
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(docs);

        // 3) Tag every chunk with documentId + source so we can filter later
        for (Document c : chunks) {
            c.getMetadata().put("documentId", documentId);
            c.getMetadata().putIfAbsent("source", filename);
        }

        // 4) Store (embeddings are generated locally by the ONNX transformers model)
        vectorStore.add(chunks);

        // 5) Persist the vector store to disk
        File file = new File(vectorStorePath);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        vectorStore.save(file);

        return chunks.size();
    }
}
