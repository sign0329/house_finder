# syntax=docker/dockerfile:1
# Same patched Java release, pinned official multi-architecture images.
# Avoid GitHub release archive downloads during every Docker build.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21.0.13_9-jdk-jammy@sha256:4b1a74fbffbe6b4e7b6f0171a472478282fb463790a51edf10e465d578f22b69 AS java-jdk

FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-21 AS build
RUN rm -rf /opt/java/openjdk
COPY --from=java-jdk /opt/java/openjdk /opt/java/openjdk
ENV MAVEN_HOME=/usr/share/maven
ENV PATH="/usr/share/maven/bin:${PATH}"
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp verify

FROM eclipse-temurin:21.0.13_9-jre-jammy@sha256:1eac8b8fee11ca7d6725a48bc5d41992ed1561504ebae25490d2d062847e8269 AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /workspace/target/apartment-search-0.1.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
