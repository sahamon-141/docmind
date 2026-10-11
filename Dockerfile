# Spring Boot backend image.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests && cp target/docmind-*.jar app.jar

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 docmind
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER docmind
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
