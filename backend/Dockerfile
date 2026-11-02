# syntax=docker/dockerfile:1.7
# =====================================================================
#  Imagen de cualquier servicio del backend. Uso:
#    docker build --build-arg SERVICIO=ms-flota -t rutagt/ms-flota backend/
#  La etapa "build" compila TODO el reactor una sola vez; docker compose la reutiliza (caché)
#  para las 8 imágenes. La imagen final solo contiene el JRE y el JAR del servicio.
# =====================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY discovery-server/pom.xml discovery-server/
COPY api-gateway/pom.xml api-gateway/
COPY ms-usuarios/pom.xml ms-usuarios/
COPY ms-flota/pom.xml ms-flota/
COPY ms-viajes/pom.xml ms-viajes/
COPY ms-clientes/pom.xml ms-clientes/
COPY ms-finanzas/pom.xml ms-finanzas/
COPY ms-notificaciones/pom.xml ms-notificaciones/
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline -DexcludeArtifactIds=common-lib || true
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
ARG SERVICIO
ENV TZ=America/Guatemala \
    JAVA_OPTS="-Xms96m -Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
RUN apk add --no-cache tzdata wget && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/${SERVICIO}/target/${SERVICIO}-*.jar app.jar
USER app
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
