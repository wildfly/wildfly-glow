#!/bin/sh


### Redhat internal tests only.

# To run this script, create a file next to this one called .env, with the following
# environment variables set
#
# GLOW_QS_HOME= path to clone of https://github.com/wildfly/quickstart
# GLOW_S2I_HOME= path to clone of https://github.com/wildfly/wildfly-s2i/tree/main/examples
# GLOW_SAML_HOME= path to clone of https://github.com/jfdenise/wildfly-s2i/tree/saml-example/examples
# GLOW_FIRST_RESPONDER_HOME= path to clone of https://github.com/jfdenise/first-responder-demo/tree/ee10
# GLOW_COOLSTORE_HOME= path to clone of https://github.com/jfdenise/eap-coolstore-monolith/tree/ee10
# GLOW_QE_OPENSHIFT_TESTS_HOME= path to clone of https://gitlab.hosts.mwqe.eng.bos.redhat.com/jbossqe-eap/openshift-eap-tests/test-eap/src/test/resources/apps/
# GLOW_GRAPHQL_HOME= path to clone of https://github.com/jfdenise/wildfly-graphql-feature-pack/

set -e
script_dir=$(dirname "$0")
jar="${script_dir}/../cli/target/wildfly-glow.jar"
compact=-Dcompact=true


executionMode=$1

if [ -f "$script_dir/.env" ]; then
  source "$script_dir/.env"
fi

RED_CONSOLE="\033[0;31m"
GREEN_CONSOLE="\033[0;32m"
NOCOLOUR_CONSOLE="\033[0m"

test_failure=0
test_count=0

function test() {
  expected=$1
  warFile=$2
  profile=$3
  provisioningFile=$4
  addOns=$5
  context=$6
  preview=$7
  test_count=$((test_count+1))

if [ ! -z "$provisioningFile" ]; then
  if [ ! "default" == "$provisioningFile" ]; then
    provisioningFile="--feature-packs-file=$provisioningFile";
  else
    unset provisioningFile
  fi
fi
if [ ! -z "$profile" ]; then
  profile="--profiles=$profile";
fi
if [ ! -z "$addOns" ]; then
  addOns="--add-ons=$addOns";
fi
if [ ! -z "$context" ]; then
  context="--context=$context";
fi
if [ ! -z "$preview" ]; then
  preview="--preview";
fi
if [ ! -z $GENERATE_CONFIG ]; then
 echo "java -jar -Dverbose=true $jar $warFile ${provisioningFile} $profile $addOns $preview"
 java -jar -Dverbose=true $jar $warFile ${provisioningFile} $profile $addOns $preview
else

  if [ "$DEBUG" = 1 ]; then
    echo "java $compact  -jar $jar $warFile ${provisioningFile} $profile $addOns $context $preview"
  fi

  found_layers=$(java $compact  -jar $jar \
  $warFile \
  ${provisioningFile} \
  $profile \
  $addOns \
  $context \
  $preview)

  if [ "$found_layers" != "$expected" ]; then
    echo "${RED_CONSOLE}ERROR $warFile, found layers $found_layers; expected $expected${NOCOLOUR_CONSOLE}"
    test_failure=1
  fi
fi
}

function validateEnvVarSet() {
  if [ -z "$1" ]; then
    echo "$2 is not set. Point it to the root of the quickstarts directory. You can create a file called .env next to the run.sh script"
    exit 1
  fi
  if [ "${1:0-1}" != "/" ]; then
    echo "$1 should end with a '/'. Change the variable to $2=$1/"
    exit 1
  fi

}

validateEnvVarSet "$GLOW_QS_HOME" "GLOW_QS_HOME"
validateEnvVarSet "$GLOW_S2I_HOME" "GLOW_S2I_HOME"
validateEnvVarSet "$GLOW_SAML_HOME" "GLOW_SAML_HOME"
validateEnvVarSet "$GLOW_FIRST_RESPONDER_HOME" "GLOW_FIRST_RESPONDER_HOME"
validateEnvVarSet "$GLOW_COOLSTORE_HOME" "GLOW_COOLSTORE_HOME"
validateEnvVarSet "$GLOW_GRAPHQL_HOME" "GLOW_GRAPHQL_HOME"


echo "* Empty execution"

java -jar $jar

echo "* Display configuration"

java -jar $jar --display-configuration


