package com.work.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient bean konfigürasyonu.
 *
 * ChatClient, Spring AI'ın fluent API'ıdır:
 *   chatClient.prompt().system(...).user(...).call().content()
 *
 * OpenAiChatModel Spring tarafından otomatik oluşturulur
 * (application.yml'deki spring.ai.openai.* ayarlarından).
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
