ARG WILDFLY_IMAGE=quay.io/wildfly/wildfly:latest
FROM ${WILDFLY_IMAGE}

COPY target/jbossqueues.war /opt/jboss/wildfly/standalone/deployments/ROOT.war

EXPOSE 8080