#preview fp
echo preview
test \
"[cdi, ee-integration, grpc, jaxrs, jpa, transactions]==>ee-core-profile-server,grpc,jaxrs,jpa" \
"$GLOW_S2I_HOME/postgresql/target/postgresql-1.0.war" \
"" \
"" \
"grpc" \
cloud \
"true"

#graphql
echo graphql
test \
"[cdi, microprofile-graphql]==>ee-core-profile-server,microprofile-graphql" \
"$GLOW_GRAPHQL_HOME"quickstart/target/quickstart.war

# merge bmt and contacts-jquerymobile
echo merge bmt and contacts-jquerymobile
test \
"[bean-validation, cdi, ee-integration, ejb-lite, h2-driver, jaxrs, jpa, servlet, transactions]==>ee-core-profile-server,ejb-lite,h2-driver,jaxrs,jpa" \
"$GLOW_QS_HOME"contacts-jquerymobile/target/contacts-jquerymobile.war,"$GLOW_QS_HOME"bmt/target/bmt.war

# eap-coolstore-monolith and first-responder-demo
echo merge eap-coolstore-monolith and first-responder-demo
test \
"[bean-validation, cdi, datasources, ee-integration, ejb, ejb-lite, jaxrs, jpa, jsonb, jsonp, messaging-activemq, microprofile-config, microprofile-reactive-messaging, microprofile-reactive-messaging-kafka, naming, transactions, web-passivation]==>ee-core-profile-server,ejb,jaxrs,jpa,microprofile-reactive-messaging-kafka,web-passivation" \
"$GLOW_COOLSTORE_HOME/target/ROOT.war","$GLOW_FIRST_RESPONDER_HOME/backend/target/frdemo-backend.war"

# eap-coolstore-monolith and first-responder-demo cloud and ha
echo merge eap-coolstore-monolith and first-responder-demo cloud and ha
test \
"[ha][bean-validation, cdi, datasources, ee-integration, ejb, ejb-lite, jaxrs, jpa, jsonb, jsonp, messaging-activemq, microprofile-config, microprofile-reactive-messaging, microprofile-reactive-messaging-kafka, naming, transactions, web-passivation]==>ee-core-profile-server,ejb,ejb-dist-cache,jaxrs,jpa-distributed,microprofile-reactive-messaging-kafka,web-clustering,-ejb-local-cache" \
"$GLOW_FIRST_RESPONDER_HOME/backend/target/frdemo-backend.war","$GLOW_COOLSTORE_HOME/target/ROOT.war" \
"ha" \
"" \
"" \
cloud

echo "Hollow server, just lra-coordinator + metrics + health add-ons + default base layer"
echo lra-coordinator + metrics + health add-ons
test \
"[cdi, health, jaxrs, management, metrics, microprofile-config, microprofile-lra-coordinator, transactions]==>ee-core-profile-server,health,metrics,microprofile-lra-coordinator" \
"" \
"" \
"" \
lra-coordinator,metrics,health

#embedded broker
echo embedded broker hollow server
test \
"[cdi, ee, elytron, embedded-activemq, messaging-activemq, naming, remoting, undertow]==>ee-core-profile-server,embedded-activemq" \
"" \
"" \
"" \
embedded-activemq

echo "* Quickstarts not yet using new provisioning"
#batch
echo batch [Execution TESTED]
test \
"[batch-jberet, cdi, h2-driver, jpa, jsf]==>ee-core-profile-server,batch-jberet,h2-driver,jpa,jsf" \
"$GLOW_QS_HOME"batch-processing/target/batch-processing.war

#batch
echo batch with grpc addon
test \
"[batch-jberet, cdi, grpc, h2-driver, jpa, jsf]==>ee-core-profile-server,batch-jberet,grpc,h2-driver,jpa,jsf" \
"$GLOW_QS_HOME"batch-processing/target/batch-processing.war \
"" \
"" \
grpc

#batch
echo batch with myfaces addon
test \
"[batch-jberet, cdi, h2-driver, jpa, jsf, myfaces]==>ee-core-profile-server,batch-jberet,h2-driver,jpa,myfaces" \
"$GLOW_QS_HOME"batch-processing/target/batch-processing.war \
"" \
"" \
myfaces

#bean-validation-custom-constraint
echo bean-validation-custom-constraint
test \
"[bean-validation, jpa]==>ee-core-profile-server,jpa" \
"$GLOW_QS_HOME"bean-validation-custom-constraint/target/bean-validation-custom-constraint.war

