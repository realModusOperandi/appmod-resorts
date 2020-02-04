# Deploying to OpenShift with Session Persistence
## Configuring the Cluster
> Note: all steps assume you are logged in to the cluster on the command line.
### Install the OpenLiberty operator
*Reference: https://github.com/OpenLiberty/open-liberty-operator/tree/master/deploy/releases/0.3.0#open-liberty-operator-v030*

This step will deploy the Open Liberty Operator to your cluster. If the operator is already set up and watching the project/namespace you intend to deploy the resorts app server to, you can skip this step.
Install the OpenLibertyApplication Custom Resource Definitions:
```shell script
oc apply -f https://raw.githubusercontent.com/OpenLiberty/open-liberty-operator/master/deploy/releases/0.3.0/openliberty-app-crd.yaml
``` 
Select the projects/namespaces for the operator to watch by setting variables to be used later:
  * Provide a single namespace for the operator pod to deploy to.
  * Provide a namespace to watch, a comma-separated list of namespaces to watch, or the empty string `'""'` to watch all namespaces in the cluster.
```shell script
OPERATOR_NAMESPACE=<SPECIFY_OPERATOR_NAMESPACE_HERE>
WATCH_NAMESPACE=<SPECIFY_WATCH_NAMESPACE_HERE>
```
If the operator is watching a namespace other than the one it is deployed to, install cluster-level role-based access. Be sure the variables above are defined so they are properly included in the command:
```shell script
curl -L https://raw.githubusercontent.com/OpenLiberty/open-liberty-operator/master/deploy/releases/0.3.0/openliberty-app-cluster-rbac.yaml \
  | sed -e "s/OPEN_LIBERTY_OPERATOR_NAMESPACE/${OPERATOR_NAMESPACE}/" \
  | oc apply -f -
```
Install the operator:
```shell script
curl -L https://raw.githubusercontent.com/OpenLiberty/open-liberty-operator/master/deploy/releases/0.3.0/openliberty-app-operator.yaml \
  | sed -e "s/OPEN_LIBERTY_WATCH_NAMESPACE/${WATCH_NAMESPACE}/" \
  | oc apply -n ${OPERATOR_NAMESPACE} -f -
```
   
### Install the Infinispan Operator
*Reference: https://infinispan.org/infinispan-operator/master/operator.html*

> Note: for this step, ensure you have the project you intend to deploy the Infinispan server to set as your current project in `oc`.

Install the Infinispan Custom Resource Definition:
```shell script
oc apply -f https://raw.githubusercontent.com/infinispan/infinispan-operator/master/deploy/crd.yaml
```

Install the role-based access for the operator:
```shell script
oc apply -f https://raw.githubusercontent.com/infinispan/infinispan-operator/master/deploy/rbac.yaml
```

Install the operator:
```shell script
oc apply -f https://raw.githubusercontent.com/infinispan/infinispan-operator/master/deploy/operator.yaml
```
### Create PersistentVolumes
Each Infinispan server created by the operator will need a suitable `PersistentVolume` in order to be scheduled in the cluster. The details are specific to the destination cluster, but the volume should be 2Gi or larger and have a mode of `ReadWriteOnce`.
   
## Deploying the Application
### Set Up Infinispan
In the `docker` directory, open the file `infinispan.yaml` which looks like this by default:
```yaml
# Assumes the Infinispan operator has been installed in your cluster
apiVersion: infinispan.org/v1
kind: Infinispan
metadata:
  name: infinispan-server
spec:
  replicas: 1
```

Update the `name` element to the name you want to use for the server. This will become the service name, so it should be unique. Leave `replicas` at 1 unless you know you have enough `PersistentVolume`s to handle each replica.

When the server pod's status is Ready and the line `Infinispan Server 10.1.1.Final started in 21301ms` appears in the logs, the server is ready.

### Create the Application Image
Build the application with `mvn package`. The application is currently built at `data/example/modresorts-1.0.war`. Copy that file to the `docker` directory.

