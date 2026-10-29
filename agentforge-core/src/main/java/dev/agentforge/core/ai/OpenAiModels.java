package dev.agentforge.core.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * One place for how AgentForge talks to OpenAI. Every agent returns typed Java records, so every
 * model is built with strict JSON-schema output: OpenAI must answer with JSON that matches the record.
 * (The apps' default model is configured the same way in application.yml for the Spring starter.)
 */
public final class OpenAiModels {

    public static ChatModel structuredOutput(String apiKey, String modelName, double temperature) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .responseFormat("json_schema")
                .strictJsonSchema(true)
                .build();
    }

    private OpenAiModels() {
    }
}
