package dev.agentforge.prreviewer.github;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;

import dev.agentforge.prreviewer.runs.ReviewRunRepository.Trigger;

/** Manual trigger for now. In the next step a GitHub webhook will call the same service automatically. */
@RestController
public class GitHubReviewController {

    private final PullRequestReviewService service;

    public GitHubReviewController(PullRequestReviewService service) {
        this.service = service;
    }

    /**
     * dryRun=true (default): review and return the result, but don't post anything to GitHub.
     * since=&lt;sha&gt; (optional): only look at lines changed after that commit, like the webhook does on a push.
     */
    @PostMapping("/api/github/{owner}/{repo}/pulls/{number}/review")
    public PullRequestReview review(@PathVariable String owner,
                                    @PathVariable String repo,
                                    @PathVariable int number,
                                    @RequestParam(defaultValue = "true") boolean dryRun,
                                    @RequestParam(required = false) String since) {
        return service.review(owner, repo, number, dryRun, since, Trigger.API);
    }

    /** Pass GitHub's error (404 not found, 401 bad token, 403 rate limit...) through instead of a generic 500. */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, Object>> gitHubError(RestClientResponseException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", "GitHub API returned " + e.getStatusCode().value(),
                        "details", e.getResponseBodyAsString()));
    }
}
