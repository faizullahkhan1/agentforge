# Build context is the repo root (see docker-compose.yml), because the module needs the parent pom.

# ---- Stage 1: build the jar with Maven + JDK 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only the poms first and download dependencies. Docker caches this layer,
# so rebuilding after a code change doesn't re-download the internet.
COPY pom.xml .
COPY pr-reviewer/pom.xml pr-reviewer/
RUN mvn -B -q -pl pr-reviewer -am dependency:go-offline

COPY pr-reviewer/src pr-reviewer/src
RUN mvn -B -q -pl pr-reviewer -am package -DskipTests

# ---- Stage 2: small runtime image, just a JRE and the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/pr-reviewer/target/pr-reviewer.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
