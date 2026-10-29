package dev.agentforge.prreviewer.review.agents;

/**
 * Prompt pieces shared by all reviewer agents. Annotation values must be compile-time
 * constants, so these are static final Strings that the agents concatenate.
 */
final class ReviewPrompts {

    static final String LINE_RULES = """
            Every line of the code starts with "<number>: ". Use that number for the line field
            and copy the code of that line (without the number prefix) into the code field.
            Point at the line that actually shows the problem, never at a lone brace.
            """;

    static final String SEVERITY_GUIDE = """
            Severity guide:
            - CRITICAL: exploitable security hole (e.g. injection, hard-coded credentials) or data loss
            - HIGH: bug or issue that will break or hurt in production
            - MEDIUM: likely problem under some conditions
            - LOW / INFO: worth improving, not urgent
            """;

    static final String RULES = """
            Report only real problems, never formatting or personal style preferences.
            """ + LINE_RULES + SEVERITY_GUIDE + """
            If you find nothing in your area, return an empty findings list. Never invent problems.
            """;

    static final String USER = """
            Review this {{language}} code:

            {{code}}
            """;

    private ReviewPrompts() {
    }
}
