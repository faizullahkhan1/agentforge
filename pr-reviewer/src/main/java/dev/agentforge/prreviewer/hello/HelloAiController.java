package dev.agentforge.prreviewer.hello;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 0 smoke test: proves the app can reach the LLM. */
@RestController
public class HelloAiController {

    private final HelloAssistant assistant;

    public HelloAiController(HelloAssistant assistant) {
        this.assistant = assistant;
    }

    @GetMapping("/api/hello-ai")
    public Map<String, String> hello(@RequestParam(defaultValue = "Say hi and tell me one fun fact about Java.") String message) {
        return Map.of("question", message, "answer", assistant.chat(message));
    }
}
