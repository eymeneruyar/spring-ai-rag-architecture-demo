package com.work.rag.controller;

import com.work.rag.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * DOCUMENT CONTROLLER
 * ─────────────────────────────────────────────────────────────────────────────
 * PDF yükleme endpoint'i.
 *
 * POST /api/documents/upload
 *   Content-Type: multipart/form-data
 *   Body: file=<pdf>
 *
 * Başarılı yanıt:
 *   {
 *     "message": "PDF başarıyla işlendi.",
 *     "fileName": "banka_politikalari.pdf",
 *     "chunksCreated": 42
 *   }
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file) throws IOException {

        // Basit validasyon
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Dosya boş olamaz."));
        }
        if (!isPdf(file)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Sadece PDF dosyaları kabul edilmektedir."));
        }

        int chunks = ingestionService.ingestPdf(file);

        return ResponseEntity.ok(Map.of(
                "message", "PDF başarıyla işlendi.",
                "fileName", file.getOriginalFilename(),
                "chunksCreated", chunks
        ));
    }

    private boolean isPdf(MultipartFile file) {
        String name = file.getOriginalFilename();
        String contentType = file.getContentType();
        return (name != null && name.toLowerCase().endsWith(".pdf"))
                || "application/pdf".equals(contentType);
    }
}
