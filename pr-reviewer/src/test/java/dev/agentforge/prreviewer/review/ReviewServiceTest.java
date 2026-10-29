package dev.agentforge.prreviewer.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import dev.agentforge.prreviewer.review.ReviewService.Candidate;
import dev.agentforge.prreviewer.review.ReviewService.Judgement;

class ReviewServiceTest {

    private static final String[] CODE = {
            "int total = 0;",
            "for (User u : users) {",
            "    total += u.getAge();",
            "}",
            "return total / users.size();"
    };

    @Test
    void numbersEveryLineStartingAtOne() {
        assertThat(ReviewService.numberLines("int a = 1;\nint b = 2;"))
                .isEqualTo("1: int a = 1;\n2: int b = 2;\n");
    }

    @Test
    void handlesWindowsLineEndingsAndBlankLines() {
        assertThat(ReviewService.numberLines("a\r\n\r\nb"))
                .isEqualTo("1: a\n2: \n3: b\n");
    }

    @Test
    void keepsLineWhenQuotedCodeIsOnIt() {
        ReviewFinding f = finding(5, "return total / users.size();", Category.BUG, Severity.HIGH);
        assertThat(ReviewService.fixLine(f, CODE).line()).isEqualTo(5);
    }

    @Test
    void movesLineToWhereTheQuotedCodeActuallyIs() {
        ReviewFinding f = finding(4, "return total / users.size();", Category.BUG, Severity.HIGH);
        assertThat(ReviewService.fixLine(f, CODE).line()).isEqualTo(5);
    }

    @Test
    void ignoresNumberPrefixTheModelCopiedIntoTheQuote() {
        ReviewFinding f = finding(1, "3: total += u.getAge();", Category.BUG, Severity.LOW);
        assertThat(ReviewService.fixLine(f, CODE).line()).isEqualTo(3);
    }

    @Test
    void leavesLineAloneWhenQuotedCodeIsNowhereNear() {
        ReviewFinding f = finding(2, "something the model made up", Category.BUG, Severity.LOW);
        assertThat(ReviewService.fixLine(f, CODE).line()).isEqualTo(2);
    }

    @Test
    void dedupeKeepsMostSevereFindingPerLineAndCategory() {
        List<ReviewFinding> result = ReviewService.dedupe(List.of(
                finding(5, "x", Category.BUG, Severity.MEDIUM),
                finding(5, "x", Category.BUG, Severity.HIGH),
                finding(5, "x", Category.PERFORMANCE, Severity.LOW)), Function.identity());

        assertThat(result).extracting(ReviewFinding::category, ReviewFinding::severity)
                .containsExactly(tuple(Category.BUG, Severity.HIGH), tuple(Category.PERFORMANCE, Severity.LOW));
    }

    @Test
    void dedupeKeepsDifferentProblemsOnTheSameLine() {
        // Real case from the eval: both are BUGs on line 37, and both are real
        List<ReviewFinding> result = ReviewService.dedupe(List.of(
                titled(37, Category.BUG, Severity.HIGH, "Possible division by zero"),
                titled(37, Category.BUG, Severity.MEDIUM, "Integer division truncates the average")), Function.identity());

        assertThat(result).extracting(ReviewFinding::title)
                .containsExactly("Possible division by zero", "Integer division truncates the average");
    }

    @Test
    void dedupeMergesTheSameProblemWordedDifferentlyKeepingTheMostSevere() {
        List<ReviewFinding> result = ReviewService.dedupe(List.of(
                titled(12, Category.SECURITY, Severity.HIGH, "SQL injection risk"),
                titled(12, Category.SECURITY, Severity.CRITICAL, "SQL Injection vulnerability")), Function.identity());

        assertThat(result).extracting(ReviewFinding::severity).containsExactly(Severity.CRITICAL);
    }

    @Test
    void titleSimilarityNeedsHalfTheMeaningfulWords() {
        assertThat(ReviewService.similarTitles("Hard-coded secret", "Hard-coded database password")).isFalse();
        assertThat(ReviewService.similarTitles("Resource leak: ResultSet not closed", "ResultSet not closed")).isTrue();
        assertThat(ReviewService.similarTitles("N+1 query problem", "N+1 query in loop")).isTrue();
    }

    @Test
    void judgeVerdictsDropKeepAndChangeSeverity() {
        List<Candidate> candidates = List.of(
                new Candidate(1, "security", finding(5, "return total / users.size();", Category.BUG, Severity.LOW)),
                new Candidate(2, "performance", finding(4, "}", Category.PERFORMANCE, Severity.MEDIUM)),
                new Candidate(3, "bugs", finding(3, "total += u.getAge();", Category.BUG, Severity.LOW)));
        JudgeResult result = new JudgeResult(List.of(
                new JudgeResult.Verdict(1, true, Severity.HIGH, "real"),
                new JudgeResult.Verdict(2, false, Severity.MEDIUM, "points at a brace")), List.of());

        Judgement judgement = ReviewService.applyVerdicts(candidates, result, CODE);

        assertThat(judgement.findings()).extracting(ReviewFinding::line, ReviewFinding::severity)
                .containsExactly(tuple(5, Severity.HIGH), tuple(3, Severity.LOW)); // no verdict for 3 -> kept as is
        assertThat(judgement.dropped()).extracting(ReviewReport.DroppedFinding::foundBy, ReviewReport.DroppedFinding::reason)
                .containsExactly(tuple("performance", "points at a brace"));
        assertThat(judgement.added()).isZero();
    }

    @Test
    void judgeMissedFindingsAreAddedWithLineFix() {
        List<Candidate> candidates = List.of(
                new Candidate(1, "bugs", finding(5, "return total / users.size();", Category.BUG, Severity.HIGH)));
        ReviewFinding missed = new ReviewFinding(2, "total += u.getAge();", Severity.LOW, Category.MAINTAINABILITY,
                "t", "e", "s");
        JudgeResult result = new JudgeResult(List.of(new JudgeResult.Verdict(1, true, Severity.HIGH, "real")), List.of(missed));

        Judgement judgement = ReviewService.applyVerdicts(candidates, result, CODE);

        assertThat(judgement.findings()).extracting(ReviewFinding::line).containsExactly(5, 3);
        assertThat(judgement.added()).isEqualTo(1);
    }

    private static ReviewFinding titled(int line, Category category, Severity severity, String title) {
        return new ReviewFinding(line, "code", severity, category, title, "explanation", "suggestion");
    }

    private static ReviewFinding finding(int line, String code, Category category, Severity severity) {
        return new ReviewFinding(line, code, severity, category, "title", "explanation", "suggestion");
    }
}
