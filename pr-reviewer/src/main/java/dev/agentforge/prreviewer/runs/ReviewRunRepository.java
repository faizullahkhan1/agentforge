package dev.agentforge.prreviewer.runs;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.agentforge.prreviewer.review.ReviewFinding;
import dev.agentforge.prreviewer.review.ReviewReport;

/**
 * Plain SQL with Spring's JdbcClient: what you write here is exactly what runs, and exactly
 * what you'll see in pgAdmin. Query results map onto records by column name
 * (snake_case columns -> camelCase record components).
 */
@Repository
public class ReviewRunRepository {

    public enum Kind { CODE, PULL_REQUEST }

    public enum Trigger { API, WEBHOOK }

    public record RunSummary(long id, String kind, String triggeredBy, String status, String repository,
                             Integer prNumber, Integer commentCount, int findings, String error,
                             OffsetDateTime startedAt, Long totalMillis) {
    }

    public record AgentCall(String filePath, String agent, int findings, long millis, String error) {
    }

    public record StoredFinding(String filePath, String status, int lineNumber, String severity, String category,
                                String title, String code, String explanation, String suggestion,
                                String foundBy, String judgeReason) {
    }

    public record RunDetail(RunSummary run, String headSha, String reviewedSince, boolean dryRun, String reviewUrl,
                            List<AgentCall> agentCalls, List<StoredFinding> findings) {
    }

    private record RunExtra(String headSha, String reviewedSince, boolean dryRun, String reviewUrl) {
    }

    private static final String SUMMARY_SELECT = """
            SELECT r.id, r.kind, r.triggered_by, r.status, r.repository, r.pr_number, r.comment_count,
                   (SELECT count(*) FROM finding f WHERE f.run_id = r.id AND f.status = 'KEPT') AS findings,
                   r.error, r.started_at, r.total_millis
            FROM review_run r
            """;

    private final JdbcClient jdbc;

    public ReviewRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserted before the review starts, so a review that crashes still leaves a FAILED row behind. */
    public long start(Kind kind, Trigger trigger, String repository, Integer prNumber, String reviewedSince, boolean dryRun) {
        return jdbc.sql("""
                        INSERT INTO review_run (kind, triggered_by, status, repository, pr_number, reviewed_since, dry_run)
                        VALUES (?, ?, 'RUNNING', ?, ?, ?, ?)
                        RETURNING id
                        """)
                .params(kind.name(), trigger.name(), repository, prNumber, reviewedSince, dryRun)
                .query(Long.class).single();
    }

    /**
     * Stores one file's review: the agent trace, the findings, and what the judge dropped.
     * changedLines null = no diff (pasted code), so every finding counts as KEPT.
     */
    @Transactional
    public void saveFile(long runId, String filePath, ReviewReport report, Set<Integer> changedLines) {
        for (ReviewReport.AgentRun call : report.agents()) {
            jdbc.sql("INSERT INTO agent_call (run_id, file_path, agent, findings, millis, error) VALUES (?, ?, ?, ?, ?, ?)")
                    .params(runId, filePath, call.agent(), call.findings(), call.millis(), call.error())
                    .update();
        }
        for (ReviewFinding f : report.findings()) {
            String status = changedLines == null || changedLines.contains(f.line()) ? "KEPT" : "OUTSIDE_DIFF";
            insertFinding(runId, filePath, status, f.line(), f.severity().name(), f.category().name(), f.title(),
                    f.code(), f.explanation(), f.suggestion(), null, null);
        }
        for (ReviewReport.DroppedFinding d : report.dropped()) {
            insertFinding(runId, filePath, "DROPPED", d.line(), null, null, d.title(),
                    null, null, null, d.foundBy(), d.reason());
        }
    }

    public void complete(long runId, String headSha, int commentCount, String reviewUrl, long totalMillis) {
        jdbc.sql("""
                        UPDATE review_run
                        SET status = 'COMPLETED', head_sha = ?, comment_count = ?, review_url = ?,
                            total_millis = ?, finished_at = now()
                        WHERE id = ?
                        """)
                .params(headSha, commentCount, reviewUrl, totalMillis, runId)
                .update();
    }

    public void fail(long runId, String error, long totalMillis) {
        jdbc.sql("UPDATE review_run SET status = 'FAILED', error = ?, total_millis = ?, finished_at = now() WHERE id = ?")
                .params(error, totalMillis, runId)
                .update();
    }

    public List<RunSummary> latest(int limit) {
        return jdbc.sql(SUMMARY_SELECT + " ORDER BY r.started_at DESC LIMIT ?")
                .param(limit)
                .query(RunSummary.class).list();
    }

    public Optional<RunDetail> find(long id) {
        Optional<RunSummary> run = jdbc.sql(SUMMARY_SELECT + " WHERE r.id = ?").param(id).query(RunSummary.class).optional();
        return run.map(summary -> {
            RunExtra extra = jdbc.sql("SELECT head_sha, reviewed_since, dry_run, review_url FROM review_run WHERE id = ?")
                    .param(id).query(RunExtra.class).single();
            List<AgentCall> calls = jdbc.sql("""
                            SELECT file_path, agent, findings, millis, error
                            FROM agent_call WHERE run_id = ? ORDER BY id
                            """)
                    .param(id).query(AgentCall.class).list();
            List<StoredFinding> findings = jdbc.sql("""
                            SELECT file_path, status, line_number, severity, category, title, code,
                                   explanation, suggestion, found_by, judge_reason
                            FROM finding WHERE run_id = ? ORDER BY status, file_path, line_number
                            """)
                    .param(id).query(StoredFinding.class).list();
            return new RunDetail(summary, extra.headSha(), extra.reviewedSince(), extra.dryRun(), extra.reviewUrl(),
                    calls, findings);
        });
    }

    private void insertFinding(long runId, String filePath, String status, int line, String severity, String category,
                               String title, String code, String explanation, String suggestion,
                               String foundBy, String judgeReason) {
        jdbc.sql("""
                        INSERT INTO finding (run_id, file_path, status, line_number, severity, category, title, code,
                                             explanation, suggestion, found_by, judge_reason)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(runId, filePath, status, line, severity, category, title, code, explanation, suggestion,
                        foundBy, judgeReason)
                .update();
    }
}
