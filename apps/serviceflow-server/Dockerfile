FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

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
