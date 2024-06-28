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
package org.wildfly.glow.deployment.openshift.amq;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.fabric8.openshift.client.OpenShiftClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.wildfly.glow.Env;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.deployment.openshift.api.Deployer;
import org.wildfly.glow.deployment.openshift.api.OpenShiftSupport;
import org.wildfly.glow.deployment.openshift.api.Utils;

/**
 *
 * @author jdenise
 */
public class AMQDeployer implements Deployer {

    private static final String REMOTE_BROKER_NAME = "amq-broker";
    private static final String REMOTE_BROKER_PASSWORD = "admin";
    private static final String REMOTE_BROKER_USER = "admin";

    private static final Map<String, String> REMOTE_BROKER_CONNECTION_MAP = new HashMap<>();
    private static final Map<String, String> REMOTE_BROKER_APP_MAP = new HashMap<>();

    private static final String AMQ_USER_ENV = "AMQ_USER";
    private static final String AMQ_PASSWORD_ENV = "AMQ_PASSWORD";
    private static final String BROKER_AMQ_USERNAME_ENV = "BROKER_AMQ_USERNAME";
    private static final String BROKER_AMQ_PASSWORD_ENV = "BROKER_AMQ_PASSWORD";
    private static final String MQ_SERVICE_PREFIX_MAPPING_ENV = "MQ_SERVICE_PREFIX_MAPPING";
    private static final String TEMPLATE_PASSWORD_ENV = "{PREFIX}_PASSWORD";
    private static final String TEMPLATE_USERNAME_ENV = "{PREFIX}_USERNAME";
    private static final String TEMPLATE_SERVICE_HOST="{SERVICE-NAME}_AMQ7_TCP_SERVICE_HOST";
    private static final String TEMPLATE_SERVICE_PORT="{SERVICE-NAME}_AMQ7_TCP_SERVICE_PORT";

    private static final Set<String> RESOLVED_ENVS = new HashSet<>();
    static {

        RESOLVED_ENVS.add(MQ_SERVICE_PREFIX_MAPPING_ENV);
        RESOLVED_ENVS.add(TEMPLATE_PASSWORD_ENV);
        RESOLVED_ENVS.add(TEMPLATE_USERNAME_ENV);
        RESOLVED_ENVS.add(TEMPLATE_SERVICE_HOST);
        RESOLVED_ENVS.add(TEMPLATE_SERVICE_PORT);

        REMOTE_BROKER_CONNECTION_MAP.put(AMQ_USER_ENV, REMOTE_BROKER_USER);
        REMOTE_BROKER_CONNECTION_MAP.put(AMQ_PASSWORD_ENV, REMOTE_BROKER_PASSWORD);
        REMOTE_BROKER_CONNECTION_MAP.put("AMQ_DATA_DIR", "/home/jboss/data");

        REMOTE_BROKER_APP_MAP.put(MQ_SERVICE_PREFIX_MAPPING_ENV, "broker-amq7=BROKER_AMQ");
        REMOTE_BROKER_APP_MAP.put("BROKER_AMQ_TCP_SERVICE_HOST", REMOTE_BROKER_NAME);
        REMOTE_BROKER_APP_MAP.put("BROKER_AMQ_TCP_SERVICE_PORT", "61616");
        REMOTE_BROKER_APP_MAP.put(BROKER_AMQ_PASSWORD_ENV, REMOTE_BROKER_PASSWORD);
        REMOTE_BROKER_APP_MAP.put(BROKER_AMQ_USERNAME_ENV, REMOTE_BROKER_USER);
    }

