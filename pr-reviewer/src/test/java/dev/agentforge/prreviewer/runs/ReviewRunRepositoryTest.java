package dev.agentforge.prreviewer.runs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.agentforge.prreviewer.review.Category;
import dev.agentforge.prreviewer.review.ReviewFinding;
import dev.agentforge.prreviewer.review.ReviewReport;
import dev.agentforge.prreviewer.review.Severity;
import dev.agentforge.prreviewer.runs.ReviewRunRepository.Kind;
import dev.agentforge.prreviewer.runs.ReviewRunRepository.RunDetail;
import dev.agentforge.prreviewer.runs.ReviewRunRepository.StoredFinding;
import dev.agentforge.prreviewer.runs.ReviewRunRepository.Trigger;

/**
 * Runs against a REAL Postgres (the same pgvector image as docker-compose), started by Testcontainers
 * just for this test and thrown away after. Flyway applies the migrations first, so this also proves
 * V1__review_runs.sql is valid SQL. Needs Docker running.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReviewRunRepository.class)
@Testcontainers
class ReviewRunRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    ReviewRunRepository runs;

    @Test
    void storesPullRequestRunWithTraceAndFindingStatuses() {
        long id = runs.start(Kind.PULL_REQUEST, Trigger.WEBHOOK, "me/repo", 7, "abc1234", false);
        ReviewReport report = new ReviewReport("summary",
                List.of(finding(12, Severity.CRITICAL), finding(40, Severity.LOW)),
                List.of(new ReviewReport.DroppedFinding("performance", 11, "Query in loop", "No loop on line 11")),
                List.of(new ReviewReport.AgentRun("security", 1, 2300, null),
                        new ReviewReport.AgentRun("judge (gpt-4.1)", 2, 3100, null)),
                5400);

        runs.saveFile(id, "src/UserService.java", report, Set.of(12, 13));
        runs.complete(id, "def5678", 1, "https://github.com/me/repo/pull/7#review", 5600);

        RunDetail detail = runs.find(id).orElseThrow();
        assertThat(detail.run().status()).isEqualTo("COMPLETED");
        assertThat(detail.run().findings()).isEqualTo(1); // only KEPT findings are counted
        assertThat(detail.headSha()).isEqualTo("def5678");
        assertThat(detail.agentCalls()).extracting(ReviewRunRepository.AgentCall::agent)
                .containsExactly("security", "judge (gpt-4.1)");
        assertThat(detail.findings()).extracting(StoredFinding::lineNumber, StoredFinding::status)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(12, "KEPT"),
                        org.assertj.core.groups.Tuple.tuple(40, "OUTSIDE_DIFF"),
                        org.assertj.core.groups.Tuple.tuple(11, "DROPPED"));
        assertThat(runs.latest(5)).extracting(ReviewRunRepository.RunSummary::id).contains(id);
    }

    @Test
    void failedRunKeepsTheError() {
        long id = runs.start(Kind.PULL_REQUEST, Trigger.WEBHOOK, "me/repo", 999, null, false);
        runs.fail(id, "404 Not Found", 300);

        RunDetail detail = runs.find(id).orElseThrow();
        assertThat(detail.run().status()).isEqualTo("FAILED");
        assertThat(detail.run().error()).isEqualTo("404 Not Found");
    }

    private static ReviewFinding finding(int line, Severity severity) {
        return new ReviewFinding(line, "code", severity, Category.SECURITY, "title", "explanation", "suggestion");
    }
}
