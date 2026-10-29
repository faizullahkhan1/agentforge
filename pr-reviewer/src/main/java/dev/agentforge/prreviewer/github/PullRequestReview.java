package dev.agentforge.prreviewer.github;

import java.util.List;

import dev.agentforge.prreviewer.review.ReviewFinding;

/** What /api/github/... returns: what was reviewed, what was found, and whether it was posted. */
public record PullRequestReview(
        long runId,
        String pullRequest,
        String headSha,
        String reviewedSince,
        List<FileReview> files,
        List<String> skipped,
        int commentCount,
        boolean posted,
        String reviewUrl,
        long totalMillis
) {

    /**
     * findings: on lines this PR changed, these become comments.
     * outsideDiff: problems in untouched code of the same file; not posted, since the PR didn't cause them.
     */
    public record FileReview(String path, List<Integer> changedLines, List<ReviewFinding> findings,
                             List<ReviewFinding> outsideDiff, String reviewSummary) {
    }
}
