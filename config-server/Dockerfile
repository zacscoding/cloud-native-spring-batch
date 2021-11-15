FROM openjdk:8-jdk-alpine as build
WORKDIR application
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=layertools -jar application.jar extract

FROM openjdk:8-jdk-alpine
RUN apk add --no-cache git
WORKDIR application
COPY --from=build application/dependencies/ ./
COPY --from=build application/snapshot-dependencies/ ./
COPY --from=build application/application/ ./
COPY --from=build application/spring-boot-loader/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]