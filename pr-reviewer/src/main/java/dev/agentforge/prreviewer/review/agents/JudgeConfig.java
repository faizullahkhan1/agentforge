package dev.agentforge.prreviewer.review.agents;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.agentforge.core.ai.OpenAiModels;
import dev.langchain4j.service.AiServices;

/**
 * The judge makes one call per review, so it can afford a stronger (pricier) model than the
 * specialists. @AiService would wire it to the app's single ChatModel, so we build it by hand:
 * this is exactly what @AiService does for the other agents behind the scenes.
 * The judge's model is deliberately NOT a Spring bean, so the other agents still see only one ChatModel.
 */
@Configuration
class JudgeConfig {

    @Bean
    JudgeAgent judgeAgent(@Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
                          @Value("${agentforge.judge.model-name}") String modelName) {
        return AiServices.create(JudgeAgent.class, OpenAiModels.structuredOutput(apiKey, modelName, 0.0));
    }
}
