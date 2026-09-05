# Three stages: build the jar, extract its Spring Boot layers, then assemble the smallest
# possible runtime image from just those layers. See docs/deployment.md for the full
# reasoning; the short version of why each stage exists:
#
# - "build" needs the JDK and Maven, and downloads the entire dependency tree - none of that
#   belongs in the image that actually runs in production.
# - The layer-extraction step is what makes rebuilds cheap: Spring Boot's own repackaged jar
#   is already split into layers ordered by how often they change (dependencies change rarely;
#   this project's own compiled classes change on every commit). Copying those layers
#   separately means Docker's layer cache can reuse the untouched dependency layers on every
#   rebuild, so a routine code change produces a small image push instead of re-transferring
#   every third-party jar again.
# - "runtime" only ever contains a JRE (not a full JDK) and those extracted layers - nothing
#   else from the build stage (no Maven, no source, no build cache) ships in the final image.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependencies are resolved from pom.xml alone, before any source is copied in, so this layer
# only gets invalidated (and re-downloaded) when pom.xml itself changes - not on every code
# change, which is the overwhelmingly more common case during development.
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /app
COPY --from=build /build/target/collabflow-backend.jar app.jar
# --destination is required here: left to its default, extract would place the layers under
# ./app/ (named after the jar), colliding with app.jar already sitting in this same directory.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine AS runtime
# Never run the app as root inside the container - if the JVM process is ever compromised
# through some dependency vulnerability, this limits what it can do to the container's own
# filesystem/process space rather than running with full root privileges inside it.
RUN addgroup -S collabflow && adduser -S collabflow -G collabflow
WORKDIR /app
COPY --from=extract --chown=collabflow:collabflow /app/extracted/dependencies/ ./
COPY --from=extract --chown=collabflow:collabflow /app/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=collabflow:collabflow /app/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=collabflow:collabflow /app/extracted/application/ ./
USER collabflow:collabflow
EXPOSE 8080
# -XX:MaxRAMPercentage instead of a fixed -Xmx: lets the JVM size its heap relative to
# whatever memory limit the container is actually run with (docker-compose.yml, or a
# Kubernetes resources.limits.memory later) instead of a value hardcoded here that would be
# wrong the moment the container's memory limit changes. Container-memory-awareness is on by
# default on this JDK version, so no -XX:+UseContainerSupport flag is needed to enable it.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
