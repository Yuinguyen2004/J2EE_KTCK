FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace/server

COPY server/pom.xml server/checkstyle.xml ./
COPY server/src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app

COPY --from=build --chown=app:app /workspace/server/target/billiard-shop-server-0.0.1-SNAPSHOT.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
