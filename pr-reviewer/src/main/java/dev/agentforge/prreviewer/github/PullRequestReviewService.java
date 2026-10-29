package dev.agentforge.prreviewer.github;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import dev.agentforge.core.run.Steps;
import dev.agentforge.prreviewer.github.GitHubModels.CreateReview;
import dev.agentforge.prreviewer.github.GitHubModels.PullRequest;
import dev.agentforge.prreviewer.github.GitHubModels.PullRequestFile;
import dev.agentforge.prreviewer.github.GitHubModels.Review;
import dev.agentforge.prreviewer.github.GitHubModels.ReviewComment;
import dev.agentforge.prreviewer.github.PullRequestReview.FileReview;
import dev.agentforge.prreviewer.review.ReviewFinding;
import dev.agentforge.prreviewer.review.ReviewReport;
import dev.agentforge.prreviewer.review.ReviewService;
import dev.agentforge.prreviewer.review.Severity;
import dev.agentforge.prreviewer.runs.ReviewRunRepository;
import dev.agentforge.prreviewer.runs.ReviewRunRepository.Kind;
import dev.agentforge.prreviewer.runs.ReviewRunRepository.Trigger;

/**
 * PR pipeline: fetch changed files -> review each FULL file (so agents see context)
 * -> keep only findings on lines the PR changed -> post them as one GitHub review.
 */
