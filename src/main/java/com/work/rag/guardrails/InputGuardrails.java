package com.work.rag.guardrails;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * INPUT GUARDRAILS
 * ─────────────────────────────────────────────────────────────────────────────
 * Kullanıcının sorusu LLM'e ulaşmadan önce bu sınıftan geçer.
 *
 * Kontrol sırası:
 *   1. Boş/null soru → red
 *   2. Çok kısa soru (anlamsız girdi koruması) → red
 *   3. Yasaklı konu listesi (application.yml'den okunur) → red
 *
 * Gerçek bir projede buraya şunlar da eklenebilir:
 *   - PII tespiti (TC kimlik no, kredi kartı no vb.)
 *   - Prompt injection pattern matching
 *   - Rate limiting (kullanıcı başına istek sınırı)
 */
@Component
public class InputGuardrails {

    @Value("${rag.guardrails.blocked-topics}")
    private List<String> blockedTopics;

    /**
     * Soruyu denetler.
     *
     * @param question Kullanıcının sorusu
     * @return Optional.empty() → soru temiz, devam et
     *         Optional.of("...") → engellendi, içerik = kullanıcıya gösterilecek mesaj
     */
    public Optional<String> check(String question) {

        // 1. Boş kontrolü
        if (question == null || question.isBlank()) {
            return Optional.of("Soru boş olamaz.");
        }

        // 2. Çok kısa girdi
        if (question.strip().length() < 5) {
            return Optional.of("Lütfen daha açıklayıcı bir soru giriniz.");
        }

        // 3. Yasaklı konu taraması (büyük/küçük harf duyarsız)
        String lowerQuestion = question.toLowerCase();
        for (String topic : blockedTopics) {
            if (lowerQuestion.contains(topic.toLowerCase())) {
                return Optional.of(
                    "Bu konu (" + topic + ") bu sistem tarafından yanıtlanamaz. " +
                    "Lütfen yüklenen belgeler hakkında soru sorunuz."
                );
            }
        }

        return Optional.empty();
    }
}