#bmt
echo bmt
test \
"[cdi, ejb-lite, h2-driver, jpa, servlet, transactions]==>ee-core-profile-server,ejb-lite,h2-driver,jpa" \
"$GLOW_QS_HOME"bmt/target/bmt.war

#contacts-jquerymobile
echo contacts-jquerymobile
test \
"[bean-validation, cdi, ee-integration, ejb-lite, h2-driver, jaxrs, jpa, servlet]==>ee-core-profile-server,ejb-lite,h2-driver,jaxrs,jpa" \
"$GLOW_QS_HOME"contacts-jquerymobile/target/contacts-jquerymobile.war

#ejb-multi-server app-one
echo "ejb-multi-server (app-one)"
test \
"[ee-integration, ejb, ejb-lite]==>ee-core-profile-server,ejb" \
"$GLOW_QS_HOME"ejb-multi-server/app-one/ear/target/ejb-multi-server-app-one.ear

#ejb-multi-server app-two
echo "ejb-multi-server (app-two)"
test \
"[ee-integration, ejb, ejb-lite]==>ee-core-profile-server,ejb" \
"$GLOW_QS_HOME"ejb-multi-server/app-two/ear/target/ejb-multi-server-app-two.ear

#ejb-multi-server app-main
echo "ejb-multi-server (app-main)"
test \
"[cdi, ee-integration, ejb, ejb-lite, jsf, naming]==>ee-core-profile-server,ejb,jsf" \
"$GLOW_QS_HOME"ejb-multi-server/app-main/ear/target/ejb-multi-server-app-main.ear

#ejb-timer
echo ejb-timer [Execution TESTED]
test \
"[ha][ee-integration, ejb-lite]==>ee-core-profile-server,ejb-dist-cache,ejb-lite,-ejb-local-cache" \
"$GLOW_QS_HOME"ejb-timer/target/ejb-timer.war \
ha

#ejb-throws-exception
echo ejb-throws-exception [Execution TESTED]
test \
"[cdi, ejb-lite, jsf]==>ee-core-profile-server,ejb-lite,jsf" \
"$GLOW_QS_HOME"ejb-throws-exception/ear/target/ejb-throws-exception.ear

#ha-singleton-deployment
echo ha-singleton-deployment  [Execution TESTED]
test \
"[ee-integration, ejb-lite, singleton-local]==>ee-core-profile-server,ejb-lite,singleton-local" \
"$GLOW_QS_HOME"ha-singleton-deployment/target/ha-singleton-deployment.jar

#ha-singleton-deployment
echo ha-singleton-deployment ha profile  [Execution TESTED]
test \
"[ha][ee-integration, ejb-lite, singleton-local]==>ee-core-profile-server,ejb-dist-cache,ejb-lite,singleton-ha,-ejb-local-cache" \
"$GLOW_QS_HOME"ha-singleton-deployment/target/ha-singleton-deployment.jar \
ha

#ha-singleton-service
echo ha-singleton-service. NOT SUPPORTED.

#helloworld
echo helloworld  [Execution TESTED]
test \
"[cdi, servlet]==>ee-core-profile-server" \
"$GLOW_QS_HOME"helloworld/target/helloworld.war

#helloworld-jms
# Not in scope.

#helloworld-mutual-ssl
echo helloworld-mutual-ssl
test \
"[cdi, servlet]==>ee-core-profile-server" \
"$GLOW_QS_HOME"helloworld-mutual-ssl/target/helloworld-mutual-ssl.war

#helloworld-mutual-ssl-secured
echo helloworld-mutual-ssl-secured
test \
"[cdi, servlet]==>ee-core-profile-server" \
"$GLOW_QS_HOME"helloworld-mutual-ssl-secured/target/helloworld-mutual-ssl-secured.war

#helloworld-singleton
echo helloworld-singleton
test \
"[cdi, ejb-lite, jsf]==>ee-core-profile-server,ejb-lite,jsf" \
"$GLOW_QS_HOME"helloworld-singleton/target/helloworld-singleton.war

#hibernate
echo hibernate
test \
"[cdi, ee-integration, ejb-lite, h2-driver, jpa, jsf]==>ee-core-profile-server,ejb-lite,h2-driver,jpa,jsf" \
"$GLOW_QS_HOME"hibernate/target/hibernate.war

#http-custom-mechanism
echo "http-custom-mechanism"
test \
"[servlet]==>ee-core-profile-server" \
"$GLOW_QS_HOME"http-custom-mechanism/webapp/target/http-custom-mechanism-webapp.war

