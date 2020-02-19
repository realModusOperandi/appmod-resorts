FROM openliberty/open-liberty:full-java13-openj9-ubi
### Hazelcast Session Caching ###
# Copy the Hazelcast libraries from the Hazelcast Docker image
COPY --from=hazelcast/hazelcast:4.0 --chown=1001:0 /opt/hazelcast/lib/*.jar /opt/ol/wlp/usr/shared/resources/hazelcast/

# Instruct configure.sh to copy the client topology hazelcast.xml
ARG HZ_SESSION_CACHE=client

# Default setting for the verbose option
ARG VERBOSE=true

COPY server.xml /config/
COPY modresorts.war /config/dropins

RUN /opt/ol/helpers/build/configure.sh