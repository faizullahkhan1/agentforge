package dev.agentforge.prreviewer.review;

import java.util.List;

import dev.langchain4j.model.output.structured.Description;

/**
 * The judge doesn't rewrite the findings itself. It returns a decision per candidate,
 * and Java applies those decisions, so every drop or change is visible and has a reason.
 */
public record JudgeResult(
        @Description("exactly one verdict for every candidate id")
        List<Verdict> verdicts,
        @Description("clear, real problems that no reviewer reported; empty list if none")
        List<ReviewFinding> missed
) {

    public record Verdict(
            @Description("id of the candidate finding this verdict is about")
            int id,
            @Description("true only if the problem really exists at that line and the title describes it correctly")
            boolean keep,
            @Description("the correct severity for this finding")
            Severity severity,
            @Description("one short sentence explaining the decision")
            String reason
    ) {
    }
}