#jaxrs-jwt
echo jaxrs-jwt
test \
"[cdi, ejb-lite, jaxrs, jsonp]==>ee-core-profile-server,ejb-lite,jaxrs" \
"$GLOW_QS_HOME"jaxrs-jwt/service/target/jaxrs-jwt-service.war

#jaxws-retail
echo jaxws-retail
test \
"[ee-integration, ejb-lite, webservices]==>ee-core-profile-server,webservices" \
"$GLOW_QS_HOME"jaxws-retail/service/target/jaxws-retail-service.war

#jsonp
echo jsonp
test \
"[cdi, jsf, jsonp]==>ee-core-profile-server,jsf" \
"$GLOW_QS_HOME"jsonp/target/jsonp.war

#jta-crash-rec
echo jta-crash-rec [Execution TESTED]
test \
"[cdi, ee-integration, ejb, embedded-activemq, h2-driver, jpa, messaging-activemq, servlet, transactions]==>ee-core-profile-server,ejb,embedded-activemq,h2-driver,jpa" \
"$GLOW_QS_HOME"jta-crash-rec/target/jta-crash-rec.war

#jts
echo jts NOT SUPPORTED.

#logging
echo logging
test \
"[]==>ee-core-profile-server" \
"$GLOW_QS_HOME"logging/target/jboss-logging.war

#mail
echo mail
test \
"[cdi, ee-integration, jsf, mail]==>ee-core-profile-server,jsf,mail" \
"$GLOW_QS_HOME"mail/target/mail.war

#security-domain-to-domain
echo security-domain-to-domain ear
test \
"[cdi, ee-integration, ejb-lite, jpa, servlet]==>ee-core-profile-server,ejb-lite,jpa" \
"$GLOW_QS_HOME"security-domain-to-domain/ear/target/security-domain-to-domain.ear

#security-domain-to-domain
echo security-domain-to-domain ejb
test \
"[ee-integration, ejb-lite]==>ee-core-profile-server,ejb-lite" \
"$GLOW_QS_HOME"security-domain-to-domain/ejb/target/security-domain-to-domain-ejb.jar

#security-domain-to-domain
echo security-domain-to-domain web
test \
"[cdi, ejb-lite, jpa, servlet]==>ee-core-profile-server,ejb-lite,jpa" \
"$GLOW_QS_HOME"security-domain-to-domain/web/target/security-domain-to-domain-web.war

#servlet-async
echo servlet-async
test \
"[cdi, ejb-lite, servlet]==>ee-core-profile-server,ejb-lite" \
"$GLOW_QS_HOME"servlet-async/target/servlet-async.war

#servlet-filterlistener
echo servlet-filterlistener
test \
"[cdi, servlet]==>ee-core-profile-server" \
"$GLOW_QS_HOME"servlet-filterlistener/target/servlet-filterlistener.war

#shopping-cart
echo shopping-cart
test \
"[ejb, ejb-lite]==>ee-core-profile-server,ejb" \
"$GLOW_QS_HOME"shopping-cart/server/target/shopping-cart-server.jar

#spring-resteasy
echo spring-resteasy. NOT SUPPORTED

#tasks-jsf
echo tasks-jsf [Execution TESTED]
test \
"[cdi, ejb-lite, h2-driver, jpa, jsf]==>ee-core-profile-server,ejb-lite,h2-driver,jpa,jsf" \
"$GLOW_QS_HOME"tasks-jsf/target/tasks-jsf.war

#websocket-endpoint
echo websocket-endpoint  [Execution TESTED]
test \
"[cdi, ee-integration, jsonp, servlet]==>ee-core-profile-server" \
"$GLOW_QS_HOME"websocket-endpoint/target/websocket-endpoint.war

#wsat-simple
echo wsat-simple
test \
"[cdi, servlet, webservices]==>ee-core-profile-server,webservices" \
"$GLOW_QS_HOME"wsat-simple/target/wsat-simple.war

#wsba-coordinator-completion-simple
echo wsba-coordinator-completion-simple
test \
"[servlet, webservices]==>ee-core-profile-server,webservices" \
"$GLOW_QS_HOME"wsba-coordinator-completion-simple/target/wsba-coordinator-completion-simple.jar

#wsba-participant-completion-simple
echo wsba-participant-completion-simple
test \
"[servlet, webservices]==>ee-core-profile-server,webservices" \
"$GLOW_QS_HOME"wsba-participant-completion-simple/target/wsba-participant-completion-simple.jar


# WildFly S2I examples
echo "* WildFly S2I examples"

