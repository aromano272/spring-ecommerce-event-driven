FROM gradle:8.5-jdk21 AS build
COPY . /app
WORKDIR /app
RUN gradle :ingester:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/ingester/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar"]