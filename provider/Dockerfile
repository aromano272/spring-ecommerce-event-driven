FROM eclipse-temurin:21-jre
WORKDIR /app

COPY provider/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8084
ENTRYPOINT ["java","-jar","/app/app.jar"]