# Build: Java 21 + Maven wrapper (the frontend-maven-plugin downloads Node for the React build)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY config config
COPY frontend frontend
COPY src src
RUN ./mvnw -B -DskipTests package

# Run: JRE only, non-root user
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home beacon
WORKDIR /app
COPY --from=build /src/target/beacon.jar beacon.jar
COPY config config
RUN mkdir data && chown beacon:beacon data
USER beacon
EXPOSE 8080
# Inside the container the app must listen on all interfaces; docker-compose publishes it on 127.0.0.1 only
ENTRYPOINT ["java", "-jar", "beacon.jar", "--server.address=0.0.0.0"]
