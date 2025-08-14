## Build
#FROM gradle:8.5-jdk21 AS build
#COPY . /app
#WORKDIR /app
#RUN gradle --no-daemon :transformer:bootJar
#
## Run
#FROM eclipse-temurin:21-jre
#WORKDIR /app
#COPY --from=build /app/transformer/build/libs/*.jar app.jar
#EXPOSE 8083
#ENTRYPOINT ["java","-jar","/app/app.jar"]

#FROM openjdk:21
#WORKDIR /transformer
#CMD ["./gradlew", "clean", "bootJar"]
#COPY build/libs/*.jar app.jar
#
#EXPOSE 8083
#ENTRYPOINT ["java", "-jar", "app.jar"]

## ---- build ----
#FROM gradle:8.5-jdk21 AS build
#WORKDIR /app
#COPY . .
#RUN gradle --no-daemon clean bootJar
#
## ---- run ----
#FROM eclipse-temurin:21-jre
#WORKDIR /app
#COPY --from=build /app/build/libs/*.jar app.jar
#EXPOSE 8083
#ENTRYPOINT ["java","-jar","/app/app.jar"]

#FROM gradle:8.5-jdk21 AS build
#WORKDIR /app
#COPY settings.gradle.kts settings.gradle.kts
#COPY transformer/build.gradle.kts transformer/build.gradle.kts
#RUN gradle --no-daemon help
#COPY . .
#RUN gradle --no-daemon :transformer:bootJar
#
#FROM eclipse-temurin:21-jre
#WORKDIR /app
#COPY --from=build /app/transformer/build/libs/*.jar app.jar
#EXPOSE 8083
#ENTRYPOINT ["java","-jar","/app/app.jar"]


FROM gradle:8.5-jdk21 AS build
COPY . /app
WORKDIR /app
RUN gradle :transformer:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/transformer/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app/app.jar"]
