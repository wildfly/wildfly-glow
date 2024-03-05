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
package org.wildfly.glow.deployment.openshift.postgresql;

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
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.openshift.client.OpenShiftClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.deployment.openshift.api.Deployer;

/**
 *
 * @author jdenise
 */
public class PostgreSQLDeployer implements Deployer {

    private static final String POSTGRESQL_NAME = "postgresql";
    private static final String POSTGRESQL_SAMPLEDB = "sampledb";
    private static final String POSTGRESQL_PASSWORD = "admin";
    private static final String POSTGRESQL_USER = "admin";
    private static final String POSTGRESQL_SERVICE_PORT = "5432";
    private static final String POSTGRESQL_SERVICE_HOST = POSTGRESQL_NAME;

    private static final String POSTGRESQL_SERVICE_PORT_ENV = "POSTGRESQL_SERVICE_PORT";
    private static final String POSTGRESQL_SERVICE_HOST_ENV = "POSTGRESQL_SERVICE_HOST";
    private static final Map<String, String> POSTGRESQL_CONNECTION_MAP = new HashMap<>();
    private static final Map<String, String> POSTGRESQL_APP_MAP = new HashMap<>();

    static {
        POSTGRESQL_CONNECTION_MAP.put("POSTGRESQL_DATABASE", POSTGRESQL_SAMPLEDB);
        POSTGRESQL_CONNECTION_MAP.put("POSTGRESQL_PASSWORD", POSTGRESQL_PASSWORD);
        POSTGRESQL_CONNECTION_MAP.put("POSTGRESQL_USER", POSTGRESQL_USER);
        POSTGRESQL_APP_MAP.putAll(POSTGRESQL_CONNECTION_MAP);
        POSTGRESQL_APP_MAP.put(POSTGRESQL_SERVICE_PORT_ENV, POSTGRESQL_SERVICE_PORT);
        POSTGRESQL_APP_MAP.put(POSTGRESQL_SERVICE_HOST_ENV, POSTGRESQL_SERVICE_HOST);
    }

    @Override
    public Map<String, String> disabledDeploy(String appHost, String appName, String matching, Map<String, String> env) {
        Map<String, String> ret = new HashMap<>();
        ret.put(POSTGRESQL_SERVICE_HOST_ENV, "PostgreSQL server host name.");
        ret.put(POSTGRESQL_SERVICE_PORT_ENV, "PostgreSQL server port.");
        ret.putAll(getExistingEnv(env));
        return ret;
    }

    private Map<String, String> getExistingEnv(Map<String, String> env) {
        Map<String, String> ret = new HashMap<>();
        for(Entry<String, String> entry : env.entrySet()) {
            if(entry.getKey().startsWith("POSTGRESQL_")) {
                ret.put(entry.getKey(), entry.getValue());
            }
        }
        return ret;
    }

    @Override
    public Map<String, String> deploy(GlowMessageWriter writer, Path target, OpenShiftClient osClient,
            Map<String, String> env, String appHost, String appName, String matching) throws Exception {
        writer.info("\nDeploying PosgreSQL server");
        Map<String, String> labels = new HashMap<>();
        labels.put(LABEL, POSTGRESQL_NAME);
        ContainerPort port = new ContainerPort();
        port.setContainerPort(5432);
        port.setProtocol("TCP");
        List<ContainerPort> ports = new ArrayList<>();
        ports.add(port);
        List<EnvVar> vars = new ArrayList<>();
        for (Map.Entry<String, String> entry : POSTGRESQL_CONNECTION_MAP.entrySet()) {
            vars.add(new EnvVar().toBuilder().withName(entry.getKey()).withValue(entry.getValue()).build());
        }
        Container container = new Container();
        container.setName(POSTGRESQL_NAME);
        container.setImage("registry.redhat.io/rhel8/postgresql-15");
        container.setPorts(ports);
        container.setEnv(vars);
        container.setImagePullPolicy("IfNotPresent");

        Deployment deployment = new DeploymentBuilder().withNewMetadata().withName(POSTGRESQL_NAME).endMetadata().
                withNewSpec().withReplicas(1).
                withNewSelector().withMatchLabels(labels).endSelector().
                withNewTemplate().withNewMetadata().withLabels(labels).endMetadata().withNewSpec().
                withContainers(container).withRestartPolicy("Always").
                endSpec().endTemplate().withNewStrategy().withType("RollingUpdate").endStrategy().endSpec().build();
        osClient.resources(Deployment.class).resource(deployment).createOr(NonDeletingOperation::update);
        Files.write(target.resolve(POSTGRESQL_NAME + "-deployment.yaml"), Serialization.asYaml(deployment).getBytes());
        IntOrString v = new IntOrString();
        v.setValue(5432);
        Service service = new ServiceBuilder().withNewMetadata().withName(POSTGRESQL_NAME).endMetadata().
                withNewSpec().withPorts(new ServicePort().toBuilder().withName("5432-tcp").withProtocol("TCP").
                        withPort(5432).
                        withTargetPort(v).build()).withType("ClusterIP").withSessionAffinity("None").withSelector(labels).endSpec().build();
        osClient.services().resource(service).createOr(NonDeletingOperation::update);
        Files.write(target.resolve(POSTGRESQL_NAME + "-service.yaml"), Serialization.asYaml(service).getBytes());
        Map<String, String> ret = new HashMap<>();
        ret.putAll(getExistingEnv(env));
        ret.putAll(POSTGRESQL_APP_MAP);
        return ret;
    }

    @Override
    public Set<String> getSupportedLayers() {
        Set<String> ret = new HashSet<>();
        ret.add("postgresql-datasource");
        ret.add("postgresql-driver");
        return ret;
    }

    @Override
    public String getName() {
        return "postgresql";
    }

}
