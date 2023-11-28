# WildFly Glow

WildFly Glow is a command line and a set of tools (maven plugin, core library) to provision a trimmed WildFly server instance that contains the server features that are required by your application.

WildFly Glow scans your deployment(s) and identify the set of [Galleon](https://github.com/wildfly/galleon) Feature-packs and Galleon Layers that are required by your application(s).

WildFly Glow command line can provision a WildFly server, a WildFly Bootable JAR or a Docker image.

# WildFly Glow overview

The [WildFly Galleon Layers](https://docs.wildfly.org/29/Galleon_Guide.html#wildfly_galleon_layers) defined in the WildFly Galleon Feature-packs have been annotated 
with some metadata allowing to bound the Galleon Layer to the content of your deployment.
WildFly Glow scans the content of your deployment (Java API in use, XML descriptors, properties files, ...) and attempt to match this content with the rules located 
inside the Galleon Layers. If a rule matches, the Galeon Layer is a candidate for inclusion.

WildFly Galleon Feature-pack is not the only Feature-pack containing annotated Galleon Layers. The following Galleon Feature-packs are supported by Glow:

* [WildFly cloud](https://github.com/wildfly-extras/wildfly-cloud-galleon-pack) Galleon Feature-pack.
* [WildFly datasources](https://github.com/wildfly-extras/wildfly-datasources-galleon-pack) Galleon Feature-pack.
* [WildFly GRPC](https://github.com/wildfly-extras/wildfly-grpc-feature-pack) Galleon Feature-pack.

N.B.: Some more Galleon Feature-packs are in the process of being annotated to be supported by WildFly Glow.

To identify the Galleon Layers your application requires, WildFly Glow retrieves Galleon Feature-packs from [WildFly Galleon Feature-packs](https://github.com/wildfly/wildfly-galleon-feature-packs/tree/release) repository. 
By default the latest WildFly version is used.

According to the Galleon Layers required by your deployment, WildFly Glow can suggest a set of additional server features (called add-ons) that the tool 
allows you to enable. For example, when JAX-RS is required, the support for `openapi` is suggested and can be enabled.

WildFly Glow allows you to specify an execution context. `bare-metal` by default, `cloud` to deploy on Kubernetes and Openshift.

In addition, WildFly Glow allows you to specify an execution profile. Non HA (High Availability) by default and HA.

The WildFly Glow command line help contains the information on how to configure WildFly Glow 
to adjust the provisioned WildFly server (execution context, profile, add-ons, WildFly server version, ...).

# Using the WildFly Glow CLI

* Download the latest WildFly Glow CLI zip file from github [releases](https://github.com/wildfly/wildfly-glow/releases)
* Unzip the file `wildfly-glow-<version>.zip`
* cd `wildfly-glow-<version>`
* To display the CLI help call: `./wildfly-glow`
* To install the automatic CLI commands completion call: `source <(./wildfly-glow completion)`
* To scan a first deployment call: `./wildfly-glow scan ./examples/kitchensink.war`

# WildFly Glow integration in the WildFly Maven Plugin

* Starting version 5.0.0.Alpha2, the [WildFly Maven plugin](https://github.com/wildfly/wildfly-maven-plugin) allows to discover Galleon Feature-packs and Layers.
Include the `<discover-provisioning-info/>` element in the plugin configuration. For exising plugin configuration, replace the `<feature-packs>` and `<layers>` elements with the `<discover-provisioning-info/>` element. 

# WildFly Glow documentation

WildFly Glow [documentation](http://docs.wildfly.org/wildfly-glow/).

# Steps to build the WildFly Glow command line

1) Make sure to use JDK11 as the minimal version.
2) Build WildFly Glow: `mvn clean install`

# Running CLI tests

1) Call `sh ./tests/run-cli-tests.sh`

# Steps to build the WildFly Glow command line to be used with SNAPSHOT versions of WildFly and SNAPSHOT versions of extra Galleon Feature-packs

1) Make sure to use JDK11 as the minimal version.
2) Build [WildFly](https://github.com/wildfly/wildfly) main branch and have the built artifacts available in your local Maven cache.
3) Build keycloak: https://github.com/jfdenise/keycloak/tree/layers_metadata_final (`mvn clean install -DskipTests -Pdistribution` to build the required org.keycloak:keycloak-saml-adapter-galleon-pack artifact)
4) Build MyFaces Feature-pack: https://github.com/jfdenise/wildfly-myfaces-feature-pack/tree/layers_metadata
5) Build graphql Feature-pack: https://github.com/jfdenise/wildfly-graphql-feature-pack/tree/layers_metadata
6) Build WildFly Glow: `mvn clean install -Dwildfly.glow.galleon.feature-packs.url=https://raw.githubusercontent.com/wildfly/wildfly-galleon-feature-packs/main/`

# Running the additional tests (internal only)

1) Build WildFly Glow to use SNAPSHOT versions of WildFly and un-released Feature-packs. 
2) Build WildFly quickstarts.
3) Clone https://github.com/jfdenise/wildfly-s2i/tree/saml-example and build examples/saml-auto-reg
4) Call `sh ./tests/run-internal.sh`
