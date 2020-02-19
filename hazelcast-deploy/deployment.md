# Deploying to OpenShift with Hazelcast Session Persistence
## Configuring the Cluster
> Note: all steps assume you are logged in to the cluster on the command line. These steps also require some permissions that aren't normally given by default, see the appendix
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
   
### Create PersistentVolumes
If you intend to use the Hazelcast management center for your cluster, create a `PersistentVolume` for it. The details are specific to the destination cluster, but the volume should be 8Gi or larger and have a mode of `ReadWriteOnce`.
   
## Deploying the Application
### Set Up Hazelcast - Helm Chart
Ensure you have Helm installed in your cluster (Helm 3 is installed as a tech preview by default in OCP 4.3) and the `helm` command line tool available locally. Add the Google public charts repository and download the charts:

```shell script
helm repo add stable https://kubernetes-charts.storage.googleapis.com/
helm repo update
```

Install the Hazelcast chart to create the cluster. You must override the default security settings in order for pod creation to succeed. You can additionally override other settings to configure the deployed cluster (see https://github.com/helm/charts/tree/master/stable/hazelcast#configuration):
```shell script
helm install hazelcast-server stable/hazelcast --set securityContext.runAsUser=null,securityContext.fsGroup=null
```

Ensure the management pod (if configured) and all server pods come up successfully. You should see a message in each server pod's logs indicating the server is up and has joined a cluster with the number of members you expect (3, in this case):

```
Members {size:3, ver:3} [
	Member [10.254.13.227]:5701 - 7c157b26-d91c-4e59-9f07-c366efec6a5e this
	Member [10.254.16.135]:5701 - 00984992-1fe0-43bd-9236-bbc86985e4f4
	Member [10.254.20.115]:5701 - b5ff9b62-6f65-4a0d-8d72-ee629156ff8b
]
```

### Create the Application Image
Build the application with `mvn package`. The application is currently built at `data/example/modresorts-1.0.war`. Copy that file to the `docker` directory.

Update the Liberty `server.xml` file with the necessary configuration:
```xml
<server>
    <featureManager>
        <feature>javaee-8.0</feature>
    </featureManager>

    <httpEndpoint httpPort="9080" httpsPort="9443"
                  id="defaultHttpEndpoint" host="*" />

</server>
```

The `sessionCache-1.0` feature and all related configuration will be added by the Liberty docker image during build, so you do not need to include any configuration for that here.

Examine the `Dockerfile`:
```Dockerfile
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
```
The Hazelcast client jars are copied from the official Hazelcast Docker image and placed in the expected file location within the application image. Note that the version of the drivers must match the version of the server, so if you have deployed a Hazelcast 3.x cluster, copy the client jars from the appropriate 3.x image.

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

## Appendix A: Required Role Bindings

The default user permissions on OpenShift prevent non-admin users from doing many of the operations required by this process. The following role bindings will likely need to be added to the user doing the deployment. Note that the open-liberty-operator-ol-liberty-ns role won't exist until the corresponding operator is installed in the project/namespace the user is working in:
* `admin` role for the namespace the user is working in
* `view` role for all namespaces where resources needed in this tutorial live, including the openshift-image-registry
* `open-liberty-operator-ol-liberty-ns` role for the namespace the user is working in (requires the Open Liberty operator be deployed to watch the namespace the user is working in)

In additon, the default serviceaccount permissions on OpenShift restrict the client's ability to discover the running servers. The following role bindings will need to be added to the serviceaccount the OpenLibertyApplication is deployed under. `namespace` refers to the namespace or project you deployed the OpenLibertyApplication to. Either allow the Open Liberty operator to create the service account and then bind this role to the account (this may require a redeployment to get the pods to pick up the new permissions) or create a serviceaccount with this role bound ahead of time and specify that account in the OpenLibertyApplication yaml file.
```yaml
kind: Role
apiVersion: rbac.authorization.k8s.io/v1
metadata:
  name: hazelcast-client-resorts
  namespace: resorts
rules:
  - verbs:
      - get
      - watch
      - list
    apiGroups:
      - ''
    resources:
      - pods
```