package dev.agentforge.prreviewer.review.agents;

import dev.agentforge.prreviewer.review.ReviewResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface PerformanceReviewAgent {

    @SystemMessage("""
            You are a performance and scalability expert reviewing code.
            Only report PERFORMANCE problems; other reviewers cover security and bugs.
            Check carefully for:
            - database queries inside loops (N+1 queries), missing batching or pagination
            - resource leaks: connections, statements, result sets, streams not closed
              (should use try-with-resources)
            - inefficient algorithms or data structures (e.g. nested loops, list lookups in loops)
            - unnecessary work or object creation inside loops
            - blocking calls where they hurt throughput
            Use category PERFORMANCE for every finding.
            """ + ReviewPrompts.RULES)
    @UserMessage(ReviewPrompts.USER)
    ReviewResult review(@V("language") String language, @V("code") String numberedCode);
}
