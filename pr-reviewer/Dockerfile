# Build context is the repo root (see docker-compose.yml), because the module needs the parent pom.

# ---- Stage 1: build the jar with Maven + JDK 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Maven reads every module listed in the parent pom, so all module poms must be here,
# even though only pr-reviewer gets built.
COPY pom.xml .
COPY agentforge-core/pom.xml agentforge-core/
COPY pr-reviewer/pom.xml pr-reviewer/
COPY invoice-processor/pom.xml invoice-processor/
# -am ("also make") builds the shared library first, so its sources are needed too
COPY agentforge-core/src agentforge-core/src
COPY pr-reviewer/src pr-reviewer/src

# Cache mount: Maven's download folder (/root/.m2) is kept by Docker between builds, even when
# a pom changes, so only NEW dependencies get downloaded. It is not stored in the image.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl pr-reviewer -am package -DskipTests

# ---- Stage 2: small runtime image, just a JRE and the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/pr-reviewer/target/pr-reviewer.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