#elytron-oidc
echo elytron-oidc
test \
"[elytron-oidc-client, servlet]==>ee-core-profile-server,elytron-oidc-client" \
"$GLOW_S2I_HOME/elytron-oidc-client/target/simple-webapp.war"

#elytron-oidc
echo elytron-oidc auto-reg
test \
"[elytron-oidc-client, servlet]==>ee-core-profile-server,elytron-oidc-client" \
"$GLOW_S2I_HOME/elytron-oidc-client-auto-reg/target/simple-webapp.war"

#mdb-consumer
echo mdb-consumer
test \
"[cdi, ejb, messaging-activemq]==>ee-core-profile-server,ejb" \
"$GLOW_S2I_HOME/jms-broker/mdb-consumer/target/mdb-consumer.war"

#jms-producer
echo jms-producer
test \
"[cdi, ee-integration, messaging-activemq, servlet]==>ee-core-profile-server,messaging-activemq" \
"$GLOW_S2I_HOME/jms-broker/producer/target/jms-producer.war"

#jsf-ejb-jpa
echo jsf-ejb-jpa
test \
"[cdi, ee-integration, ejb-lite, h2-driver, jpa, jsf, servlet]==>ee-core-profile-server,ejb-lite,h2-driver,jpa,jsf" \
"$GLOW_S2I_HOME/jsf-ejb-jpa/target/jsf-ejb-jpa-demo-1.0.war"

#postgresql
echo postgresql
test \
"[cdi, datasources, ee-integration, jaxrs, jpa, postgresql-datasource, postgresql-driver, transactions]==>ee-core-profile-server,jaxrs,jpa,postgresql-datasource" \
"$GLOW_S2I_HOME/postgresql/target/postgresql-1.0.war" \
"" \
"" \
"postgresql" \

#web-clustering
echo web-clustering
test \
"[servlet, web-passivation]==>ee-core-profile-server,web-passivation" \
"$GLOW_S2I_HOME/web-clustering/target/web-clustering-demo-1.0.war"

#web-clustering HA
echo web-clustering HA
test \
"[ha][servlet, web-passivation]==>ee-core-profile-server,web-clustering" \
"$GLOW_S2I_HOME/web-clustering/target/web-clustering-demo-1.0.war" \
ha

#keycloak saml
echo keycloak saml
test \
"[keycloak-client-saml, keycloak-saml, servlet]==>ee-core-profile-server,keycloak-client-saml" \
"$GLOW_SAML_HOME/saml-auto-reg/target/saml-app.war"

# keycloak saml cloud auto-reg
echo keycloak saml cloud auto-reg
test \
"[keycloak-saml, servlet]==>ee-core-profile-server,keycloak-saml" \
"$GLOW_SAML_HOME/saml-auto-reg/target/saml-app.war" \
"" \
"" \
"" \
cloud

echo "* OCP big apps"

#first-responder-demo
echo first-responder-demo  [Execution TESTED]
test \
"[bean-validation, cdi, ee-integration, ejb-lite, jaxrs, jpa, jsonb, jsonp, microprofile-config, microprofile-reactive-messaging, microprofile-reactive-messaging-kafka, transactions]==>ee-core-profile-server,ejb-lite,jaxrs,jpa,microprofile-reactive-messaging-kafka" \
"$GLOW_FIRST_RESPONDER_HOME/backend/target/frdemo-backend.war"

#first-responder-demo ha profile
echo first-responder-demo ha
test \
"[ha][bean-validation, cdi, ee-integration, ejb-lite, jaxrs, jpa, jsonb, jsonp, microprofile-config, microprofile-reactive-messaging, microprofile-reactive-messaging-kafka, transactions]==>ee-core-profile-server,ejb-dist-cache,ejb-lite,jaxrs,jpa-distributed,microprofile-reactive-messaging-kafka,-ejb-local-cache" \
"$GLOW_FIRST_RESPONDER_HOME/backend/target/frdemo-backend.war"  ha

#first-responder-demo cloud
echo first-responder-demo cloud
test \
"[bean-validation, cdi, ee-integration, ejb-lite, jaxrs, jpa, jsonb, jsonp, microprofile-config, microprofile-reactive-messaging, microprofile-reactive-messaging-kafka, transactions]==>ee-core-profile-server,ejb-lite,jaxrs,jpa,microprofile-reactive-messaging-kafka" \
"$GLOW_FIRST_RESPONDER_HOME/backend/target/frdemo-backend.war" \
"" \
"" \
"" \
cloud

