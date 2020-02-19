FROM openliberty/open-liberty:full-java13-openj9-ubi as builder

# Install Infinispan jars
USER root
RUN dnf install -y maven \
    && mkdir -p /opt/ol/wlp/usr/shared/resources/infinispan \
    && echo '<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">  <modelVersion>4.0.0</modelVersion>   <groupId>io.openliberty</groupId>  <artifactId>openliberty-infinispan-client</artifactId>  <version>1.0</version>  <!-- https://mvnrepository.com/artifact/org.infinispan/infinispan-jcache-remote -->  <dependencies>    <dependency>      <groupId>org.infinispan</groupId>      <artifactId>infinispan-jcache-remote</artifactId>      <version>10.1.0.Final</version>    </dependency>  </dependencies></project>' > /opt/ol/wlp/usr/shared/resources/infinispan/pom.xml \
    && mvn -f /opt/ol/wlp/usr/shared/resources/infinispan/pom.xml dependency:copy-dependencies -DoutputDirectory=/opt/ol/wlp/usr/shared/resources/infinispan \
    && dnf remove -y maven \
    && rm -f /opt/ol/wlp/usr/shared/resources/infinispan/pom.xml \
    && rm -f /opt/ol/wlp/usr/shared/resources/infinispan/jboss-transaction-api*.jar \
    && rm -f /opt/ol/wlp/usr/shared/resources/infinispan/reactive-streams-*.jar \
    && rm -f /opt/ol/wlp/usr/shared/resources/infinispan/rxjava-*.jar \
    && rm -rf ~/.m2 \
    && chown -R 1001:0 /opt/ol/wlp/usr/shared/resources/infinispan \
    && chmod -R g+rw /opt/ol/wlp/usr/shared/resources/infinispan
USER 1001

FROM openliberty/open-liberty:full-java13-openj9-ubi
COPY --from=builder /opt/ol/wlp/usr/shared/resources/infinispan /opt/ol/wlp/usr/shared/resources/infinispan
COPY server.xml /config/
COPY jvm.options /config/jvm.options
COPY modresorts.war /config/dropins
RUN /opt/ol/helpers/build/configure.sh