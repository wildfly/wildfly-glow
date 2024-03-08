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
package org.wildfly.glow.deployment.openshift.api;

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

/**
 *
 * @author jdenise
 */
public class AbstractDatabaseDeployer implements Deployer {

    private static final String SAMPLEDB = "sampledb";
    private static final String PASSWORD = "admin";
    private static final String USER = "admin";
    private static final String SERVICE_PORT_ENV_SUFFIX = "_SERVICE_PORT";
    private static final String SERVICE_HOST_ENV_SUFFIX = "_SERVICE_HOST";

    private final String dbName;
    private final String image;
    private final String envRadical;
    private final int port;

    private final Map<String, String> CONNECTION_MAP = new HashMap<>();
    private final Map<String, String> APP_MAP = new HashMap<>();

    protected AbstractDatabaseDeployer(String dbName,
            String image,
            String dbEnvRadical,
            String envRadical,
            String host,
            int port) {
        this.dbName = dbName;
        this.image = image;
        this.envRadical = envRadical;
        this.port = port;
        CONNECTION_MAP.put(dbEnvRadical + "_DATABASE", SAMPLEDB);
        CONNECTION_MAP.put(dbEnvRadical + "_PASSWORD", PASSWORD);
        CONNECTION_MAP.put(dbEnvRadical + "_USER", USER);

        APP_MAP.put(envRadical + "_DATABASE", SAMPLEDB);
        APP_MAP.put(envRadical + "_PASSWORD", PASSWORD);
        APP_MAP.put(envRadical + "_USER", USER);
        APP_MAP.put(envRadical + SERVICE_PORT_ENV_SUFFIX, "" + port);
        APP_MAP.put(envRadical + SERVICE_HOST_ENV_SUFFIX, host);
    }

    @Override
    public Map<String, String> disabledDeploy(String appHost, String appName, String matching, Map<String, String> env) {
        Map<String, String> ret = new HashMap<>();
        ret.put(envRadical + SERVICE_HOST_ENV_SUFFIX, dbName + " server host name.");
        ret.put(envRadical + SERVICE_PORT_ENV_SUFFIX, dbName + "server port.");
        ret.putAll(getExistingEnv(env));
        return ret;
    }

    private Map<String, String> getExistingEnv(Map<String, String> env) {
        Map<String, String> ret = new HashMap<>();
        for (Entry<String, String> entry : env.entrySet()) {
            if (entry.getKey().startsWith(envRadical + "_")) {
                ret.put(entry.getKey(), entry.getValue());
            }
        }
        return ret;
    }

    @Override
    public Map<String, String> deploy(GlowMessageWriter writer, Path target, OpenShiftClient osClient,
            Map<String, String> env, String appHost, String appName, String matching) throws Exception {
        writer.info("\nDeploying " + dbName + " server");
        Map<String, String> labels = new HashMap<>();
        labels.put(LABEL, dbName);
        ContainerPort port = new ContainerPort();
        port.setContainerPort(this.port);
        port.setProtocol("TCP");
        List<ContainerPort> ports = new ArrayList<>();
        ports.add(port);
        List<EnvVar> vars = new ArrayList<>();
        for (Map.Entry<String, String> entry : CONNECTION_MAP.entrySet()) {
            vars.add(new EnvVar().toBuilder().withName(entry.getKey()).withValue(entry.getValue()).build());
        }
        Container container = new Container();
        container.setName(dbName);
        container.setImage(image);
        container.setPorts(ports);
        container.setEnv(vars);
        container.setImagePullPolicy("IfNotPresent");

        Deployment deployment = new DeploymentBuilder().withNewMetadata().withName(dbName).endMetadata().
                withNewSpec().withReplicas(1).
                withNewSelector().withMatchLabels(labels).endSelector().
                withNewTemplate().withNewMetadata().withLabels(labels).endMetadata().withNewSpec().
                withContainers(container).withRestartPolicy("Always").
                endSpec().endTemplate().withNewStrategy().withType("RollingUpdate").endStrategy().endSpec().build();
        osClient.resources(Deployment.class).resource(deployment).createOr(NonDeletingOperation::update);
        Files.write(target.resolve(dbName + "-deployment.yaml"), Serialization.asYaml(deployment).getBytes());
        IntOrString v = new IntOrString();
        v.setValue(this.port);
        Service service = new ServiceBuilder().withNewMetadata().withName(dbName).endMetadata().
                withNewSpec().withPorts(new ServicePort().toBuilder().withName(this.port + "-tcp").withProtocol("TCP").
                        withPort(this.port).
                        withTargetPort(v).build()).withType("ClusterIP").withSessionAffinity("None").withSelector(labels).endSpec().build();
        osClient.services().resource(service).createOr(NonDeletingOperation::update);
        Files.write(target.resolve(dbName + "-service.yaml"), Serialization.asYaml(service).getBytes());
        Map<String, String> ret = new HashMap<>();
        ret.putAll(getExistingEnv(env));
        ret.putAll(APP_MAP);
        return ret;
    }

    @Override
    public Set<String> getSupportedLayers() {
        Set<String> ret = new HashSet<>();
        ret.add(dbName + "-datasource");
        ret.add(dbName + "-driver");
        return ret;
    }

    @Override
    public String getName() {
        return dbName;
    }

}
