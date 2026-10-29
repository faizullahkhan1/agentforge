package dev.agentforge.core.run;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs several tasks (typically agent calls) at the same time, one Java 21 virtual thread each.
 * The total time is roughly the slowest task, not the sum. A task that fails doesn't stop the
 * others: its failure is returned as an Outcome, and the caller decides what to do with it.
 */
public final class Parallel {

    /** What one task produced: its value, or the error it failed with. Both carry how long it took. */
    public record Outcome<T>(String name, T value, Throwable error, long millis) {

        public boolean succeeded() {
            return error == null;
        }

        /** The error message, or the exception type when there is no message (e.g. a NullPointerException). */
        public String errorMessage() {
            return error == null ? null
                    : error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        }
    }

    /** Outcomes come back in the same order as the tasks were given (use a LinkedHashMap). */
    public static <T> List<Outcome<T>> runAll(Map<String, Callable<T>> tasks) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Outcome<T>>> futures = tasks.entrySet().stream()
                    .map(task -> CompletableFuture.supplyAsync(() -> run(task.getKey(), task.getValue()), executor))
                    .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }

    private static <T> Outcome<T> run(String name, Callable<T> task) {
        long start = System.nanoTime();
        try {
            return new Outcome<>(name, task.call(), null, Steps.millisSince(start));
        } catch (Exception e) {
            return new Outcome<>(name, null, e, Steps.millisSince(start));
        }
    }

    private Parallel() {
    }
}
