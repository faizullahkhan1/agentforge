# AgentForge

A Java agent-orchestration engine, plus real apps built on it:

| Module | Status | What it does |
|---|---|---|
| `pr-reviewer` | 🚧 Phase 0 | Multi-agent AI code review for GitHub pull requests |
| `invoice-processor` | planned | Extract → validate → categorize invoices with AI agents |
| `agentforge-core` | planned | The engine: agents, workflows, retries, traces (extracted from the apps) |

**Stack:** Java 21 · Spring Boot 3.5 · LangChain4j · PostgreSQL + pgvector · Docker Compose · OpenAI (or local Ollama)

## Quick start (Docker)

1. Copy `.env.example` to `.env` and put your OpenAI API key in it.
2. Start everything:
   ```bash
   docker compose up -d --build
   ```
3. Try it:
   - http://localhost:8090/api/hello-ai?message=What%20is%20a%20record%20in%20Java
   - http://localhost:8090/actuator/health (should show `db` and the app as `UP`)

   The app listens on 8080 inside the container; `APP_PORT` in `.env` picks the port on your PC (default 8090).

Stop with `docker compose down` (add `-v` to also delete the database volume).

## Running from IntelliJ / Maven (for development)

Start only the database in Docker, and run the app on your machine:

```bash
docker compose up -d postgres
```

Then switch the terminal to Java 21 (only that window changes) and run:

```powershell
. .\use-java21.ps1
mvn -pl pr-reviewer spring-boot:run
```

In IntelliJ, set **Project Structure → SDK** to the Java 21 JDK. The app reads `.env` from the repo root automatically.

## Optional: free local LLM with Ollama

```bash
docker compose --profile local-llm up -d ollama
docker compose exec ollama ollama pull llama3.2:3b
```

(Wiring the app to Ollama comes in a later phase.)
