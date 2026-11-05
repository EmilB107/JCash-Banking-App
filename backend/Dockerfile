# Builds and runs the JCash backend in a container (used by Render).
# Two stages: the first has the full JDK and Maven to build the jar, the second
# has only the Java runtime to run it, so the final image is smaller and
# contains no build tools or source code.

# ---- Stage 1: build the jar ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only the Maven files first and download dependencies. Docker caches each
# step, so as long as pom.xml doesn't change, later builds skip this slow step.
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN ./mvnw -B -q dependency:go-offline

# Now copy the source and build. Tests are skipped here because they need a
# PostgreSQL database; run them locally before pushing.
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# ---- Stage 2: run the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# JVM options, chosen because Render's free plan has only 0.1 CPU:
# - MaxRAMPercentage=75: use 75% of the container's 512 MB for the heap
#   (without it Java assumes a much smaller one).
# - TieredStopAtLevel=1: only use the quick JIT compiler, which costs far less
#   CPU while starting; peak speed is a bit lower, which a demo doesn't notice.
# - UseSerialGC: a single-threaded garbage collector, the lightest option for
#   a small container.
# Measured locally, these and the CDS archive below cut startup CPU time ~4x.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -XX:+UseSerialGC"

# Unpack the jar into app.jar + lib/ so the CDS archive can refer to the files.
# The built jar goes to /tmp first because extract needs an empty destination.
COPY --from=build /app/target/*.jar /tmp/build.jar
RUN java -Djarmode=tools -jar /tmp/build.jar extract \
      --destination /app --application-filename app.jar \
    && rm /tmp/build.jar

# Class Data Sharing (CDS): a "training run" starts the app once and saves the
# classes it loaded into app.jsa; real starts then map that file instead of
# loading thousands of classes again. The training run has no database, so it
# turns off Flyway and Hibernate's database checks, uses dummy secrets, and
# exits as soon as the Spring context is ready (spring.context.exit=onRefresh).
RUN DB_PASSWORD=training ACTUATOR_USERNAME=training ACTUATOR_PASSWORD=training \
    java $JAVA_OPTS -XX:ArchiveClassesAtExit=app.jsa \
      -Dspring.context.exit=onRefresh \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -jar app.jar

# Run as a normal user instead of root, so a bug in the app can't change the
# container's system files.
RUN useradd --system --no-create-home jcash
USER jcash

EXPOSE 8080
# "sh -c" so $JAVA_OPTS is expanded; "exec" makes Java the main process so it
# receives Render's stop signal and shuts down cleanly.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:SharedArchiveFile=app.jsa -jar app.jar"]