Update the Liberty `server.xml` file with the necessary configuration:
```xml
<server>
    <featureManager>
        <feature>javaee-8.0</feature>
        <feature>sessionCache-1.0</feature>
    </featureManager>

    <httpEndpoint httpPort="9080" httpsPort="9443"
                  id="defaultHttpEndpoint" host="*" />

    <httpSessionCache libraryRef="InfinispanLib">
        <properties infinispan.client.hotrod.server_list="infinispan-server:11222"/>
        <properties infinispan.client.hotrod.marshaller="org.infinispan.commons.marshall.JavaSerializationMarshaller"/>
        <properties infinispan.client.hotrod.auth_username="developer"/>
        <properties infinispan.client.hotrod.auth_password="wXQhTvK48VLX3Lgs"/>
        <properties infinispan.client.hotrod.auth_realm="default"/>
        <properties infinispan.client.hotrod.auth_server_name="infinispan"/>
        <properties infinispan.client.hotrod.sasl_mechanism="DIGEST-MD5"/>
    </httpSessionCache>

    <library id="InfinispanLib">
        <fileset dir="${shared.resource.dir}/infinispan" includes="*.jar"/>
    </library>

</server>
```

The `sessionCache-1.0` feature enables the integration with Infinispan. The `httpSessionCache` element defines the properties used to configure the connection to the Infinispan server. Update each property:
* `server_list`: The hostname and port of the server. Since both are deployed to the same OpenShift cluster, the hostname is the name of the service defined for the Infinispan server.
* `auth_username`: The username to authenticate with. Unless you configured custom credentials, the username is probably `developer`. Locate the `<infinispan-server-name>-generated-secret` and decode it's value to see the credentials generated automatically.
* `auth_password`: The password for the username specified above.
* `auth_realm`: Probably `default`.
* `auth_server_name` Probably `infinispan`. Can be found in the Infinispan server pod in the file `/opt/infinispan/server/conf/infinispan.xml`.

The other properties can be left as-is.

Examine the `Dockerfile`:
```Dockerfile
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
```
 This is a multi-stage build. The first stage fetches the Infinispan dependency jars from Maven Central. It fetches all the jars necessary for using Infinispan with Java SE, so several API jars that conflict with APIs provided by Liberty are removed.

In the second stage, the Dockerfile copies over the dependencies and places the app, `jvm.options`, and `server.xml` files in the appropriate place.

Build this image, tag it, and push it. You may need to log in to your Docker registry. (commands are executed from the `docker` directory in the project.)

```shell script
HOST=$(oc get route default-route -n openshift-image-registry --template='{{ .spec.host }}')
PROJECT=<enter OpenShift project name here>
docker login -u $(oc whoami) -p $(oc whoami -t) $HOST
docker build -t modresorts:1.0 .
docker tag modresorts:1.0 $HOST/$PROJECT/modresorts:1.0
docker push $HOST/$PROJECT/modresorts:1.0
```

### Deploy the Application
Inspect the `openliberty.yaml` file:
```yaml
# Assumes the OpenLiberty operator has been installed in your cluster
apiVersion: openliberty.io/v1beta1
kind: OpenLibertyApplication
metadata:
  name: modresorts
spec:
  applicationImage: <image name here>
  replicas: 2
  service:
    type: ClusterIP
    port: 9080
  expose: true
```

Update the `applicationImage` field with the name of the image you pushed. 

> If you pushed to the internal OpenShift image registry, alter the repository name so that it reads `image-registry.openshift-image-registry.svc:5000/` instead of the value for `$HOST`. All other portions after `$HOST` remain unchanged.

Adjust the amount of replicas to create if you want, but provision at least 2 replicas if you want to demo the session caching in action.

Apply the custom resource to deploy the application:

```shell script
oc apply -f openliberty.yaml
```

Watch the deployment in the web console. When the pods become ready, you can access the application via the Route that was automatically created. Be sure to add the context root of `resorts` to the end of the URL.