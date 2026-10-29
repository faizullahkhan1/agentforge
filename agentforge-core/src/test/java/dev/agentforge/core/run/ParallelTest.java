package dev.agentforge.core.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;

import dev.agentforge.core.run.Parallel.Outcome;

class ParallelTest {

    @Test
    void runsTasksAtTheSameTimeAndKeepsTheirOrder() {
        Map<String, Callable<String>> tasks = new LinkedHashMap<>();
        tasks.put("security", () -> sleepThen(300, "s"));
        tasks.put("performance", () -> sleepThen(300, "p"));
        tasks.put("bugs", () -> sleepThen(300, "b"));

        long start = System.nanoTime();
        List<Outcome<String>> outcomes = Parallel.runAll(tasks);
        long took = Steps.millisSince(start);

        assertThat(outcomes).extracting(Outcome::name).containsExactly("security", "performance", "bugs");
        assertThat(outcomes).extracting(Outcome::value).containsExactly("s", "p", "b");
        assertThat(took).as("3 x 300 ms in parallel, not 900 ms in a row").isLessThan(750);
    }

    @Test
    void aFailingTaskIsReportedWithoutStoppingTheOthers() {
        Map<String, Callable<String>> tasks = new LinkedHashMap<>();
        tasks.put("ok", () -> "fine");
        tasks.put("broken", () -> {
            throw new IllegalStateException("model timed out");
        });
        tasks.put("npe", () -> {
            throw new NullPointerException();
        });

        List<Outcome<String>> outcomes = Parallel.runAll(tasks);

        assertThat(outcomes.get(0).succeeded()).isTrue();
        assertThat(outcomes.get(1).succeeded()).isFalse();
        assertThat(outcomes.get(1).errorMessage()).isEqualTo("model timed out");
        assertThat(outcomes.get(2).errorMessage()).isEqualTo("NullPointerException");
    }

    private static String sleepThen(long millis, String value) throws InterruptedException {
        Thread.sleep(millis);
        return value;
    }
}
