package dev.agentforge.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.agentforge.core.run.Steps.Step;

class StepsTest {

    @Test
    void recordsEachSuccessfulStepInOrder() {
        List<Step> trace = new ArrayList<>();

        String text = Steps.time(trace, "read-pdf", false, () -> "invoice text");
        int length = Steps.time(trace, "extractor", true, text::length);

        assertThat(length).isEqualTo(12);
        assertThat(trace).extracting(Step::name, Step::usesAi)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("read-pdf", false),
                        org.assertj.core.groups.Tuple.tuple("extractor", true));
    }

    @Test
    void rethrowsCheckedExceptionsAsTheyAreAndRecordsNothing() {
        List<Step> trace = new ArrayList<>();

        assertThatThrownBy(() -> Steps.time(trace, "read-pdf", false, () -> {
            throw new IOException("not a PDF");
        })).isInstanceOf(IOException.class).hasMessage("not a PDF");
        assertThat(trace).isEmpty();
    }
}
