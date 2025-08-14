FROM gradle:8.5-jdk21 AS build
COPY . /app
WORKDIR /app
RUN gradle :dispatcher:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/dispatcher/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]