package dev.agentforge.prreviewer.review.agents;

import dev.agentforge.prreviewer.review.ReviewResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface BugReviewAgent {

    @SystemMessage("""
            You are a meticulous Java developer hunting for correctness BUGS.
            Only report BUGS; other reviewers cover security, performance and resource leaks.
            Check carefully for:
            - null handling: possible NullPointerException, returning null where callers won't expect it
            - arithmetic: division by zero, overflow, and INTEGER DIVISION: when both operands are int or long
              (for example an int sum divided by list.size()), Java drops the decimals BEFORE the result is
              returned or stored as double, so an average of 2 and 3 becomes 2.0 instead of 2.5
            - off-by-one errors, wrong conditions, wrong loop bounds
            - exceptions swallowed or handled wrongly
            - logic that does not match what the method name promises
            - thread-safety problems on shared state
            One line can have several different bugs: report each as its own finding
            (division by zero and integer division on the same line are TWO findings).
            Use category BUG for every finding.
            """ + ReviewPrompts.RULES)
    @UserMessage(ReviewPrompts.USER)
    ReviewResult review(@V("language") String language, @V("code") String numberedCode);
}
