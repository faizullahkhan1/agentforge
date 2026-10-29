package dev.agentforge.prreviewer.review;

import java.util.List;

/** What the API returns: the final findings, what the judge threw out, and a trace of every agent. */
public record ReviewReport(
        String summary,
        List<ReviewFinding> findings,
        List<DroppedFinding> dropped,
        List<AgentRun> agents,
        long totalMillis
) {

    /** One agent's part of the review. error is null when the agent succeeded. */
    public record AgentRun(String agent, int findings, long millis, String error) {
    }

    /** A candidate the judge rejected, and why. */
    public record DroppedFinding(String foundBy, int line, String title, String reason) {
    }
}