# eap-coolstore-monolith
echo eap-coolstore-monolith  [Execution TESTED]
test \
"[cdi, datasources, ee-integration, ejb, ejb-lite, jaxrs, jpa, jsonp, messaging-activemq, naming, web-passivation]==>ee-core-profile-server,ejb,jaxrs,jpa,web-passivation" \
"$GLOW_COOLSTORE_HOME/target/ROOT.war"

# eap-coolstore-monolith cloud
echo eap-coolstore-monolith cloud
test \
"[cdi, datasources, ee-integration, ejb, ejb-lite, jaxrs, jpa, jsonp, messaging-activemq, naming, web-passivation]==>ee-core-profile-server,ejb,jaxrs,jpa,web-passivation" \
"$GLOW_COOLSTORE_HOME/target/ROOT.war" \
"" \
"" \
"" \
cloud

echo "* Quickstarts Microprofile using Bootable JAR"

#microprofile-config
echo microprofile-config  [Execution TESTED]
test \
"[cdi, jaxrs, microprofile-config]==>ee-core-profile-server,jaxrs,microprofile-config" \
"$GLOW_QS_HOME"microprofile-config/target/microprofile-config.war

#microprofile-fault-tolerance
echo microprofile-fault-tolerance [Execution TESTED]
test \
"[cdi, jaxrs, microprofile-fault-tolerance]==>ee-core-profile-server,jaxrs,microprofile-fault-tolerance" \
"$GLOW_QS_HOME"microprofile-fault-tolerance/target/microprofile-fault-tolerance.war

#microprofile-health
echo microprofile-health
test \
"[cdi, jaxrs, microprofile-config, microprofile-health]==>ee-core-profile-server,jaxrs,microprofile-health" \
"$GLOW_QS_HOME"microprofile-health/target/microprofile-health.war

#microprofile-jwt
echo microprofile-jwt
test \
"[cdi, ee-integration, jaxrs, microprofile-jwt]==>ee-core-profile-server,jaxrs,microprofile-jwt" \
"$GLOW_QS_HOME"microprofile-jwt/target/microprofile-jwt.war

#microprofile-openapi
echo microprofile-openapi
test \
"[jaxrs]==>ee-core-profile-server,jaxrs" \
"$GLOW_QS_HOME"microprofile-openapi/target/microprofile-openapi.war

#microprofile-openapi with EE feature-pack, no microprofile-openapi suggestion
echo microprofile-openapi with EE feature-pack, no microprofile-openapi suggestion
test \
"[jaxrs]==>ee-core-profile-server,jaxrs" \
"$GLOW_QS_HOME"microprofile-openapi/target/microprofile-openapi.war \
" " \
tests/ee-provisioning.xml

#microprofile-reactive-messaging-kafka  [Execution TESTED]
echo microprofile-reactive-messaging-kafka
test \
"[cdi, ee-integration, h2-datasource, jaxrs, jpa, microprofile-reactive-messaging, microprofile-reactive-messaging-kafka, microprofile-reactive-streams-operators, transactions]==>ee-core-profile-server,h2-datasource,jaxrs,jpa,microprofile-reactive-messaging-kafka" \
"$GLOW_QS_HOME"microprofile-reactive-messaging-kafka/target/microprofile-reactive-messaging-kafka.war

#microprofile-rest-client server
echo microprofile-rest-client server
test \
"[jaxrs]==>ee-core-profile-server,jaxrs" \
"$GLOW_QS_HOME"microprofile-rest-client/country-server/target/country-server.war

#microprofile-rest-client client
echo microprofile-rest-client client
test \
"[cdi, jaxrs, microprofile-rest-client]==>ee-core-profile-server,jaxrs,microprofile-rest-client" \
"$GLOW_QS_HOME"microprofile-rest-client/country-client/target/country-client.war

# Quickstarts
echo "* Quickstarts using new provisioning"
#cmt
#particular is default h2 datasource. So a default datasource in some sort
echo cmt [Execution TESTED]
test \
"[cdi, ee-integration, ejb, ejb-lite, embedded-activemq, jpa, jsf, messaging-activemq, naming, transactions]==>ee-core-profile-server,ejb,embedded-activemq,jpa,jsf" \
"$GLOW_QS_HOME"cmt/target/cmt.war

#ee-security
echo ee-security [Execution TESTED]
test \
"[cdi, ee-security, servlet]==>ee-core-profile-server,ee-security" \
"$GLOW_QS_HOME"ee-security/target/ee-security.war

