package dev.agentforge.core.run;

import java.util.List;

/**
 * Times the steps of a pipeline and records them in a trace, so every result can show which steps
 * ran, which of them used AI, and how long each took.
 *
 * <pre>
 * List&lt;Step&gt; trace = new ArrayList&lt;&gt;();
 * Invoice invoice = Steps.time(trace, "extractor", true, () -&gt; extractor.extract(text));
 * </pre>
 */
public final class Steps {

    /** One entry of a trace. usesAi separates (paid, slower, fallible) agent calls from plain Java steps. */
    public record Step(String name, boolean usesAi, long millis) {
    }

    /** Like Callable, but keeps checked exceptions typed, so time() can rethrow e.g. an IOException as is. */
    @FunctionalInterface
    public interface Work<T, E extends Exception> {
        T run() throws E;
    }

    /** Runs the work, adds a Step to the trace if it succeeds, and returns its result. */
    public static <T, E extends Exception> T time(List<Step> trace, String name, boolean usesAi, Work<T, E> work) throws E {
        long start = System.nanoTime();
        T result = work.run();
        trace.add(new Step(name, usesAi, millisSince(start)));
        return result;
    }

    public static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private Steps() {
    }
}
