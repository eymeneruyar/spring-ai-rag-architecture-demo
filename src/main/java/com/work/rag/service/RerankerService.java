package com.work.rag.service;

import com.work.rag.model.SourceChunk;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RERANKER
 * ─────────────────────────────────────────────────────────────────────────────
 * Vector search ilk aşamada top-K belge getirir (örn. 10).
 * Bu servis, getirilen chunk'ları soruyla daha derin biçimde karşılaştırıp
 * en alakalı top-N'i seçer (örn. 3).
 *
 * PRODUCTION notu:
 *   Gerçek bir reranker için Cohere Rerank API veya cross-encoder modeli
 *   (örn. BAAI/bge-reranker-base) kullanılır. Bu PoC'de harici API
 *   bağımlılığını ortadan kaldırmak için skor, kelime örtüşmesi + TF ağırlıklı
 *   basit bir heuristik ile hesaplanır. Mantık aynı, model değişebilir.
 *
 * Adımlar:
 *   1. Her chunk için skor hesapla (scoreChunk)
 *   2. Skora göre azalan sırala
 *   3. rerank-top-k kadar al
 *   4. SourceChunk listesi olarak döndür (API yanıtında görünür)
 */
@Service
public class RerankerService {

    @Value("${rag.retrieval.rerank-top-k}")
    private int rerankTopK;

    /**
     * @param question Kullanıcının orijinal sorusu
     * @param candidates Vector search'ten gelen aday chunk'lar
     * @return Rerank edilmiş ve kesilmiş liste (max rerankTopK eleman)
     */
    public List<Document> rerank(String question, List<Document> candidates) {
        String[] queryTokens = tokenize(question);

        return candidates.stream()
                .sorted(Comparator.comparingDouble(
                    doc -> -scoreChunk(queryTokens, doc.getText())  // azalan sıra için negatif
                ))
                .limit(rerankTopK)
                .collect(Collectors.toList());
    }

    /**
     * Reranked belgeleri SourceChunk listesine dönüştürür (API yanıtı için).
     */
    public List<SourceChunk> toSourceChunks(String question, List<Document> reranked) {
        String[] queryTokens = tokenize(question);

        return reranked.stream().map(doc -> {
            double score = scoreChunk(queryTokens, doc.getText());
            String fileName = (String) doc.getMetadata().getOrDefault("file_name", "bilinmiyor");
            Object pageObj  = doc.getMetadata().get("page_number");
            Integer page    = pageObj != null ? Integer.parseInt(pageObj.toString()) : null;
            String preview  = doc.getText().length() > 150
                    ? doc.getText().substring(0, 150) + "..."
                    : doc.getText();

            return SourceChunk.builder()
                    .fileName(fileName)
                    .pageNumber(page)
                    .preview(preview)
                    .rerankScore(Math.round(score * 1000.0) / 1000.0)
                    .build();
        }).collect(Collectors.toList());
    }

    // ── Özel metodlar ────────────────────────────────────────────────────────

    /**
     * Heuristik skor:
     *   - Her soru token'ı için chunk'ta geçiş frekansını say
     *   - Kısa token'lara (stop-word benzeri) daha düşük ağırlık ver
     *   - Normalize et (toplam token sayısına böl)
     */
    private double scoreChunk(String[] queryTokens, String chunkText) {
        if (chunkText == null || chunkText.isBlank()) return 0.0;
        String lowerChunk = chunkText.toLowerCase();
        double score = 0.0;

        for (String token : queryTokens) {
            if (token.length() < 3) continue; // çok kısa token'ları atla
            double weight = token.length() > 5 ? 1.5 : 1.0; // uzun kelimeler daha değerli
            int count = countOccurrences(lowerChunk, token);
            score += count * weight;
        }

        // Uzun chunk'lara ceza (normalize)
        double lengthPenalty = Math.log(chunkText.length() + 1);
        return score / lengthPenalty;
    }

    private String[] tokenize(String text) {
        return text.toLowerCase()
                   .replaceAll("[^a-zA-ZğüşıöçĞÜŞİÖÇ0-9\\s]", " ")
                   .trim()
                   .split("\\s+");
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }
}
