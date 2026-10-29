package dev.agentforge.prreviewer.github;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** GitHub calls this whenever something happens in the repo (we subscribe to pull request events). */
@RestController
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    /** opened = new PR, synchronize = new commits pushed, reopened / ready_for_review = back in play */
    private static final Set<String> REVIEW_ACTIONS = Set.of("opened", "synchronize", "reopened", "ready_for_review");

    private final AsyncReviewRunner runner;
    private final ObjectMapper objectMapper;
    private final String secret;

    public GitHubWebhookController(AsyncReviewRunner runner,
                                   ObjectMapper objectMapper,
                                   @Value("${agentforge.github.webhook-secret:}") String secret) {
        this.runner = runner;
        this.objectMapper = objectMapper;
        this.secret = secret;
    }

    /**
     * The body is taken as raw bytes on purpose: the signature is computed over the exact bytes
     * GitHub sent, and parsing to JSON first could change whitespace and break the check.
     */
    @PostMapping("/api/github/webhook")
    public ResponseEntity<Map<String, String>> receive(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestHeader(value = "X-GitHub-Delivery", defaultValue = "?") String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] body) throws IOException {

        if (!WebhookSignature.isValid(secret, body, signature)) {
            log.warn("Webhook {} rejected: bad or missing signature", deliveryId);
            return ResponseEntity.status(401).body(Map.of("status", "invalid signature"));
        }

        JsonNode payload = objectMapper.readTree(body);
        if ("ping".equals(event)) {
            // GitHub sends this once when you create the webhook
            log.info("Webhook {}: ping received, hook is working", deliveryId);
            return ResponseEntity.ok(Map.of("status", "pong"));
        }
        if (!"pull_request".equals(event)) {
            return ResponseEntity.ok(Map.of("status", "ignored event " + event));
        }

        String action = payload.path("action").asText();
        JsonNode pr = payload.path("pull_request");
        if (!REVIEW_ACTIONS.contains(action)) {
            return ResponseEntity.ok(Map.of("status", "ignored action " + action));
        }
        if (pr.path("draft").asBoolean()) {
            return ResponseEntity.ok(Map.of("status", "ignored draft PR"));
        }

        String owner = payload.path("repository").path("owner").path("login").asText();
        String repo = payload.path("repository").path("name").asText();
        int number = pr.path("number").asInt();
        // On a push to the PR, "before" is the previous head: only review what this push changed
        String sinceSha = "synchronize".equals(action) ? payload.path("before").asText(null) : null;
        runner.review(owner, repo, number, sinceSha, deliveryId);
        return ResponseEntity.accepted().body(Map.of("status", "review started for " + owner + "/" + repo + "#" + number));
    }
}
