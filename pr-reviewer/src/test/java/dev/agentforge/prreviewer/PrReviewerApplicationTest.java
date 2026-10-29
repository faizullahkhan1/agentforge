package dev.agentforge.prreviewer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.agentforge.prreviewer.review.agents.JudgeAgent;

/**
 * Starts the whole application once (real Postgres via Testcontainers, a dummy API key, no AI calls).
 * Unit tests don't start Spring, so broken wiring only showed up when the app booted: this test exists
 * because extracting agentforge-core once made the judge fail at startup with two HTTP clients on the classpath.
 */
@SpringBootTest(properties = "langchain4j.open-ai.chat-model.api-key=test-key-not-used")
@Testcontainers
class PrReviewerApplicationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    JudgeAgent judge;

    @Test
    void applicationStartsWithAllAgentsWired() {
        assertThat(judge).isNotNull();
    }
}
