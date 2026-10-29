package dev.agentforge.core.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Settings, paths and report files for the evals (tests that call the real API, see docs/evals.md).
 * Lives in core, not in a test folder, so both apps share one copy; it uses only the JDK.
 */
public final class EvalSupport {

    /** The repo root, found by walking up from the module folder Maven runs tests in. */
    public static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("samples"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Could not find the repo root (a folder containing samples/)");
        }
        return dir;
    }

    /** Environment variable first, then the repo-root .env file (same order as the apps use). */
    public static Optional<String> setting(String name) {
        String env = System.getenv(name);
        if (env != null && !env.isBlank()) {
            return Optional.of(env.strip());
        }
        Path dotEnv = repoRoot().resolve(".env");
        if (!Files.exists(dotEnv)) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(dotEnv);
            return lines.stream()
                    .map(String::strip)
                    .filter(l -> l.startsWith(name + "="))
                    .map(l -> l.substring(name.length() + 1).strip())
                    .filter(v -> !v.isEmpty())
                    .findFirst();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read .env", e);
        }
    }

    public static String setting(String name, String defaultValue) {
        return setting(name).orElse(defaultValue);
    }

    public static String requiredSetting(String name) {
        return setting(name).orElseThrow(() -> new IllegalStateException(
                name + " is not set (as an environment variable or in .env); evals call the real API"));
    }

    public static int intProperty(String name, int defaultValue) {
        return Integer.parseInt(System.getProperty(name, String.valueOf(defaultValue)));
    }

    /** Writes the report to eval-results/ (git-ignored) and returns its path. */
    public static Path writeReport(String prefix, String markdown) throws IOException {
        Path dir = repoRoot().resolve("eval-results");
        Files.createDirectories(dir);
        Path file = dir.resolve(prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md");
        Files.writeString(file, markdown);
        return file;
    }

    public static String percent(long part, long whole) {
        return whole == 0 ? "-" : Math.round(100.0 * part / whole) + "%";
    }

    private EvalSupport() {
    }
}
