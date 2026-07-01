FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S parkflow && adduser -S parkflow -G parkflow
COPY --from=build /app/target/parkflow-live-demo-1.0.0.jar app.jar
USER parkflow
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
