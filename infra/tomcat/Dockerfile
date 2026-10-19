# Tomcat 11 with the lessor extranet (legacy-portal-war), built from the back-end sources.
#   docker build -f infra/tomcat/Dockerfile --build-context tomcat=infra/tomcat back
# The application is a plain WAR deployed in an external Tomcat, as it was in production for years; Apache httpd sits
# in front of it (infra/apache).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY config config
COPY legacy-portal-war legacy-portal-war
RUN chmod +x mvnw && ./mvnw -B -q -N install \
    && ./mvnw -B -q -f legacy-portal-war/pom.xml package -DskipTests -Dcheckstyle.skip

FROM tomcat:11.0-jdk25-temurin
# The default applications (ROOT, examples, manager) are never deployed in production
RUN rm -rf /usr/local/tomcat/webapps/* /usr/local/tomcat/webapps.dist \
    && groupadd --system tomcat && useradd --system --gid tomcat --home /usr/local/tomcat tomcat
COPY --from=tomcat server.xml /usr/local/tomcat/conf/server.xml
COPY --from=build /workspace/legacy-portal-war/target/extranet.war /usr/local/tomcat/webapps/extranet.war
RUN chown -R tomcat:tomcat /usr/local/tomcat/webapps /usr/local/tomcat/work /usr/local/tomcat/temp /usr/local/tomcat/logs
USER tomcat
ENV CATALINA_OPTS="-XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8 -Duser.timezone=Europe/Paris"
EXPOSE 8080
