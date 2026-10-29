package dev.agentforge.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiffParserTest {

    @Test
    void returnsNewFileLineNumbersOfAddedLines() {
        String patch = """
                @@ -10,4 +10,5 @@ public class A {
                     int a = 1;
                -    int b = 2;
                +    int b = 3;
                +    int c = 4;
                     return;
                """;
        assertThat(DiffParser.addedLines(patch)).containsExactly(11, 12);
    }

    @Test
    void handlesSeveralHunks() {
        String patch = """
                @@ -1,2 +1,3 @@
                 package x;
                +import java.util.List;

                @@ -40 +41,2 @@ class B
                 }
                +// end
                """;
        assertThat(DiffParser.addedLines(patch)).containsExactly(2, 42);
    }

    @Test
    void newFileIsAllAdded() {
        String patch = "@@ -0,0 +1,3 @@\n+a\n+b\n+c";
        assertThat(DiffParser.addedLines(patch)).containsExactly(1, 2, 3);
    }

    @Test
    void ignoresNoNewlineMarkerAndNullPatch() {
        assertThat(DiffParser.addedLines("@@ -1 +1 @@\n-a\n\\ No newline at end of file\n+b")).containsExactly(1);
        assertThat(DiffParser.addedLines(null)).isEmpty();
    }
}
