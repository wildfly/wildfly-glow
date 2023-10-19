# WildFly Glow

WildFly Glow is a command line and a set of tools (maven plugin, core library) to provision a trimmed WildFly server instance that contains the server features that are required by your application.

WildFly Glow scans your deployment(s) and identify the set of [Galleon](https://github.com/wildfly/galleon) feature-packs and Galleon layers that are required by your application(s).

WildFly Glow command line can provision a WildFly server, a WildFly Bootable JAR or a Docker image by scanning your deployment.

# WildFly Glow overview

The [WildFly Galleon layers](https://docs.wildfly.org/29/Galleon_Guide.html#wildfly_galleon_layers) defined in the WildFly Galleon feature-packs have been annotated 
with some metadata allowing to bound the layer to the content of your deployment.
WildFly Glow scans the content of your deployment (Java API in use, XML descriptors, properties files) and attempt to match this content with the rules located 
inside the Galleon layers. If a rule matches, the Galeon layer is a candidate for inclusion.

WildFly feature-pack is not the only feature-pack containing annotated Galleon layers. The following galleon feature-packs are supported by Glow:

* [WildFly cloud](https://github.com/wildfly-extras/wildfly-cloud-galleon-pack) Galleon feature-pack.
* [WildFly datasources](https://github.com/wildfly-extras/wildfly-datasources-galleon-pack) Galleon feature-pack.
* [WildFly GRPC](https://github.com/wildfly-extras/wildfly-grpc-feature-pack) Galleon feature-pack.

N.B.: Some more Galleon feature-packs are in the process of being annotated to be supported by WildFly Glow.

To identify the Galleon layers your application requires, WildFly Glow retrieves Galleon feature-packs from [WildFly Galleon feature-packs](https://github.com/wildfly/wildfly-galleon-feature-packs/tree/release) repository. 
By default the latest WildFly version is used.

According to the Galleon layers required by your deployment, WildFly Glow can suggest a set of additional server features (called add-ons) that the tool 
allows you to enable. For example, when JAX-RS is required, the support for `openapi` is suggested and can be enabled.

WildFly Glow allows you to specify an execution context. `bare-metal` by default, `cloud` to deploy on Kubernetes and Openshift.

In addition, WildFly Glow allows you to specify an execution profile. Non HA (High Availability) by default and HA.

The WildFly Glow command line help contains the information on how to configure WildFly Glow 
to adjust the provisioned WildFly server (execution context, profile, add-ons, WildFly server version, ...).

# Using WildFly Glow

* Build the `wildfly-glow` maven project and use the WildFly Glow command line (`cli/target/wildfly-glow.jar` executable jar).
* Use the [WildFly Maven plugin](https://github.com/wildfly/wildfly-maven-plugin) starting version 5.0.0.Alpha1.

# Steps to build the WildFly Glow command line

1) Make sure to use JDK11 as the minimal version.
2) Build WildFly Glow: `mvn clean install`

# Kitchensink examples

* To display the required Galleon layers and feature-packs required to run kitchensink: 

`./wildfly-glow scan examples/war/kitchensink.war`

* To display the required Galleon layers and feature-packs required to run kitchensink on the cloud: 

`./wildfly-glow scan examples/war/kitchensink.war --cloud`

* To provision a WildFly server to run kitchensink: 

`./wildfly-glow scan examples/war/kitchensink.war --provision=server`

* To provision a WildFly Bootable JAR to run kitchensink:

`./wildfly-glow scan examples/war/kitchensink.war --provision=bootable-jar`

* To provision a WildFly server for the cloud and produce a Docker image to run kitchensink: 

`./wildfly-glow scan examples/war/kitchensink.war --provision=server --cloud`


# Accessing the WildFly Glow command line help

`./wildfly-glow --help` 

# WildFly Glow documentation

For now only help display by command line. Work on online documentation is in progress.

# Running CLI tests

1) Call `sh ./tests/run-cli-tests.sh`

# Steps to build the WildFly Glow command line to be used with SNAPSHOT versions of WildFly and SNAPSHOT versions of extra feature-packs

1) Make sure to use JDK11 as the minimal version.
2) Build [WildFly](https://github.com/wildfly/wildfly) main branch and have the built artifacts available in your local Maven cache.
3) Build keycloak: https://github.com/jfdenise/keycloak/tree/layers_metadata_final (`mvn clean install -DskipTests -Pdistribution` to build the required org.keycloak:keycloak-saml-adapter-galleon-pack artifact)
4) Build MyFaces feature-pack: https://github.com/jfdenise/wildfly-myfaces-feature-pack/tree/layers_metadata
5) Build graphql feature-pack: https://github.com/jfdenise/wildfly-graphql-feature-pack/tree/layers_metadata
6) Build WildFly Glow: `mvn clean install -Dwildfly.glow.galleon.feature-packs.url=https://raw.githubusercontent.com/wildfly/wildfly-galleon-feature-packs/main/`

# Running the additional tests (internal only)

1) Build WildFly Glow to use SNAPSHOT versions of WildFly and un-released feature-packs. 
2) Build WildFly quickstarts.
3) Clone https://github.com/jfdenise/wildfly-s2i/tree/saml-example and build examples/saml-auto-reg
4) Call `sh ./tests/run-internal.sh`
