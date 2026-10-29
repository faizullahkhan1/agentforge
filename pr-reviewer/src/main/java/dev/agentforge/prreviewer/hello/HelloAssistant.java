package dev.agentforge.prreviewer.hello;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * A LangChain4j "AI Service": you declare an interface, LangChain4j generates the
 * implementation that turns each method call into an LLM request.
 * The ChatModel it uses (gpt-4o-mini) is configured in application.yml.
 */
@AiService
public interface HelloAssistant {

    @SystemMessage("You are a friendly senior Java developer. Answer in at most three sentences, using a Java analogy when it helps.")
    String chat(String message);
}
