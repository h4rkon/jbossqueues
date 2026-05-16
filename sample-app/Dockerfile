FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -B package

FROM quay.io/wildfly/wildfly:latest
COPY --from=build /workspace/target/wildfly-kafka-sample.war /opt/jboss/wildfly/standalone/deployments/ROOT.war

