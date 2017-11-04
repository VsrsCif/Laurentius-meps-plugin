FROM jrihtarsic/laurentius

ENV VERSION 2.0-SNAPSHOT
ENV SERVER_VERSION wildfly-11.0
ENV LAU_PROJECT .
ENV WILDFLY_HOME /opt/jboss/wildfly


# application modules
ADD $LAU_PROJECT/target/plugin-meps.war $WILDFLY_HOME/standalone/deployments/


# Set the default command to run on boot
CMD ["/opt/jboss/wildfly/bin/laurentius-demo.sh", "--init", "-d", "test-laurentius.si"]
