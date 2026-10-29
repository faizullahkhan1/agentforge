package dev.agentforge.prreviewer.review;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.agentforge.prreviewer.runs.ReviewRunRepository;
import dev.agentforge.prreviewer.runs.ReviewRunRepository.Kind;
import dev.agentforge.prreviewer.runs.ReviewRunRepository.Trigger;

@RestController
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewRunRepository runs;

    public ReviewController(ReviewService reviewService, ReviewRunRepository runs) {
        this.reviewService = reviewService;
        this.runs = runs;
    }

    public record ReviewRequest(String language, String code) {
    }

    /**
     * mode=team (default): 3 specialist agents in parallel, then the judge (turn off with judge=false).
     * mode=single: the one generalist agent, no judge.
     */
    @PostMapping("/api/review")
    public ReviewReport review(@RequestBody ReviewRequest request,
                               @RequestParam(defaultValue = "team") String mode,
                               @RequestParam(defaultValue = "true") boolean judge) {
        if (request.code() == null || request.code().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code must not be empty");
        }
        if (!mode.equals("team") && !mode.equals("single")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be 'team' or 'single'");
        }
        String language = request.language() == null || request.language().isBlank() ? "Java" : request.language();

        long start = System.nanoTime();
        long runId = runs.start(Kind.CODE, Trigger.API, null, null, null, false);
        try {
            ReviewReport report = mode.equals("team")
                    ? reviewService.reviewWithTeam(language, request.code(), judge)
                    : reviewService.reviewWithGeneralist(language, request.code());
            runs.saveFile(runId, null, report, null);
            runs.complete(runId, null, report.findings().size(), null, report.totalMillis());
            return report;
        } catch (RuntimeException e) {
            runs.fail(runId, e.getMessage(), (System.nanoTime() - start) / 1_000_000);
            throw e;
        }
    }
}
