package com.example.pdfrag.api;

import com.example.pdfrag.ingest.PdfIngestionService;
import com.example.pdfrag.rag.RagService;
import com.example.pdfrag.store.DocumentRegistry;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RagController {

    private final DocumentRegistry documentRegistry;
    private final PdfIngestionService ingestionService;
    private final RagService ragService;

    public RagController(DocumentRegistry documentRegistry,
                         PdfIngestionService ingestionService,
                         RagService ragService) {
        this.documentRegistry = documentRegistry;
        this.ingestionService = ingestionService;
        this.ragService = ragService;
    }

    @PostMapping("/ingest-pdf")
    public Map<String, Object> ingestPdf(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }
        String filename = file.getOriginalFilename() == null ? "uploaded.pdf" : file.getOriginalFilename();

        String documentId = documentRegistry.newId();
        Resource resource = file.getResource();

        int chunkCount = ingestionService.ingest(resource, documentId, filename);
        DocumentRegistry.DocumentInfo doc = documentRegistry.save(documentId, filename, chunkCount);

        return Map.of(
                "documentId", doc.documentId(),
                "filename", doc.filename(),
                "chunkCount", doc.chunkCount(),
                "status", "indexed"
        );
    }

    @GetMapping("/documents")
    public List<DocumentRegistry.DocumentInfo> documents() {
        return documentRegistry.list();
    }

    public record AskRequest(@NotBlank String documentId, @NotBlank String question) {}

    @PostMapping("/ask")
    public RagService.AskResponse ask(@RequestBody AskRequest req) {
        // Ensure documentId exists (clear error if missing)
        documentRegistry.getOrThrow(req.documentId());
        return ragService.ask(req.documentId(), req.question());
    }
}
