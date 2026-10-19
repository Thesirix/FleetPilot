# Builds the image of one Spring Boot service of the multi-module project.
#   docker build -f back/Dockerfile --build-arg SERVICE=vehicle-service back
# The build stage compiles with the Maven Wrapper; the runtime stage only contains a JRE and the layered jar.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY config config
COPY fleet-common/pom.xml fleet-common/pom.xml
COPY fleet-messaging/pom.xml fleet-messaging/pom.xml
COPY fleet-documents/pom.xml fleet-documents/pom.xml
COPY vehicle-service/pom.xml vehicle-service/pom.xml
COPY quote-service/pom.xml quote-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY reporting-service/pom.xml reporting-service/pom.xml
COPY telemetry-service/pom.xml telemetry-service/pom.xml
COPY vehicle-simulator/pom.xml vehicle-simulator/pom.xml
COPY integration-esb/pom.xml integration-esb/pom.xml
COPY bigdata-pipeline/pom.xml bigdata-pipeline/pom.xml
COPY energy-service/pom.xml energy-service/pom.xml
COPY graphql-gateway/pom.xml graphql-gateway/pom.xml
COPY legacy-portal-war/pom.xml legacy-portal-war/pom.xml
COPY acceptance-tests/pom.xml acceptance-tests/pom.xml
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline -DexcludeReactor=true || true
COPY . .
ARG SERVICE
RUN ./mvnw -B -q -pl ${SERVICE} -am package -DskipTests -Dcheckstyle.skip \
    && java -Djarmode=tools -jar ${SERVICE}/target/${SERVICE}.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /data/inbox && chown -R app:app /data
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
