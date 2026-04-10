package com.work.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * DOCUMENT INGESTION SERVİSİ
 * ─────────────────────────────────────────────────────────────────────────────
 * PDF → Sayfalara böl → Chunk'lara ayır → Embed et → PGVector'a kaydet
 *
 * Akış:
 *   1. MultipartFile → Spring Resource'a dönüştür
 *   2. PagePdfDocumentReader ile sayfa sayfa oku (metadata: sayfa no, dosya adı)
 *   3. TokenTextSplitter ile chunk'lara böl (size + overlap application.yml'den)
 *   4. Her chunk'a dosya adı metadata'sı ekle
 *   5. VectorStore.add() → Spring AI embedding'i otomatik hesaplayıp kaydeder
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    @Value("${rag.chunk.size}")
    private int chunkSize;

    @Value("${rag.chunk.overlap}")
    private int chunkOverlap;

    /**
     * PDF dosyasını işleyip vector store'a kaydeder.
     *
     * @param file Yüklenen PDF
     * @return Oluşturulan chunk sayısı
     */
    public int ingestPdf(MultipartFile file) throws IOException {
        log.info("PDF ingestion başladı: {}, boyut: {} bytes", file.getOriginalFilename(), file.getSize());

        // 1. MultipartFile → Resource
        Resource pdfResource = file.getResource();

        // 2. PDF'i sayfa sayfa oku
        //    PdfDocumentReaderConfig ile her sayfanın metadata'sına page_number eklenir
        PdfDocumentReaderConfig readerConfig = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)  // Her sayfa ayrı Document olsun
                .build();

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource, readerConfig);
        List<Document> pages = pdfReader.get();
        log.info("PDF okundu: {} sayfa", pages.size());

        // 3. Chunk'lara böl
        //    TokenTextSplitter token sayısına göre böler (karakter değil),
        //    overlap sayesinde cümle sonu/başı bağlamı kaybolmaz
        TokenTextSplitter splitter = new TokenTextSplitter(
                chunkSize,    // defaultChunkSize
                chunkOverlap, // minChunkSizeChars (overlap için)
                5,            // minChunkLengthToEmbed
                10000,        // maxNumChunks
                true          // keepSeparator
        );
        List<Document> chunks = splitter.apply(pages);
        log.info("Chunk'lara bölündü: {} chunk", chunks.size());

        // 4. Her chunk'a dosya adı metadata'sı ekle
        //    (Reranker ve API yanıtında kaynak göstermek için kullanılır)
        String fileName = file.getOriginalFilename();
        chunks.forEach(chunk ->
            chunk.getMetadata().put("file_name", fileName)
        );

        // 5. VectorStore'a kaydet
        //    Spring AI bu noktada OpenAI Embeddings API'ını çağırır,
        //    dönen vektörleri chunk'larla birlikte PGVector'a yazar
        vectorStore.add(chunks);
        log.info("Vector store'a kaydedildi: {} chunk, dosya: {}", chunks.size(), fileName);

        return chunks.size();
    }
}
