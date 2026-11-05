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

# Run as a normal user instead of root, so a bug in the app can't change the
# container's system files.
RUN useradd --system --no-create-home jcash
USER jcash

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
# MaxRAMPercentage lets the JVM use 75% of the container's memory (Render's
# free plan has 512 MB); without it Java assumes a much smaller heap.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
