package dev.agentforge.invoices;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.agentforge.invoices.store.InvoiceService;

/**
 * Starts the whole application once (real Postgres via Testcontainers, a dummy API key, no AI calls),
 * so wiring and startup problems fail mvn test instead of only showing up in Docker.
 */
@SpringBootTest(properties = "langchain4j.open-ai.chat-model.api-key=test-key-not-used")
@Testcontainers
class InvoiceProcessorApplicationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    InvoiceService invoices;

    @Test
    void applicationStartsWithPipelineAndDatabaseWired() {
        assertThat(invoices).isNotNull();
    }
}
