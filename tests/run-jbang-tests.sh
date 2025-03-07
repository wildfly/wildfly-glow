#!/bin/bash

test_failure=0
test_count=0
TEST_TIMEOUT=20

test_dir=tests/target/jbang/

glow_version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
# find the latest version of WildFLy that is supported by WildFly Glow
wildfly_latest_version=$(curl -s 'https://raw.githubusercontent.com/wildfly/wildfly-galleon-feature-packs/refs/heads/release/versions.yaml' | yq .latest)

function setupTestSuite {
  echo Clear JBang cache
  jbangExec cache clear
  mkdir -p $test_dir
}

function tearDownTestSuite {
  rm -rf $test_dir
}

function check_endpoint() {
  local url=$1

  response=$(curl -s -w "%{http_code}" "${url}")
  echo "Request responded with : ${response}" >&2
  http_code="${response: -3}"
  length=${#response}
  body="${response:0:length-3}"

  if [ "${http_code}" == "200" ]; then
    echo "${body}"
    return 0
  else
    echo "Request failed with status: ${http_code}" >&2
    return 1
  fi
}

function check_endpoint_response() {
  local url=$1
  local expected_body=$2

  if body=$(check_endpoint "${url}"); then
    if [ "${body}" == "${expected_body}" ]; then
      return 0
    else
      echo "Got: ${body} but expected: ${expected_body}" >&2
      return 1
    fi
  else
    return 1
  fi
}

function jbangExec {
  curl -Ls https://sh.jbang.dev | bash -s - "$@"
}

function jbangBuild {
  local java_source_file=$1
  echo "Building with source"
  cat "${java_source_file}"

  if ! jbangExec --verbose build "${java_source_file}"; then
    echo "Error building the application with JBang, check log"
    test_failure=1
    return 1
  fi
  return 0
}

function jbangRun {
  local java_source_file=$1
  local verifier=$2

  # Run the application and verify it works as expected
  jbangExec --verbose run "${java_source_file}" &

  failed=0
  elapsed=0
  sleep 5
  while ! eval "${verifier}"; do
    sleep 1
    ((elapsed++))

    if $elapsed -ge "$TIMEOUT"; then
      echo "Timeout reached: Process did not start responding on port 8080 within ${TEST_TIMEOUT} seconds."
      test_failure=1
      failed=1
      break
    fi
  done
  wildfly_pid=$(lsof -ti :8080)
  echo "Kill WildFly process (${wildfly_pid})"
  kill "${wildfly_pid}"
  return $failed
}

# Test a simple Jakarta RS application
# without any 3rd-party dependencies
# (all deps are provided by WildFly)
function testApp {
  test_count=$((test_count + 1))
  java_source_file=$test_dir/app.java
  cat <<-EOF >$java_source_file
///usr/bin/env jbang "\$0" "\$@" ; exit $?
//JAVA ${jdk_version}
//DEPS org.wildfly.bom:wildfly-expansion:${wildfly_latest_version}@pom
//DEPS org.wildfly.glow:wildfly-glow:${glow_version}
//DEPS jakarta.ws.rs:jakarta.ws.rs-api
//DEPS jakarta.enterprise:jakarta.enterprise.cdi-api
//GLOW --server-version=${wildfly_latest_version}

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/")
public class app extends Application {

    @Path("/hello")
    @ApplicationScoped
    public static class Hello {

        @GET
        public String sayHello() {
            return "Hello, WildFly!";
        }

    }
}
EOF
  jbangBuild $java_source_file
  jbangRun $java_source_file "check_endpoint_response 'http://localhost:8080/app/hello' 'Hello, WildFly!'"

  rm $java_source_file
}

# Test a simple Jakarta RS application
# with health enabled using //GLOW --add-ons=health
function testAppWithHealth {
  test_count=$((test_count + 1))
  java_source_file=$test_dir/appWithHealth.java
  cat <<-EOF >$java_source_file
///usr/bin/env jbang "\$0" "\$@" ; exit $?
//JAVA ${jdk_version}
//DEPS org.wildfly.bom:wildfly-expansion:${wildfly_latest_version}@pom
//DEPS org.wildfly.glow:wildfly-glow:${glow_version}
//DEPS jakarta.ws.rs:jakarta.ws.rs-api
//DEPS jakarta.enterprise:jakarta.enterprise.cdi-api
//GLOW --server-version=${wildfly_latest_version}
//GLOW --add-ons=health

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/")
public class appWithHealth extends Application {

    @Path("/hello")
    @ApplicationScoped
    public static class Hello {

        @GET
        public String sayHello() {
            return "Hello, WildFly!";
        }

    }
}
EOF
  jbangBuild $java_source_file
  jbangRun $java_source_file "check_endpoint 'http://localhost:9990/health'"

  rm $java_source_file
}

# Test a simple Jakarta RS application
# with metrics enabled using //GLOW --add-ons=metrics
function testAppWithMetrics {
  test_count=$((test_count + 1))
  java_source_file=$test_dir/appWithMetrics.java
  cat <<-EOF >$java_source_file
///usr/bin/env jbang "\$0" "\$@" ; exit $?
//JAVA ${jdk_version}
//DEPS org.wildfly.bom:wildfly-expansion:${wildfly_latest_version}@pom
//DEPS org.wildfly.glow:wildfly-glow:${glow_version}
//DEPS jakarta.ws.rs:jakarta.ws.rs-api
//DEPS jakarta.enterprise:jakarta.enterprise.cdi-api
//GLOW --server-version=${wildfly_latest_version}
//GLOW --add-ons=metrics

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("/")
public class appWithMetrics extends Application {

    @Path("/hello")
    @ApplicationScoped
    public static class Hello {

        @GET
        public String sayHello() {
            return "Hello, WildFly!";
        }

    }
}
EOF
  jbangBuild $java_source_file
  jbangRun $java_source_file "check_endpoint 'http://localhost:9990/metrics'"

  rm $java_source_file
}

# Test a simple MicroProfile Config application
function testMicroProfileConfigApp {
  test_count=$((test_count + 1))
  java_source_file=$test_dir/microProfileConfigApp.java
  cat <<-EOF >$java_source_file
///usr/bin/env jbang "\$0" "\$@" ; exit $?
//JAVA ${jdk_version}
//DEPS org.wildfly.bom:wildfly-expansion:${wildfly_latest_version}@pom
//DEPS org.wildfly.glow:wildfly-glow:${glow_version}
//DEPS jakarta.ws.rs:jakarta.ws.rs-api
//DEPS jakarta.enterprise:jakarta.enterprise.cdi-api
//DEPS org.eclipse.microprofile.config:microprofile-config-api
//GLOW --server-version=${wildfly_latest_version}

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationPath("/")
public class microProfileConfigApp extends Application {

    @Path("/hello")
    @ApplicationScoped
    public static class Hello {

        @Inject
        @ConfigProperty(name = "nick.name", defaultValue = "Alice")
        private String name;

        @GET
        public String sayHello() {
            return String.format("Hello, %s!", name);
        }

    }
}
EOF
  jbangBuild $java_source_file
  jbangRun $java_source_file "check_endpoint_response 'http://localhost:8080/microProfileConfigApp/hello' 'Hello, Alice!'"

  rm $java_source_file
}

# Test a simple app with a 3rd-party library that
# is not provided by WildFly
# Any deps that is versioned is included in the WAR deployment
function testAppWithLib {
  test_count=$((test_count + 1))
  java_source_file=$test_dir/appWithLib.java
  cat <<-EOF >$java_source_file
///usr/bin/env jbang "\$0" "\$@" ; exit $?
//JAVA ${jdk_version}
//DEPS org.wildfly.bom:wildfly-expansion:${wildfly_latest_version}@pom
//DEPS org.wildfly.glow:wildfly-glow:${glow_version}
//DEPS jakarta.ws.rs:jakarta.ws.rs-api
//DEPS jakarta.enterprise:jakarta.enterprise.cdi-api
//DEPS com.google.code.gson:gson:2.12.1
//GLOW --server-version=${wildfly_latest_version}

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import com.google.gson.*;

@ApplicationPath("/")
public class appWithLib extends Application {

    @Path("/hello")
    @ApplicationScoped
    public static class Hello {

        @GET
        @Produces(MediaType.TEXT_HTML)

        public String sayHello() throws Exception {
            Gson gson = new Gson();
            String[] values = { "a", "b", "c" };
            return gson.toJson(values);
        }

    }
}
EOF
  jbangBuild $java_source_file
  jbangRun $java_source_file "check_endpoint_response http://localhost:8080/appWithLib/hello '[\"a\",\"b\",\"c\"]'"

  rm $java_source_file
}

jdk_version=$1
if [ -z "${jdk_version}" ]; then
  jdk_version="17"
fi
echo "Running tests with JDK ${jdk_version}"

setupTestSuite

testApp
testAppWithLib
testAppWithHealth
testAppWithMetrics
testMicroProfileConfigApp

tearDownTestSuite

if "${test_failure}" -eq 1; then
  echo "There were test failures! See the above output for details."
  exit 1
else
  echo "All ${test_count} tests passed!"
fi
