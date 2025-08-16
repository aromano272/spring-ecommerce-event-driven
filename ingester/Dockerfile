FROM eclipse-temurin:21-jre
WORKDIR /app

COPY ingester/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar"]