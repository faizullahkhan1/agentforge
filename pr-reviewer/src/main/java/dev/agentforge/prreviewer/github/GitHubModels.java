package dev.agentforge.prreviewer.github;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The small slice of GitHub's REST API JSON we actually use. GitHub uses snake_case,
 * so @JsonProperty maps those names onto Java's camelCase. Unknown fields are ignored.
 */
public final class GitHubModels {

    public record PullRequest(String title, @JsonProperty("html_url") String htmlUrl, Head head) {
    }

    public record Head(String sha) {
    }

    /** One changed file in a PR. patch is the unified diff, null for binary or huge files. */
    public record PullRequestFile(String filename, String status, String patch) {
    }

    /** GET /repos/{owner}/{repo}/compare/{base}...{head}: the files changed between two commits. */
    public record Comparison(List<PullRequestFile> files) {
    }

    /** Body of POST /repos/{owner}/{repo}/pulls/{number}/reviews */
    public record CreateReview(@JsonProperty("commit_id") String commitId,
                               String body,
                               String event,
                               List<ReviewComment> comments) {
    }

    /** An inline comment. side=RIGHT means line is a line number in the new version of the file. */
    public record ReviewComment(String path, int line, String side, String body) {
    }

    public record Review(long id, @JsonProperty("html_url") String htmlUrl) {
    }

    private GitHubModels() {
    }
}
