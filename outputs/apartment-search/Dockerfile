# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jre AS os-base
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* /opt/java/openjdk

# Official Java 21.0.13 archives; hashes are reviewed with each patch update.
FROM os-base AS java-download
ARG TARGETARCH
COPY docker/install-java.sh docker/java21-checksums.txt /tmp/
RUN sh /tmp/install-java.sh "$TARGETARCH" jdk /opt/jdk \
    && sh /tmp/install-java.sh "$TARGETARCH" jre /opt/jre

FROM maven:3.9.9-eclipse-temurin-21 AS maven-tool
FROM os-base AS build
COPY --from=java-download /opt/jdk /opt/java/openjdk
COPY --from=maven-tool /usr/share/maven /usr/share/maven
ENV MAVEN_HOME=/usr/share/maven
ENV PATH="/usr/share/maven/bin:${PATH}"
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp verify

FROM os-base AS runtime
COPY --from=java-download /opt/jre /opt/java/openjdk
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /workspace/target/apartment-search-0.1.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
