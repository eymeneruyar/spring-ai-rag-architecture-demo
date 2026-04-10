package com.work.rag.model;

import lombok.Builder;
import lombok.Data;

/**
 * Reranking sonrası LLM'e gönderilen her chunk'ın özeti.
 * API yanıtında "sources" dizisi olarak dönülür — hangi belgeden,
 * hangi sayfadan geldiğini ve rerank skorunu gösterir.
 */
@Data
@Builder
public class SourceChunk {

    /** Kaynak PDF dosya adı (metadata'dan alınır) */
    private String fileName;

    /** PDF sayfa numarası (varsa) */
    private Integer pageNumber;

    /** Chunk metninin ilk 150 karakteri (önizleme) */
    private String preview;

    /**
     * Reranker'ın atadığı 0-1 arası kalite skoru.
     * 1.0'a yakın → soruyla çok alakalı.
     */
    private double rerankScore;
}
