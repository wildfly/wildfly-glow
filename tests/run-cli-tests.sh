#!/bin/bash

script_dir=$(dirname "$0")
jar="${script_dir}/../cli/target/wildfly-glow.jar"
compact=-Dcompact=true

executionMode=$1

test_failure=0
test_count=0

function test {
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
    provisioningFile="--input-feature-packs-file=$provisioningFile";
  else
    unset provisioningFile
  fi
fi
if [ ! -z "$profile" ]; then
  profile="--$profile";
fi
if [ ! -z "$addOns" ]; then
  addOns="--add-ons=$addOns";
fi
if [ ! -z "$context" ]; then
  context="--$context";
fi
if [ ! -z "$preview" ]; then
  preview="--wildfly-preview";
fi
if [ ! -z $GENERATE_CONFIG ]; then
 echo "java -jar -Dverbose=true $jar scan $warFile ${provisioningFile} $profile $addOns $preview"
 java -jar -Dverbose=true $jar scan $warFile ${provisioningFile} $profile $addOns $preview
else

  if [ "$DEBUG" = 1 ]; then
    echo "java $compact  -jar $jar scan $warFile ${provisioningFile} $profile $addOns $context $preview"
  fi

  found_layers=$(java $compact  -jar $jar scan \
  $warFile \
  ${provisioningFile} \
  $profile \
  $addOns \
  $context \
  $preview)

  if [ "$found_layers" != "$expected" ]; then
    echo "ERROR $warFile, found layers $found_layers; expected $expected"
    test_failure=1
  fi
fi
}

echo "* Empty execution"

java -jar $jar

if [ $? -ne 0 ]; then
    echo "Error, check log"
    exit 1
fi

echo "* Show configuration"

java -jar $jar show-configuration

if [ $? -ne 0 ]; then
    echo "Error, check log"
    exit 1
fi

echo "* Show configuration cloud"

java -jar $jar show-configuration --cloud

if [ $? -ne 0 ]; then
    echo "Error, check log"
    exit 1
fi

echo kitchensink
test \
"[bean-validation, cdi, ee-integration, ejb-lite, h2-driver, jaxrs, jpa, jsf]==>ee-core-profile-server,ejb-lite,h2-driver,jaxrs,jpa,jsf" \
"examples/war/kitchensink.war"

echo kitchensink cloud
test \
"[bean-validation, cdi, ee-integration, ejb-lite, h2-driver, jaxrs, jpa, jsf]==>ee-core-profile-server,ejb-lite,h2-driver,jaxrs,jpa,jsf" \
"examples/war/kitchensink.war" \
"" \
"" \
"" \
cloud

echo kitchensink cloud HA
test \
"[ha][bean-validation, cdi, ee-integration, ejb-lite, h2-driver, jaxrs, jpa, jsf]==>ee-core-profile-server,ejb-dist-cache,ejb-lite,h2-driver,jaxrs,jpa-distributed,jsf,-ejb-local-cache" \
"examples/war/kitchensink.war" \
"ha" \
"" \
"" \
cloud

if [ "$test_failure" -eq 1 ]; then
  echo "There were test failures! See the above output for details."
  exit 1
else
  echo "All $test_count tests passed!"
fi