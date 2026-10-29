package dev.agentforge.prreviewer.review;

import java.util.List;

import dev.langchain4j.model.output.structured.Description;

public record ReviewResult(
        @Description("one or two sentence overall verdict on the code")
        String summary,
        @Description("problems found; empty list if the code has no real issues")
        List<ReviewFinding> findings
) {
}
