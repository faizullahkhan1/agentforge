package dev.agentforge.prreviewer.review.agents;

import dev.agentforge.prreviewer.review.JudgeResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Quality control: checks the specialists' findings against the code before anyone sees them.
 * No @AiService here: JudgeConfig builds it with its own, stronger model.
 */
public interface JudgeAgent {

    @SystemMessage("""
            You are the lead reviewer. Specialist reviewers (security, performance, bugs) looked at
            the code and produced candidate findings. Your job is quality control.
            For every candidate:
            - keep=true only if the problem really exists on that line and the title and
              explanation describe it correctly
            - keep=false for false positives, vague findings, findings pointing at the wrong code,
              and duplicates of another candidate (keep the better one of the two)
            - set the correct severity using the guide below
            Then look at the code once more and put into missed only clear, real problems that no
            candidate covers. Do not re-add candidates you dropped.
            """ + ReviewPrompts.SEVERITY_GUIDE + ReviewPrompts.LINE_RULES)
    @UserMessage("""
            {{language}} code:

            {{code}}

            Candidate findings (JSON):
            {{candidates}}
            """)
    JudgeResult judge(@V("language") String language,
                      @V("code") String numberedCode,
                      @V("candidates") String candidatesJson);
}
