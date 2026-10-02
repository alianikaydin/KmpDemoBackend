# Runtime image only: build the distribution first with `./gradlew :server:installDist`.
FROM eclipse-temurin:21-jre-noble

RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY build/install/server/ /app/

USER app
EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    LOG_FORMAT=json
ENTRYPOINT ["/app/bin/server"]