#helloworld
echo helloworld [Execution TESTED]
test \
"[cdi, servlet]==>ee-core-profile-server" \
"$GLOW_QS_HOME"helloworld/target/helloworld.war

#helloworld-mdb
echo helloworld-mdb [Execution TESTED]
test \
"[cdi, ee-integration, ejb, messaging-activemq, servlet]==>ee-core-profile-server,ejb" \
"$GLOW_QS_HOME"helloworld-mdb/target/helloworld-mdb.war

#helloworld-ws
echo "helloworld-ws"
test \
"[cdi, webservices]==>ee-core-profile-server,webservices" \
"$GLOW_QS_HOME"helloworld-ws/target/helloworld-ws.war

#jaxrs-client
echo jaxrs-client
test \
"[cdi, jaxrs]==>ee-core-profile-server,jaxrs" \
"$GLOW_QS_HOME"jaxrs-client/target/jaxrs-client.war

#kitchensink
echo kitchensink [Execution TESTED]
test \
"[bean-validation, cdi, ee-integration, ejb-lite, h2-driver, jaxrs, jpa, jsf]==>ee-core-profile-server,ejb-lite,h2-driver,jaxrs,jpa,jsf" \
"$GLOW_QS_HOME"kitchensink/target/kitchensink.war

#numberguess
echo numberguess
test \
"[cdi, ee-integration, jsf]==>ee-core-profile-server,jsf" \
"$GLOW_QS_HOME"numberguess/target/numberguess.war

#remote-helloworld-mdb
echo remote-helloworld-mdb [Execution TESTED]
test \
"[cdi, ee-integration, ejb, messaging-activemq, servlet]==>ee-core-profile-server,ejb" \
"$GLOW_QS_HOME"remote-helloworld-mdb/target/remote-helloworld-mdb.war

#servlet-security
echo servlet-security [Execution TESTED]
test \
"[cdi, jpa, servlet]==>ee-core-profile-server,jpa" \
"$GLOW_QS_HOME"servlet-security/target/servlet-security.war

#temperature-converter
echo temperature-converter
test \
"[cdi, ejb-lite, jsf]==>ee-core-profile-server,ejb-lite,jsf" \
"$GLOW_QS_HOME"temperature-converter/target/temperature-converter.war

#thread-racing
echo thread-racing [Execution TESTED]
test \
"[batch-jberet, cdi, ee-concurrency, ee-integration, ejb, ejb-lite, jaxrs, jpa, jsonp, messaging-activemq, naming, servlet]==>ee-core-profile-server,batch-jberet,ejb,jaxrs,jpa" \
"$GLOW_QS_HOME"thread-racing/target/thread-racing.war

#todo-backend
echo todo-backend [Execution TESTED]
test \
"[cdi, datasources, ejb-lite, jaxrs, jpa, postgresql-datasource, postgresql-driver, transactions]==>ee-core-profile-server,ejb-lite,jaxrs,jpa,postgresql-datasource" \
"$GLOW_QS_HOME"todo-backend/target/todo-backend.war \
standalone \
default \
postgresql

#todo-backend
echo todo-backend with default profile
test \
"[cdi, ejb-lite, jaxrs, jpa, transactions]==>ee-core-profile-server,ejb-lite,jaxrs,jpa" \
"$GLOW_QS_HOME"todo-backend/target/todo-backend.war

#todo-backend
echo todo-backend with ha profile and postgresql addOn
test \
"[ha][cdi, datasources, ejb-lite, jaxrs, jpa, postgresql-datasource, postgresql-driver, transactions]==>ee-core-profile-server,ejb-dist-cache,ejb-lite,jaxrs,jpa-distributed,postgresql-datasource,-ejb-local-cache" \
"$GLOW_QS_HOME"todo-backend/target/todo-backend.war \
ha \
default \
postgresql

#websocket-hello
echo websocket-hello
test \
"[servlet]==>ee-core-profile-server" \
"$GLOW_QS_HOME"websocket-hello/target/websocket-hello.war

