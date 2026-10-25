FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY build-settings.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -s build-settings.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 serviceflow \
    && mkdir -p /data/uploads \
    && chown -R serviceflow:serviceflow /app /data
COPY --from=build /workspace/target/serviceflow-server-*.jar app.jar
USER serviceflow
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 CMD curl --fail http://localhost:8080/api/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
