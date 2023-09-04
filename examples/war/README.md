# layers-scanner war example

* To provision a WildFly server: 

java -jar ../../cli/target/wildfly-glow.jar kitchensink.war --output=server

* To provision a WildFly Bootable JAR:

java -jar ../../cli/target/wildfly-glow.jar kitchensink.war --output=bootable-jar

* To provision a WildFly server for the cloud and produce a Docker image: 

java -jar ../../cli/target/wildfly-glow.jar kitchensink.war --output=server --context=cloud