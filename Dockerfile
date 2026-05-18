ARG WILDFLY_IMAGE=quay.io/wildfly/wildfly:latest
FROM ${WILDFLY_IMAGE}

COPY configure-wildfly.cli /tmp/configure-wildfly.cli
RUN /opt/jboss/wildfly/bin/jboss-cli.sh --file=/tmp/configure-wildfly.cli \
    && rm -rf /opt/jboss/wildfly/standalone/configuration/standalone_xml_history/*

COPY target/jbossqueues.war /opt/jboss/wildfly/standalone/deployments/ROOT.war

EXPOSE 8080
