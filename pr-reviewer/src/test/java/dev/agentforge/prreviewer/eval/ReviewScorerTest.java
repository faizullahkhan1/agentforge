package dev.agentforge.prreviewer.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.agentforge.prreviewer.eval.ReviewScorer.Expected;
import dev.agentforge.prreviewer.eval.ReviewScorer.RunScore;
import dev.agentforge.prreviewer.review.Category;
import dev.agentforge.prreviewer.review.ReviewFinding;
import dev.agentforge.prreviewer.review.Severity;

/** Normal unit test (runs in every mvn test): checks the grading logic, not the AI. */
class ReviewScorerTest {

    private static final List<Expected> KEY = List.of(
            new Expected("sql-injection", List.of(11, 12), List.of("injection")),
            new Expected("division-by-zero", List.of(37, 37), List.of("zero", "empty")),
            new Expected("integer-division", List.of(37, 37), List.of("integer division", "decimal")));

    @Test
    void matchesOnLineRangeAndKeywordIgnoringCase() {
        RunScore score = ReviewScorer.score(List.of(
                finding(12, "SQL Injection vulnerability", "user input concatenated")), KEY);

        assertThat(score.found()).containsExactly("sql-injection");
        assertThat(score.unexpected()).isEmpty();
    }

    @Test
    void rightWordsOnTheWrongLineDoNotCount() {
        RunScore score = ReviewScorer.score(List.of(finding(30, "SQL injection", "")), KEY);

        assertThat(score.found()).isEmpty();
        assertThat(score.unexpected()).hasSize(1);
    }

    @Test
    void oneFindingCanCoverTwoProblemsOnTheSameLine() {
        RunScore score = ReviewScorer.score(List.of(
                finding(37, "Division by zero", "and integer division drops the decimal part")), KEY);

        assertThat(score.found()).containsExactlyInAnyOrder("division-by-zero", "integer-division");
    }

    @Test
    void findingsThatMatchNothingAreUnexpected() {
        RunScore score = ReviewScorer.score(List.of(
                finding(11, "Database query inside loop", "not in a loop at all")), KEY);

        assertThat(score.found()).isEmpty();
        assertThat(score.unexpected()).extracting(ReviewFinding::title).containsExactly("Database query inside loop");
    }

    private static ReviewFinding finding(int line, String title, String explanation) {
        return new ReviewFinding(line, "code", Severity.HIGH, Category.BUG, title, explanation, "fix");
    }
}
