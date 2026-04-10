package com.work.rag.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatResponse {

    private String answer;

    /** Guardrails tarafından engellendi mi? */
    private boolean blocked;

    /** Engellendiyse kullanıcıya gösterilen mesaj */
    private String blockedReason;

    /** LLM'e gönderilen kaynak chunk'ların kısa özeti (şeffaflık için) */
    private List<SourceChunk> sources;
}
