# Same recipe as pr-reviewer/Dockerfile. Build context is the repo root.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY pr-reviewer/pom.xml pr-reviewer/
COPY invoice-processor/pom.xml invoice-processor/
COPY invoice-processor/src invoice-processor/src

# Shares the same Maven cache as pr-reviewer, so common libraries (Spring, LangChain4j) are downloaded once
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl invoice-processor -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/invoice-processor/target/invoice-processor.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
