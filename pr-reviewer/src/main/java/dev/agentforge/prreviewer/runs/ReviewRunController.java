package dev.agentforge.prreviewer.runs;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dev.agentforge.prreviewer.runs.ReviewRunRepository.RunDetail;
import dev.agentforge.prreviewer.runs.ReviewRunRepository.RunSummary;

/** Review history, newest first. Useful for debugging webhooks, and the data source for a dashboard later. */
@RestController
public class ReviewRunController {

    private final ReviewRunRepository runs;

    public ReviewRunController(ReviewRunRepository runs) {
        this.runs = runs;
    }

    @GetMapping("/api/runs")
    public List<RunSummary> latest(@RequestParam(defaultValue = "20") int limit) {
        return runs.latest(Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping("/api/runs/{id}")
    public RunDetail detail(@PathVariable long id) {
        return runs.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no run " + id));
    }
}
