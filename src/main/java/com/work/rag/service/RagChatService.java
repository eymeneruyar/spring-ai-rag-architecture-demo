package com.work.rag.service;

import com.work.rag.guardrails.InputGuardrails;
import com.work.rag.model.ChatResponse;
import com.work.rag.model.SourceChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * RAG PIPELINE SERVİSİ
 * ─────────────────────────────────────────────────────────────────────────────
 * Tam RAG akışı by sınıfta yönetilir:
 *
 *   [Kullanıcı Sorusu]
 *        │
 *        ▼
 *   [1] INPUT GUARDRAILS → yasaklı konu mu?
 *        │ temiz
 *        ▼
 *   [2] VECTOR SEARCH → top-K en yakın chunk'ı getir
 *        │
 *        ▼
 *   [3] RERANKING → top-K'yı puanla, top-N'i seç
 *        │
 *        ▼
 *   [4] PROMPT OLUŞTUR → sistem promptu + bağlam + soru
 *        │
 *        ▼
 *   [5] LLM (gpt-4o-mini) → yanıt üret
 *        │
 *        ▼
 *   [ChatResponse] → answer + sources + blocked flag
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final RerankerService rerankerService;
    private final InputGuardrails inputGuardrails;

    @Value("${rag.retrieval.top-k}")
    private int topK;

    /**
     * Ana RAG metodu. Controller bu metodu çağırır.
     */
    public ChatResponse chat(String question) {

        // ── ADIM 1: Input Guardrails ────────────────────────────────────────
        Optional<String> guardrailViolation = inputGuardrails.check(question);
        if (guardrailViolation.isPresent()) {
            log.warn("Guardrails engelledi: '{}' → {}", question, guardrailViolation.get());
            return ChatResponse.builder()
                    .blocked(true)
                    .blockedReason(guardrailViolation.get())
                    .answer(null)
                    .sources(List.of())
                    .build();
        }

        // ── ADIM 2: Vector Search ───────────────────────────────────────────
        // Spring AI soruyu otomatik embed edip cosine similarity ile arar
        List<Document> candidates = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(topK)
                        .build()
        );
        log.info("Vector search: {} aday chunk bulundu", candidates.size());

        if (candidates.isEmpty()) {
            return ChatResponse.builder()
                    .blocked(false)
                    .answer("Yüklenen belgelerde bu soruyla ilgili bilgi bulunamadı.")
                    .sources(List.of())
                    .build();
        }

        // ── ADIM 3: Reranking ───────────────────────────────────────────────
        List<Document> reranked = rerankerService.rerank(question, candidates);
        List<SourceChunk> sources = rerankerService.toSourceChunks(question, reranked);
        log.info("Reranking tamamlandı: {} chunk LLM'e gönderilecek", reranked.size());

        // ── ADIM 4: Prompt Oluşturma ────────────────────────────────────────
        String context = buildContext(reranked);

        // ── ADIM 5: LLM Çağrısı ────────────────────────────────────────────
        String answer = chatClient.prompt()
                .system(systemPrompt())
                .user(userMessage(question, context))
                .call()
                .content();

        log.info("LLM yanıtı alındı ({} karakter)", answer.length());

        return ChatResponse.builder()
                .blocked(false)
                .answer(answer)
                .sources(sources)
                .build();
    }

    // ── Yardımcı metodlar ────────────────────────────────────────────────────

    /**
     * Reranked chunk'ları numaralı bağlam metnine dönüştürür.
     * LLM'e hangi kaynaktan alıntı yapacağını gösterir.
     */
    private String buildContext(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String fileName = (String) doc.getMetadata().getOrDefault("file_name", "belge");
            Object page = doc.getMetadata().get("page_number");
            sb.append(String.format("[Kaynak %d] %s%s\n",
                    i + 1,
                    fileName,
                    page != null ? " (sayfa " + page + ")" : ""));
            sb.append(doc.getText());
            sb.append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Sistem promptu: LLM'in rolünü ve davranış kurallarını tanımlar.
     * - Sadece sağlanan bağlamı kullan (hallüsinasyon önleme)
     * - Bilmiyorsan belirt
     * - Kaynağa atıfta bulun
     */
    private String systemPrompt() {
        return """
                Sen yüklenen PDF belgelerine dayalı çalışan bir belge sorgulama asistanısın.
                
                KURALLAR:
                1. YALNIZCA aşağıda verilen bağlam bilgisini kullan. Kendi bilginle yanıt üretme.
                2. Bağlamda cevap yoksa "Bu bilgi yüklenen belgelerde yer almıyor." de.
                3. Yanıtında hangi kaynaktan (Kaynak 1, Kaynak 2 vb.) bilgi aldığını belirt.
                4. Türkçe soru gelirse Türkçe, İngilizce soru gelirse İngilizce yanıt ver.
                5. Spekülasyon yapma, sadece belgede yazan bilgiyi aktar.
                """;
    }

    /**
     * Kullanıcı mesajı: bağlamı ve soruyu bir araya getirir.
     */
    private String userMessage(String question, String context) {
        return String.format("""
                BAĞLAM:
                %s
                
                SORU: %s
                """, context, question);
    }
}
