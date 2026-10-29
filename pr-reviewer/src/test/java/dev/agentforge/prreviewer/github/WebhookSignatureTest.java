package dev.agentforge.prreviewer.github;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    // Example from GitHub's docs: "Validating webhook deliveries"
    private static final String SECRET = "It's a Secret to Everybody";
    private static final byte[] BODY = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    private static final String GITHUB_SIGNATURE =
            "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

    @Test
    void matchesGitHubsDocumentedExample() {
        assertThat(WebhookSignature.sign(SECRET, BODY)).isEqualTo(GITHUB_SIGNATURE);
        assertThat(WebhookSignature.isValid(SECRET, BODY, GITHUB_SIGNATURE)).isTrue();
    }

    @Test
    void rejectsWrongSecretChangedBodyOrMissingHeader() {
        assertThat(WebhookSignature.isValid("wrong secret", BODY, GITHUB_SIGNATURE)).isFalse();
        assertThat(WebhookSignature.isValid(SECRET, "Hello, World?".getBytes(StandardCharsets.UTF_8), GITHUB_SIGNATURE)).isFalse();
        assertThat(WebhookSignature.isValid(SECRET, BODY, null)).isFalse();
    }

    @Test
    void rejectsEverythingWhenNoSecretIsConfigured() {
        assertThat(WebhookSignature.isValid("", BODY, GITHUB_SIGNATURE)).isFalse();
        assertThat(WebhookSignature.isValid(null, BODY, GITHUB_SIGNATURE)).isFalse();
    }
}
