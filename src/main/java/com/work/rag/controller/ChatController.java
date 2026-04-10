package com.work.rag.controller;

import com.work.rag.model.ChatRequest;
import com.work.rag.model.ChatResponse;
import com.work.rag.service.RagChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CHAT CONTROLLER
 * ─────────────────────────────────────────────────────────────────────────────
 * RAG sorgulama endpoint'i.
 *
 * POST /api/chat/query
 *   Content-Type: application/json
 *   Body: { "question": "Kredi başvurusu için hangi belgeler gerekli?" }
 *
 * Normal yanıt:
 *   {
 *     "answer": "Kredi başvurusu için...",
 *     "blocked": false,
 *     "blockedReason": null,
 *     "sources": [
 *       {
 *         "fileName": "banka_politikalari.pdf",
 *         "pageNumber": 12,
 *         "preview": "Kredi başvurusunda ibraz edilmesi gereken...",
 *         "rerankScore": 0.847
 *       }
 *     ]
 *   }
 *
 * Guardrails engeli:
 *   {
 *     "answer": null,
 *     "blocked": true,
 *     "blockedReason": "Bu konu (yatırım tavsiyesi) bu sistem tarafından yanıtlanamaz.",
 *     "sources": []
 *   }
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagChatService ragChatService;

    @PostMapping("/query")
    public ResponseEntity<ChatResponse> query(@RequestBody ChatRequest request) {
        ChatResponse response = ragChatService.chat(request.getQuestion());
        return ResponseEntity.ok(response);
    }
}
