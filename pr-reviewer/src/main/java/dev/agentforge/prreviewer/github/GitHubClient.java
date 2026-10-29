package dev.agentforge.prreviewer.github;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.agentforge.prreviewer.github.GitHubModels.Comparison;
import dev.agentforge.prreviewer.github.GitHubModels.CreateReview;
import dev.agentforge.prreviewer.github.GitHubModels.PullRequest;
import dev.agentforge.prreviewer.github.GitHubModels.PullRequestFile;
import dev.agentforge.prreviewer.github.GitHubModels.Review;

/**
 * Thin wrapper over the GitHub REST API using Spring's RestClient.
 * Without a token it can still read public repos (60 requests/hour), but not post reviews.
 */
@Component
public class GitHubClient {

    private final RestClient rest;

    public GitHubClient(RestClient.Builder builder,
                        @Value("${agentforge.github.api-url}") String apiUrl,
                        @Value("${agentforge.github.token:}") String token) {
        RestClient.Builder b = builder.baseUrl(apiUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (!token.isBlank()) {
            b.defaultHeader("Authorization", "Bearer " + token);
        }
        this.rest = b.build();
    }

    public PullRequest getPullRequest(String owner, String repo, int number) {
        return rest.get().uri("/repos/{owner}/{repo}/pulls/{number}", owner, repo, number)
                .retrieve().body(PullRequest.class);
    }

    /** First 100 changed files; enough for any PR a human would want reviewed in one go. */
    public List<PullRequestFile> listFiles(String owner, String repo, int number) {
        return rest.get().uri("/repos/{owner}/{repo}/pulls/{number}/files?per_page=100", owner, repo, number)
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    /** Files changed between two commits, e.g. the commit before a push and the one after it. */
    public List<PullRequestFile> compareFiles(String owner, String repo, String base, String head) {
        Comparison comparison = rest.get().uri("/repos/{owner}/{repo}/compare/{base}...{head}", owner, repo, base, head)
                .retrieve().body(Comparison.class);
        return comparison == null || comparison.files() == null ? List.of() : comparison.files();
    }

    /** The full file at a given commit. The "raw" media type returns plain text instead of base64 JSON. */
    public String getFileContent(String owner, String repo, String path, String ref) {
        return rest.get().uri("/repos/{owner}/{repo}/contents/{path}?ref={ref}", owner, repo, path, ref)
                .header("Accept", "application/vnd.github.raw+json")
                .retrieve().body(String.class);
    }

    public Review createReview(String owner, String repo, int number, CreateReview review) {
        return rest.post().uri("/repos/{owner}/{repo}/pulls/{number}/reviews", owner, repo, number)
                .body(review)
                .retrieve().body(Review.class);
    }
}