@Service
public class PullRequestReviewService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestReviewService.class);

    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"), Map.entry("py", "Python"),
            Map.entry("js", "JavaScript"), Map.entry("ts", "TypeScript"), Map.entry("tsx", "TypeScript"),
            Map.entry("go", "Go"), Map.entry("cs", "C#"), Map.entry("rb", "Ruby"), Map.entry("php", "PHP"),
            Map.entry("sql", "SQL"));

    private final GitHubClient github;
    private final ReviewService reviewService;
    private final ReviewRunRepository runs;
    private final int maxFiles;
    private final int maxLinesPerFile;

    public PullRequestReviewService(GitHubClient github,
                                    ReviewService reviewService,
                                    ReviewRunRepository runs,
                                    @Value("${agentforge.github.max-files}") int maxFiles,
                                    @Value("${agentforge.github.max-lines-per-file}") int maxLinesPerFile) {
        this.github = github;
        this.reviewService = reviewService;
        this.runs = runs;
        this.maxFiles = maxFiles;
        this.maxLinesPerFile = maxLinesPerFile;
    }

    /**
     * @param sinceSha null = review everything the PR changes. A commit SHA (the "before" of a push)
     *                 = only comment on lines changed since that commit, so a new push doesn't
     *                 repeat comments on code that was already reviewed.
     */
    public PullRequestReview review(String owner, String repo, int number, boolean dryRun, String sinceSha,
                                    Trigger trigger) {
        long start = System.nanoTime();
        long runId = runs.start(Kind.PULL_REQUEST, trigger, owner + "/" + repo, number, sinceSha, dryRun);
        try {
            PullRequestReview result = review(runId, start, owner, repo, number, dryRun, sinceSha);
            runs.complete(runId, result.headSha(), result.commentCount(), result.reviewUrl(), result.totalMillis());
            return result;
        } catch (RuntimeException e) {
            runs.fail(runId, e.getMessage(), Steps.millisSince(start));
            throw e;
        }
    }

    private PullRequestReview review(long runId, long start, String owner, String repo, int number,
                                     boolean dryRun, String sinceSha) {
        PullRequest pr = github.getPullRequest(owner, repo, number);
        String sha = pr.head().sha();
        Map<String, String> patchesSince = sinceSha == null ? null : patchesSince(owner, repo, sinceSha, sha);
        String reviewedSince = patchesSince == null ? null : sinceSha;

        List<FileReview> fileReviews = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<ReviewComment> comments = new ArrayList<>();

        for (PullRequestFile file : github.listFiles(owner, repo, number)) {
            Optional<String> language = languageOf(file.filename());
            Set<Integer> changedLines = DiffParser.addedLines(
                    patchesSince == null ? file.patch() : patchesSince.get(file.filename()));

            if ("removed".equals(file.status())) {
                skipped.add(file.filename() + " (deleted)");
            } else if (language.isEmpty()) {
                skipped.add(file.filename() + " (not a supported language)");
            } else if (changedLines.isEmpty()) {
                skipped.add(file.filename() + (reviewedSince == null ? " (no added lines)"
                        : " (not changed since " + shortSha(reviewedSince) + ")"));
            } else if (fileReviews.size() >= maxFiles) {
                skipped.add(file.filename() + " (over the " + maxFiles + "-file limit)");
            } else {
                String content = github.getFileContent(owner, repo, file.filename(), sha);
                long lineCount = content.lines().count();
                if (lineCount > maxLinesPerFile) {
                    skipped.add(file.filename() + " (" + lineCount + " lines, over the " + maxLinesPerFile + "-line limit)");
                    continue;
                }
                log.info("Reviewing {} ({} lines, {} changed)", file.filename(), lineCount, changedLines.size());
                ReviewReport report = reviewService.reviewWithTeam(language.get(), content, true);
                runs.saveFile(runId, file.filename(), report, changedLines);

                // Agents saw the whole file, but we only comment on what this PR changed
                Map<Boolean, List<ReviewFinding>> split = report.findings().stream()
                        .collect(Collectors.partitioningBy(f -> changedLines.contains(f.line())));
                List<ReviewFinding> inDiff = split.get(true);
                fileReviews.add(new FileReview(file.filename(), List.copyOf(changedLines), inDiff,
                        split.get(false), report.summary()));
                inDiff.forEach(f -> comments.add(new ReviewComment(file.filename(), f.line(), "RIGHT", commentBody(f))));
            }
        }

        String reviewUrl = null;
        if (!dryRun) {
            String body = reviewBody(fileReviews, skipped, comments.size(), reviewedSince);
            Review posted = github.createReview(owner, repo, number, new CreateReview(sha, body, "COMMENT", comments));
            reviewUrl = posted.htmlUrl();
        }
        return new PullRequestReview(runId, owner + "/" + repo + "#" + number, sha, reviewedSince, fileReviews, skipped,
                comments.size(), !dryRun, reviewUrl, Steps.millisSince(start));
    }

    /**
     * filename -> diff between two commits. Returns null if GitHub can't compare them
     * (e.g. after a force-push the old commit may be gone), and the caller reviews the whole PR instead.
     */
    private Map<String, String> patchesSince(String owner, String repo, String base, String head) {
        try {
            Map<String, String> patches = new HashMap<>();
            github.compareFiles(owner, repo, base, head).forEach(f -> patches.put(f.filename(), f.patch()));
            return patches;
        } catch (RestClientResponseException e) {
            log.warn("Could not compare {}...{} ({}), reviewing the whole PR", shortSha(base), shortSha(head), e.getStatusCode());
            return null;
        }
    }

    private static String shortSha(String sha) {
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }

    static Optional<String> languageOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? Optional.empty() : Optional.ofNullable(LANGUAGES.get(filename.substring(dot + 1).toLowerCase()));
    }

    static String commentBody(ReviewFinding f) {
        return """
                %s **%s · %s** · %s

                %s

                **Suggestion:** %s

                <sub>🤖 AgentForge PR Reviewer</sub>""".formatted(
                icon(f.severity()), f.severity(), f.category(), f.title(), f.explanation(), f.suggestion());
    }

    private static String reviewBody(List<FileReview> files, List<String> skipped, int commentCount, String reviewedSince) {
        StringBuilder body = new StringBuilder("## 🤖 AgentForge review\n\n");
        if (reviewedSince != null) {
            body.append("Reviewed the changes pushed since `").append(shortSha(reviewedSince)).append("`.\n\n");
        }
        if (commentCount == 0) {
            body.append("No issues found in the changed lines. ✅\n\n");
        } else {
            body.append("Found **").append(commentCount).append("** issue(s) in the changed lines, see inline comments.\n\n");
        }
        body.append("| File | Issues in this PR | Pre-existing issues (not commented) |\n|---|---|---|\n");
        files.forEach(f -> body.append("| `").append(f.path()).append("` | ").append(f.findings().size())
                .append(" | ").append(f.outsideDiff().size()).append(" |\n"));
        if (!skipped.isEmpty()) {
            body.append("\n<details><summary>Skipped files</summary>\n\n");
            skipped.forEach(s -> body.append("- ").append(s).append('\n'));
            body.append("</details>\n");
        }
        body.append("\n<sub>Reviewed by security, performance and bug agents, checked by a judge agent.</sub>");
        return body.toString();
    }

    private static String icon(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "🔴";
            case HIGH -> "🟠";
            case MEDIUM -> "🟡";
            case LOW, INFO -> "🔵";
        };
    }
}
