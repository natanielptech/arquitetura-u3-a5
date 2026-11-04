# Uso: docker build --build-arg MODULE=order-service -t order-service:1.0.0 .
# Multi-stage: imagem final só com JRE e usuário não-root.
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
RUN mvn -q -B -pl ${MODULE} -am dependency:go-offline
COPY . .
RUN mvn -q -B -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
RUN useradd --system --uid 10001 app
USER 10001
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-1.0.0.jar app.jar
# Respeita os limits do container (cgroups) e dimensiona o heap em % da memória.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
