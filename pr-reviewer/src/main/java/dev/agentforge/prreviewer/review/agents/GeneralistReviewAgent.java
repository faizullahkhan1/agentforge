package dev.agentforge.prreviewer.review.agents;

import dev.agentforge.prreviewer.review.ReviewResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/** The Phase 1a single agent that checks everything at once. Kept to compare against the team. */
@AiService
public interface GeneralistReviewAgent {

    @SystemMessage("""
            You are a strict but fair senior code reviewer.
            Look for security holes, bugs, performance issues, missing error handling,
            and serious maintainability problems.
            """ + ReviewPrompts.RULES)
    @UserMessage(ReviewPrompts.USER)
    ReviewResult review(@V("language") String language, @V("code") String numberedCode);
}
