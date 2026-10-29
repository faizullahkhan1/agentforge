package dev.agentforge.prreviewer.eval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.agentforge.prreviewer.review.ReviewFinding;

/**
 * Grades one review against the answer key. Pure Java, so the grading itself is unit-tested
 * (an eval with a buggy scorer would produce confident, wrong numbers).
 */
final class ReviewScorer {

    /** One known problem: where it is (line range) and words a correct finding would use for it. */
    record Expected(String id, List<Integer> lines, List<String> keywords) {

        boolean matches(ReviewFinding f) {
            boolean inRange = f.line() >= lines.get(0) && f.line() <= lines.get(1);
            String text = (f.title() + " " + f.explanation()).toLowerCase(Locale.ROOT);
            return inRange && keywords.stream().anyMatch(k -> text.contains(k.toLowerCase(Locale.ROOT)));
        }
    }

    /** found = ids of expected problems that at least one finding matched; unexpected = findings that matched nothing. */
    record RunScore(Set<String> found, List<ReviewFinding> unexpected) {
    }

    static RunScore score(List<ReviewFinding> findings, List<Expected> expected) {
        Set<String> found = new LinkedHashSet<>();
        List<ReviewFinding> unexpected = new ArrayList<>();
        for (ReviewFinding f : findings) {
            boolean matchedAny = false;
            for (Expected e : expected) {
                if (e.matches(f)) {
                    found.add(e.id());
                    matchedAny = true;
                }
            }
            if (!matchedAny) {
                unexpected.add(f);
            }
        }
        return new RunScore(found, unexpected);
    }

    private ReviewScorer() {
    }
}
