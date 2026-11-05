# Backend image for Render. Built in two stages so the final image has only the
# Java runtime, not the JDK, Maven, or source. See docs/deployment.md.

# ---- Stage 1: build the jar ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dependencies first, so Docker reuses this cached layer until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN ./mvnw -B -q dependency:go-offline

# Tests need PostgreSQL, so they run locally, not here.
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# ---- Stage 2: run the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Light JIT and GC for the free plan's 0.1 CPU; heap sized from the 512 MB limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -XX:+UseSerialGC"

# CDS needs the jar unpacked; extract requires an empty destination, hence /tmp.
COPY --from=build /app/target/*.jar /tmp/build.jar
RUN java -Djarmode=tools -jar /tmp/build.jar extract \
      --destination /app --application-filename app.jar \
    && rm /tmp/build.jar

# CDS training run: start once without a database and save the loaded classes
# to app.jsa, which real starts reuse.
RUN DB_PASSWORD=training ACTUATOR_USERNAME=training ACTUATOR_PASSWORD=training \
    java $JAVA_OPTS -XX:ArchiveClassesAtExit=app.jsa \
      -Dspring.context.exit=onRefresh \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -jar app.jar

# Don't run as root.
RUN useradd --system --no-create-home jcash
USER jcash

EXPOSE 8080
# sh expands $JAVA_OPTS; exec lets Java receive the stop signal directly.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:SharedArchiveFile=app.jsa -jar app.jar"]
