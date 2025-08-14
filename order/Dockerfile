# Build
FROM gradle:8.12-jdk21 AS build
COPY . /app
WORKDIR /app
RUN gradle --no-daemon :order:bootJar

# Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/order/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]