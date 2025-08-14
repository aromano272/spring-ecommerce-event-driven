FROM gradle:8.5-jdk21 AS build
COPY . /app
WORKDIR /app
RUN gradle :admindashboard:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/admindashboard/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]