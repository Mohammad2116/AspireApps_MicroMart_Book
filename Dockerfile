# Reuse the tested JAR, or use the default multi-stage source build.
# Base OS patch installation requires network access on an uncached build.
FROM eclipse-temurin:21-jre-alpine@sha256:82cf3d9ccbe1ed46252008d51b2f2329e6b9e8a2f4394c4bebefc3ea7f4b1372 AS base
# Explicit security patch; verified by the image scan.
RUN apk add --no-cache libexpat=2.10.0-r0
WORKDIR /app
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=3 CMD ["java","-cp","/app/health","HealthProbe"]
ENTRYPOINT ["java","-jar","/app/application.jar"]

FROM base AS runtime-from-tested
COPY --chown=10001:10001 application/target/application-1.0.0-SNAPSHOT.jar /app/application.jar
COPY --chown=10001:10001 ops/health/HealthProbe.class /app/health/HealthProbe.class


FROM eclipse-temurin:21-jdk@sha256:85f00967bcc624fc19fa9c2cf124ea426a5363898e267141726f31f358c2e14b AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /source
COPY . .
RUN ./mvnw -B -ntp -DskipTests package

FROM build AS probe
RUN mkdir -p /probe && javac -d /probe ops/HealthProbe.java

FROM base AS runtime
COPY --from=build --chown=10001:10001 /source/application/target/application-1.0.0-SNAPSHOT.jar /app/application.jar
COPY --from=probe --chown=10001:10001 /probe/HealthProbe.class /app/health/HealthProbe.class
