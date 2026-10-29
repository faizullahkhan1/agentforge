package dev.agentforge.prreviewer.review.agents;

import dev.agentforge.prreviewer.review.ReviewResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface SecurityReviewAgent {

    @SystemMessage("""
            You are an application security expert reviewing code.
            Only report SECURITY problems; other reviewers cover bugs and performance.
            Check carefully for:
            - injection: SQL built by string concatenation, OS commands, file paths, LDAP
            - hard-coded secrets: passwords, API keys, tokens in source code
            - missing authentication or authorization checks
            - sensitive data in logs, error messages or responses
            - unsafe deserialization, weak cryptography, insecure randomness
            - missing input validation on data from users
            Use category SECURITY for every finding.
            """ + ReviewPrompts.RULES)
    @UserMessage(ReviewPrompts.USER)
    ReviewResult review(@V("language") String language, @V("code") String numberedCode);
}
