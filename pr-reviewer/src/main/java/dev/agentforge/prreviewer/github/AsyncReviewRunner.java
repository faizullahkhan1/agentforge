package dev.agentforge.prreviewer.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import dev.agentforge.prreviewer.runs.ReviewRunRepository.Trigger;

/**
 * GitHub gives up on a webhook after 10 seconds, but a review takes 10-30.
 * So the webhook answers "202 Accepted" right away and the review runs here, in the background.
 * @Async only works when called from ANOTHER bean (Spring wraps this class in a proxy),
 * which is why this is its own class instead of a method on the controller.
 */
@Component
public class AsyncReviewRunner {

    private static final Logger log = LoggerFactory.getLogger(AsyncReviewRunner.class);

    private final PullRequestReviewService reviewService;

    public AsyncReviewRunner(PullRequestReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Async
    public void review(String owner, String repo, int number, String sinceSha, String deliveryId) {
        String pr = owner + "/" + repo + "#" + number;
        log.info("Webhook {}: starting review of {}{}", deliveryId, pr, sinceSha == null ? "" : " (changes since " + sinceSha + ")");
        try {
            PullRequestReview result = reviewService.review(owner, repo, number, false, sinceSha, Trigger.WEBHOOK);
            log.info("Webhook {}: run {} posted {} comment(s) on {} in {} ms: {}",
                    deliveryId, result.runId(), result.commentCount(), pr, result.totalMillis(), result.reviewUrl());
        } catch (RuntimeException e) {
            // Nobody is waiting for a response anymore, so the log is the only place this can go
            log.error("Webhook {}: review of {} failed", deliveryId, pr, e);
        }
    }
}
