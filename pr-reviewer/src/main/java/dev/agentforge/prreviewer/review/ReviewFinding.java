package dev.agentforge.prreviewer.review;

import dev.langchain4j.model.output.structured.Description;

/**
 * One problem the AI found. LangChain4j turns this record into a JSON schema that is sent
 * to OpenAI, and parses the JSON answer back into this record. The @Description texts
 * end up in that schema, so they are instructions for the model, not just docs for us.
 */
public record ReviewFinding(
        @Description("line number where the problem is, taken from the number prefix of that line")
        int line,
        @Description("the code on that line copied exactly, without the number prefix")
        String code,
        Severity severity,
        Category category,
        @Description("short title of the problem, max 10 words")
        String title,
        @Description("why this is a problem and what could go wrong")
        String explanation,
        @Description("concrete fix, can include a short code snippet")
        String suggestion
) {

    public ReviewFinding withLine(int newLine) {
        return new ReviewFinding(newLine, code, severity, category, title, explanation, suggestion);
    }

    public ReviewFinding withSeverity(Severity newSeverity) {
        return new ReviewFinding(line, code, newSeverity, category, title, explanation, suggestion);
    }
}