    @Override
    public Map<String, String> deploy(GlowMessageWriter writer, Path target, OpenShiftClient osClient,
            Map<String, String> env, String appHost, String appName, String matching, Map<String, String> extraEnv, boolean dryRun) throws Exception {
        writer.info("Deploying AMQ Messaging Broker");
        Map<String, String> labels = new HashMap<>();
        labels.put(LABEL, REMOTE_BROKER_NAME);
        ContainerPort port = new ContainerPort();
        port.setContainerPort(61616);
        port.setProtocol("TCP");
        List<ContainerPort> ports = new ArrayList<>();
        ports.add(port);
        List<EnvVar> vars = new ArrayList<>();
        for (Map.Entry<String, String> entry : REMOTE_BROKER_CONNECTION_MAP.entrySet()) {
            // In case user overrides the default values.
            String val = extraEnv.get(entry.getKey());
            if (val != null) {
                if (AMQ_USER_ENV.equals(entry.getKey())) {
                    REMOTE_BROKER_APP_MAP.put(BROKER_AMQ_USERNAME_ENV, val);
                } else if (AMQ_PASSWORD_ENV.equals(entry.getKey())) {
                    REMOTE_BROKER_APP_MAP.put(BROKER_AMQ_PASSWORD_ENV, val);
                }
            }
            vars.add(new EnvVar().toBuilder().withName(entry.getKey()).withValue(val == null ? entry.getValue() : val).build());
        }
        Container container = new Container();
        container.setName(REMOTE_BROKER_NAME);
        container.setImage("registry.redhat.io/amq7/amq-broker");
        container.setPorts(ports);
        container.setEnv(vars);
        container.setImagePullPolicy("IfNotPresent");

        Deployment deployment = new DeploymentBuilder().withNewMetadata().withName(REMOTE_BROKER_NAME).endMetadata().
                withNewSpec().withReplicas(1).
                withNewSelector().withMatchLabels(labels).endSelector().
                withNewTemplate().withNewMetadata().withLabels(labels).endMetadata().withNewSpec().
                withContainers(container).withRestartPolicy("Always").
                endSpec().endTemplate().withNewStrategy().withType("RollingUpdate").endStrategy().endSpec().build();
        if (!dryRun) {
            osClient.resources(Deployment.class).resource(deployment).createOr(NonDeletingOperation::update);
        }
        Utils.persistResource(OpenShiftSupport.getDeployersDirectory(target), deployment, REMOTE_BROKER_NAME + "-deployment.yaml");
        IntOrString v = new IntOrString();
        v.setValue(61616);
        Service service = new ServiceBuilder().withNewMetadata().withName(REMOTE_BROKER_NAME).endMetadata().
                withNewSpec().withPorts(new ServicePort().toBuilder().withName("61616-tcp").withProtocol("TCP").
                        withPort(61616).
                        withTargetPort(v).build()).withType("ClusterIP").withSessionAffinity("None").withSelector(labels).endSpec().build();
        if (!dryRun) {
            osClient.services().resource(service).createOr(NonDeletingOperation::update);
        }
        Utils.persistResource(OpenShiftSupport.getDeployersDirectory(target), service, REMOTE_BROKER_NAME + "-service.yaml");
        if (dryRun) {
            writer.info("Resources for AMQ Messaging Broker have been generated");
        } else {
            writer.info("AMQ Messaging Broker has been deployed");
        }
        return REMOTE_BROKER_APP_MAP;
    }

    @Override
    public Map<String, String> disabledDeploy(String appHost, String appName, String matching, Map<String, String> env) {
        Map<String, String> ret = new HashMap<>();
        String descriptionPrefix = " Replace the PREFIX with the prefix used in the MQ_SERVICE_PREFIX_MAPPING env variable";
        String descriptionServiceName = " Replace the SERVICE-NAME with the service name used in the MQ_SERVICE_PREFIX_MAPPING env variable";
        for (Map.Entry<String, String> entry : env.entrySet()) {
            if (entry.getKey().startsWith("{PREFIX}")) {
                String k = entry.getKey().replace("{PREFIX}", "PREFIX");
                ret.put(k, entry.getValue() + descriptionPrefix);
            } else {
                if (entry.getKey().startsWith("{SERVICE-NAME}")) {
                    String k = entry.getKey().replace("{SERVICE-NAME}", "SERVICE-NAME");
                    ret.put(k, entry.getValue() + descriptionServiceName);
                } else {
                    if (entry.getKey().startsWith("MQ_SERVICE_PREFIX_MAPPING")) {
                        ret.put(entry.getKey(), entry.getValue());
                    }

                }
            }
        }
        return ret;
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
        ret.add("cloud-remote-activemq");
        return ret;
    }

    @Override
    public String getName() {
        return REMOTE_BROKER_NAME;
    }

}
