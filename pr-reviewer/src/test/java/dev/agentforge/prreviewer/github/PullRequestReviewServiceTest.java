package dev.agentforge.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.agentforge.prreviewer.review.Category;
import dev.agentforge.prreviewer.review.ReviewFinding;
import dev.agentforge.prreviewer.review.Severity;

class PullRequestReviewServiceTest {

    @Test
    void detectsLanguageFromExtension() {
        assertThat(PullRequestReviewService.languageOf("src/main/java/App.java")).contains("Java");
        assertThat(PullRequestReviewService.languageOf("web/App.TSX")).contains("TypeScript");
        assertThat(PullRequestReviewService.languageOf("README.md")).isEmpty();
        assertThat(PullRequestReviewService.languageOf("Dockerfile")).isEmpty();
    }

    @Test
    void commentBodyShowsSeverityCategoryAndSuggestion() {
        ReviewFinding f = new ReviewFinding(12, "x", Severity.CRITICAL, Category.SECURITY,
                "SQL injection", "User input is concatenated.", "Use a PreparedStatement.");

        assertThat(PullRequestReviewService.commentBody(f))
                .startsWith("🔴 **CRITICAL · SECURITY** · SQL injection")
                .contains("User input is concatenated.")
                .contains("**Suggestion:** Use a PreparedStatement.");
    }
}
