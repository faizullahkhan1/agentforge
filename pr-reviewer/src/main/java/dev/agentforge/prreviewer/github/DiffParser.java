package dev.agentforge.prreviewer.github;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a unified diff (the "patch" GitHub gives per file) and returns the line numbers,
 * in the NEW version of the file, of every line the PR added or changed.
 *
 * <pre>
 * @@ -10,4 +10,5 @@          new-file lines start at 10
 *  unchanged                  line 10 (context)
 * -removed                    not in the new file, doesn't advance the counter
 * +added                      line 11  <- reported
 *  unchanged                  line 12
 * </pre>
 */
public final class DiffParser {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    public static Set<Integer> addedLines(String patch) {
        Set<Integer> added = new TreeSet<>();
        if (patch == null) {
            return added;
        }
        int newLine = 0;
        for (String line : patch.split("\n", -1)) {
            Matcher hunk = HUNK_HEADER.matcher(line);
            if (hunk.find()) {
                newLine = Integer.parseInt(hunk.group(1));
            } else if (line.startsWith("+")) {
                added.add(newLine++);
            } else if (line.startsWith(" ")) {
                newLine++;
            }
            // "-" lines and "\ No newline at end of file" don't exist in the new file
        }
        return added;
    }

    private DiffParser() {
    }
}