if [ -n "$GLOW_QE_OPENSHIFT_TESTS_HOME" ]; then
# In order to build openshift qe tests:
# mvn clean package -Dserver-maven-plugin.groupId=org.wildfly.plugins \
# -Dserver-maven-plugin.artifactId=wildfly-maven-plugin \
# -Dserver-maven-plugin.version=4.1.0.Final \
# -Dserver-feature-pack.location=org.wildfly:wildfly-ee-galleon-pack:29.0.0.Alpha1-SNAPSHOT \
# -Dcloud-feature-pack.location=org.wildfly.cloud:wildfly-cloud-galleon-pack:3.0.1.Final-SNAPSHOT \
# -Ddatasources-feature-pack.location=org.wildfly:wildfly-datasources-galleon-pack:4.0.0.Final-SNAPSHOT \
# -Dserver-ee-bom.groupId=org.wildfly.bom -Dserver-ee-bom.artifactId=wildfly-ee \
# -Dserver-ee-bom.version=28.0.0.Final \
# -Dwildfly.package.skip=true \
# -Dboms.groupId=org.wildfly

  validateEnvVarSet "$GLOW_QE_OPENSHIFT_TESTS_HOME" "GLOW_QE_OPENSHIFT_TESTS_HOME"

  echo "* QE Openshift tests"

  #concurrency
  echo concurrency [Execution TESTED]
  test \
  "[ee-concurrency, ee-integration, servlet]==>ee-core-profile-server,ee-concurrency" \
  "$GLOW_QE_OPENSHIFT_TESTS_HOME"concurrency/target/ROOT.war

  #database-datasources-fp
  echo database-datasources-fp
  test \
  "[datasources, naming, servlet]==>ee-core-profile-server,datasources" \
  "$GLOW_QE_OPENSHIFT_TESTS_HOME"database-datasources-fp/target/ROOT.war

  #ejb-timer-expiration-store
  echo ejb-timer-expiration-store
  test \
  "[cdi, datasources, ejb, ejb-lite, jaxrs, jpa, postgresql-datasource, postgresql-driver]==>ee-core-profile-server,ejb,jaxrs,jpa,postgresql-datasource" \
  "$GLOW_QE_OPENSHIFT_TESTS_HOME"ejb-timer-expiration-store/target/ROOT.war\
  "" \
  "" \
  "postgresql" \

  #ejb-timers
  echo ejb-timers [Execution partly TESTED, no DB access]
  test \
  "[cdi, datasources, ee-integration, ejb-lite, jaxrs, naming]==>ee-core-profile-server,datasources,ejb-lite,jaxrs" \
  "$GLOW_QE_OPENSHIFT_TESTS_HOME"ejb-timers/target/ROOT.war

  #ha-2lc-jpa
  echo ha-2lc-jpa
  test \
  "[ha][cdi, datasources, jpa, postgresql-datasource, postgresql-driver, servlet, transactions, web-passivation]==>ee-core-profile-server,jpa-distributed,postgresql-datasource,web-clustering" \
  "$GLOW_QE_OPENSHIFT_TESTS_HOME"ha-2lc-jpa/target/ROOT.war \
  ha \
  "" \
  postgresql

  #ha-ejb-counter
  echo ha-ejb-counter
  test \
  "[ha][cdi, ee-integration, ejb-lite, servlet, web-passivation]==>ee-core-profile-server,ejb-dist-cache,ejb-lite,web-clustering,-ejb-local-cache" \
  "$GLOW_QE_OPENSHIFT_TESTS_HOME"ha-ejb-counter/target/ROOT.war \
  ha

  #opentelemetry
  echo opentelemetry
  test \
  "[cdi, jaxrs, microprofile-telemetry]==>ee-core-profile-server,jaxrs,microprofile-telemetry" \
  "$GLOW_QE_OPENSHIFT_TESTS_HOME"opentelemetry/target/opentelemetry.war

  #simple-jaxrs-health
  echo simple-jaxrs-health [Execution TESTED]
  test \
  "[cdi, jaxrs, microprofile-health]==>ee-core-profile-server,jaxrs,microprofile-health" \
  "$GLOW_QE_OPENSHIFT_TESTS_HOME"simple-jaxrs-health/target/ROOT.war

  #s2i (ear file)
  echo "s2i (ear file) [Execution TESTED]"
  test \
  "[cdi, ejb-lite, jsf, servlet]==>ee-core-profile-server,ejb-lite,jsf" \
  "$GLOW_QE_OPENSHIFT_TESTS_HOME"s2i/deployments/eap-s2i.ear

fi

if [ "$test_failure" -eq 1 ]; then
  echo "${RED_CONSOLE}There were test failures! See the above output for details.{$NOCOLOUR_CONSOLE}"
  exit 1
else
  echo "${GREEN_CONSOLE}All $test_count tests passed!"
fi
