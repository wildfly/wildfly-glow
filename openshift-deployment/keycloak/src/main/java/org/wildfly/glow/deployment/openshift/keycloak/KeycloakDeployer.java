/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.glow.deployment.openshift.keycloak;

import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.client.OpenShiftClient;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.wildfly.glow.Env;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.deployment.openshift.api.Deployer;
import org.wildfly.glow.deployment.openshift.api.Utils;

/**
 *
 * @author jdenise
 */
public class KeycloakDeployer implements Deployer {

    private static final String KEYCLOAK_TEMPLATE_URL = "https://raw.githubusercontent.com/keycloak/keycloak-quickstarts/23.0.7/openshift/keycloak.yaml";
    private static final String KEYCLOAK_NAME = "keycloak";
    private static final String WILDFLY_REALM_PATH = "/realms/WildFly";
    private static final String KEYCLOAK_ADMIN = "admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = "admin";
    private static final String KEYCLOAK_DEMO = "demo";
    private static final String KEYCLOAK_DEMO_PASSWORD = "demo";
    private static final String KEYCLOAK_ADMIN_ENV = "KEYCLOAK_ADMIN";
    private static final String KEYCLOAK_ADMIN_PASSWORD_ENV = "KEYCLOAK_ADMIN_PASSWORD";
    private static final String OIDC_PROVIDER_URL_ENV = "OIDC_PROVIDER_URL";
    private static final String OIDC_PROVIDER_NAME_ENV = "OIDC_PROVIDER_NAME";
    private static final String OIDC_SECURE_DEPLOYMENT_SECRET_ENV = "OIDC_SECURE_DEPLOYMENT_SECRET";
    private static final String OIDC_USER_NAME_ENV = "OIDC_USER_NAME";
    private static final String OIDC_USER_PASSWORD_ENV = "OIDC_USER_PASSWORD";
    private static final String OIDC_HOSTNAME_HTTPS_ENV = "OIDC_HOSTNAME_HTTPS";
    private static final String MYSECRET = "mysecret";
    private static final String NAMESPACE_ENV = "NAMESPACE";

    private static final Set<String> RESOLVED_ENVS = new HashSet<>();
    static {
        RESOLVED_ENVS.add(OIDC_PROVIDER_URL_ENV);
        RESOLVED_ENVS.add(OIDC_SECURE_DEPLOYMENT_SECRET_ENV);
        RESOLVED_ENVS.add(OIDC_USER_NAME_ENV);
        RESOLVED_ENVS.add(OIDC_USER_PASSWORD_ENV);
        RESOLVED_ENVS.add(OIDC_PROVIDER_NAME_ENV);
        RESOLVED_ENVS.add(OIDC_HOSTNAME_HTTPS_ENV);
    }
    @Override
    public Map<String, String> disabledDeploy(String appHost, String appName, String matching, Map<String, String> env) {
        Map<String, String> ret = new HashMap<>();
        ret.put(OIDC_HOSTNAME_HTTPS_ENV, appHost);
        ret.putAll(getExistingEnv(env));
        return ret;
    }

    private Map<String, String> getExistingEnv(Map<String, String> env) {
        Map<String, String> ret = new HashMap<>();
        for(Map.Entry<String, String> entry : env.entrySet()) {
            if(entry.getKey().startsWith("OIDC_")) {
                ret.put(entry.getKey(), entry.getValue());
            }
        }
        return ret;
    }

    @Override
    public Map<String, String> deploy(GlowMessageWriter writer, Path target, OpenShiftClient osClient, Map<String, String> env,
            String appHost, String appName, String matching, Map<String, String> extraEnv) throws Exception {
        writer.info("Deploying Keycloak server");
        Map<String, String> parameters = new HashMap<>();
        String adminVal = extraEnv.get(KEYCLOAK_ADMIN_ENV);
        parameters.put(KEYCLOAK_ADMIN_ENV, adminVal == null ? KEYCLOAK_ADMIN : adminVal);
        String adminPassword = extraEnv.get(KEYCLOAK_ADMIN_PASSWORD_ENV);
        parameters.put(KEYCLOAK_ADMIN_PASSWORD_ENV, adminPassword == null ? KEYCLOAK_ADMIN_PASSWORD : adminPassword);
        parameters.put(NAMESPACE_ENV, osClient.getNamespace());
        Template t = osClient.templates().
                load(new URL(KEYCLOAK_TEMPLATE_URL)).createOr(NonDeletingOperation::update);
        final KubernetesList processedTemplateWithCustomParameters = osClient.templates().
                withName(KEYCLOAK_NAME)
                .process(parameters);
        osClient.resourceList(processedTemplateWithCustomParameters).createOrReplace();
        Utils.persistResource(target, processedTemplateWithCustomParameters, KEYCLOAK_NAME + "-resources.yaml");
        writer.info("Waiting until keycloak is ready ...");
        DeploymentConfig dc = new DeploymentConfigBuilder().withNewMetadata().withName(KEYCLOAK_NAME).endMetadata().build();
        osClient.resources(DeploymentConfig.class).resource(dc).waitUntilReady(5, TimeUnit.MINUTES);

        Route route = new RouteBuilder().withNewMetadata().withName(KEYCLOAK_NAME).
                endMetadata().build();
        String host = osClient.routes().resource(route).get().getSpec().getHost();
        String url = "https://" + host;
        writer.info("Keycloak route: " + url);
        Map<String, String> retEnv = new HashMap<>();
        String realmUrl = url + WILDFLY_REALM_PATH;
        writer.warn("NOTE: Some actions must be taken from the keycloack console.");
        writer.warn("1- Use admin/admin to log to the console " + url);
        writer.warn("2- Create a realm named WildFly");
        writer.warn("3- Create a user named demo, password demo");
        writer.warn("4- Create a role needed by your application and assign it to the demo user");
        if (env.containsKey(OIDC_PROVIDER_URL_ENV)) {
            writer.warn("5- Assign the role 'realm-management create-client' to the demo user");
            writer.warn("NOTE: In case your application is deployed prior you completed the keycloak admin tasks, make sure to re-deploy your application.");
        } else {
            writer.warn("5 - Create an OIDC Client named the way your OIDC configuration expects it. "
                    + "Set its Root URL to  'https://" + appHost + ("ROOT.war".equals(appName) ? "" : "/" + appName) + "'");
        }
        retEnv.put(OIDC_PROVIDER_URL_ENV, realmUrl);
        if (env.containsKey(OIDC_PROVIDER_URL_ENV)) {
            retEnv.put(OIDC_PROVIDER_NAME_ENV, KEYCLOAK_NAME);
            retEnv.put(OIDC_SECURE_DEPLOYMENT_SECRET_ENV, MYSECRET);
            retEnv.put(OIDC_USER_NAME_ENV, KEYCLOAK_DEMO);
            retEnv.put(OIDC_USER_PASSWORD_ENV, KEYCLOAK_DEMO_PASSWORD);
            retEnv.put(OIDC_HOSTNAME_HTTPS_ENV, appHost);
        }
        writer.info("Keycloak server has been deployed");
        return retEnv;
    }

    @Override
    public Set<Env> getResolvedEnvs(Set<Env> input) {
        Set<Env> envs = new HashSet<>();
        for (Env env : input) {
            if (RESOLVED_ENVS.contains(env.getName())) {
                envs.add(env);
            }
        }
        return envs;
    }

    @Override
    public Set<String> getSupportedLayers() {
        Set<String> ret = new HashSet<>();
        ret.add("elytron-oidc-client");
        return ret;
    }

    @Override
    public String getName() {
        return KEYCLOAK_NAME;
    }

}
