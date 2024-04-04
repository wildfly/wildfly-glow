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
import io.fabric8.kubernetes.api.model.HTTPGetAction;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.ImageLookupPolicy;
import io.fabric8.openshift.api.model.ImageSource;
import io.fabric8.openshift.api.model.ImageSourceBuilder;
import io.fabric8.openshift.api.model.ImageSourcePath;
import io.fabric8.openshift.api.model.ImageSourcePathBuilder;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteBuilder;
import io.fabric8.openshift.api.model.RouteTargetReference;
import io.fabric8.openshift.api.model.TLSConfig;
import io.fabric8.openshift.client.OpenShiftClient;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayers;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.wildfly.glow.ConfigurationResolver;
import org.wildfly.glow.Env;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.Layer;

/**
 *
 * @author jdenise
 */
public class OpenShiftSupport {

    private static class BuildWatcher implements Watcher<Build>, AutoCloseable {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final GlowMessageWriter writer;
        private boolean failed;
        BuildWatcher(GlowMessageWriter writer) {
            this.writer = writer;
        }
        @Override
        public void eventReceived(Action action, Build build) {
            String phase = build.getStatus().getPhase();
            if ("Running".equals(phase)) {
                writer.info("Build is running...");
            }
            if ("Complete".equals(phase)) {
                writer.info("Build is complete.");
                latch.countDown();
            }
            if ("Failed".equals(phase)) {
                writer.info("Build Failed.");
                failed = true;
                latch.countDown();
            }
        }

        @Override
        public void onClose(WatcherException cause) {
        }
        void await() throws InterruptedException {
            latch.await();
        }
        boolean isFailed() {
            return failed;
        }

        @Override
        public void close() throws Exception {
        }
    }
    private static void createAppDeployment(GlowMessageWriter writer, Path target, OpenShiftClient osClient, String name, Map<String, String> env, boolean ha) throws Exception {
        Map<String, String> labels = new HashMap<>();
        labels.put(Deployer.LABEL, name);
        ContainerPort port = new ContainerPort();
        port.setContainerPort(8080);
        port.setName("http");
        port.setProtocol("TCP");

        ContainerPort portAdmin = new ContainerPort();
        portAdmin.setContainerPort(9990);
        portAdmin.setName("admin");
        portAdmin.setProtocol("TCP");

        List<ContainerPort> ports = new ArrayList<>();
        ports.add(port);
        ports.add(portAdmin);
        List<EnvVar> vars = new ArrayList<>();
        for (Entry<String, String> entry : env.entrySet()) {
            vars.add(new EnvVar().toBuilder().withName(entry.getKey()).withValue(entry.getValue()).build());
        }
        if (ha) {
            writer.info("\n HA enabled, 2 replicas will be started.");
            vars.add(new EnvVar().toBuilder().withName("JGROUPS_PING_PROTOCOL").withValue("DNS_PING").build());
            vars.add(new EnvVar().toBuilder().withName("OPENSHIFT_DNS_PING_SERVICE_PORT").withValue("8888").build());
            vars.add(new EnvVar().toBuilder().withName("OPENSHIFT_DNS_PING_SERVICE_NAME").withValue(name + "-ping").build());
            IntOrString v = new IntOrString();
            v.setValue(8888);
            Service pingService = new ServiceBuilder().withNewMetadata().withName(name + "-ping").endMetadata().
                    withNewSpec().withPorts(new ServicePort().toBuilder().withProtocol("TCP").
                            withPort(8888).
                            withName("ping").
                            withTargetPort(v).build()).
                    withClusterIP("None").withPublishNotReadyAddresses().withIpFamilies("IPv4").
                    withInternalTrafficPolicy("Cluster").withClusterIPs("None").
                    withType("ClusterIP").withIpFamilyPolicy("SingleStack").
                    withSessionAffinity("None").withSelector(labels).endSpec().build();
            osClient.services().resource(pingService).createOr(NonDeletingOperation::update);
            Utils.persistResource(target, pingService, name + "-ping-service.yaml");
        }
        Container container = new Container();
        container.setName(name);
        container.setImage(name + ":latest");
        container.setPorts(ports);
        container.setEnv(vars);
        container.setImagePullPolicy("IfNotPresent");
        Probe readinessProbe = new Probe();
        HTTPGetAction getAction = new HTTPGetAction();
        getAction.setPath("/health/ready");
        IntOrString pp = new IntOrString("admin");
        getAction.setPort(pp);
        getAction.setScheme("HTTP");
        readinessProbe.setHttpGet(getAction);
        readinessProbe.setTimeoutSeconds(1);
        readinessProbe.setPeriodSeconds(10);
        readinessProbe.setSuccessThreshold(1);
        readinessProbe.setFailureThreshold(3);

        container.setReadinessProbe(readinessProbe);
        container.setTerminationMessagePath("/dev/termination-log");

        Probe livenessProbe = new Probe();
        HTTPGetAction getAction2 = new HTTPGetAction();
        getAction2.setPath("/health/live");
        IntOrString pp2 = new IntOrString("admin");
        getAction2.setPort(pp2);
        getAction2.setScheme("HTTP");
        livenessProbe.setHttpGet(getAction);
        livenessProbe.setTimeoutSeconds(1);
        livenessProbe.setPeriodSeconds(10);
        livenessProbe.setSuccessThreshold(1);
        livenessProbe.setFailureThreshold(3);
        container.setLivenessProbe(livenessProbe);

        Deployment deployment = new DeploymentBuilder().withNewMetadata().withName(name).endMetadata().
                withNewSpec().withReplicas(ha ? 2 : 1).
                withNewSelector().withMatchLabels(labels).endSelector().
                withNewTemplate().withNewMetadata().withLabels(labels).endMetadata().withNewSpec().
                withContainers(container).withRestartPolicy("Always").
                endSpec().endTemplate().withNewStrategy().withType("RollingUpdate").endStrategy().endSpec().build();
        osClient.resources(Deployment.class).resource(deployment).createOr(NonDeletingOperation::update);
        Utils.persistResource(target, deployment, name + "-deployment.yaml");
        IntOrString v = new IntOrString();
        v.setValue(8080);
        Service service = new ServiceBuilder().withNewMetadata().withName(name).endMetadata().
                withNewSpec().withPorts(new ServicePort().toBuilder().withProtocol("TCP").
                        withPort(8080).
                        withTargetPort(v).build()).withType("ClusterIP").withSessionAffinity("None").withSelector(labels).endSpec().build();
        osClient.services().resource(service).createOr(NonDeletingOperation::update);
        Utils.persistResource(target, service, name + "-service.yaml");

        writer.info("\nWaiting until the application is ready ...");
        osClient.resources(Deployment.class).resource(deployment).waitUntilReady(5, TimeUnit.MINUTES);
    }

    public static ConfigurationResolver.ResolvedEnvs getResolvedEnvs(Layer layer, Set<Env> input, Set<String> disabledDeployers) throws Exception {
        ConfigurationResolver.ResolvedEnvs resolved = null;
        List<Deployer> deployers = getEnabledDeployers(disabledDeployers);
        for (Deployer d : deployers) {
            if (d.getSupportedLayers().contains(layer.getName())) {
               Set<Env> envs = d.getResolvedEnvs(input);
               if (envs != null && !envs.isEmpty()) {
                   resolved = new ConfigurationResolver.ResolvedEnvs("openshift/"+d.getName(), envs);
                   break;
               }
            }
        }
        return resolved;
    }

    private static List<Deployer> getEnabledDeployers(Set<String> disabledDeployers) throws Exception {
        Map<String, Deployer> existingDeployers = new HashMap<>();

        for (Deployer d : ServiceLoader.load(Deployer.class)) {
            existingDeployers.put(d.getName(), d);
        }
        for (String disabled : disabledDeployers) {
            if (!"ALL".equals(disabled)) {
                if (!existingDeployers.containsKey(disabled)) {
                    throw new Exception("Invalid deployer to disable: " + disabled);
                }
            }
        }
        List<Deployer> deployers = new ArrayList<>();
        for (Deployer d : existingDeployers.values()) {
            boolean isDisabled = isDisabled(d.getName(), disabledDeployers);
            if(!isDisabled) {
                deployers.add(d);
            }
        }
        return deployers;
    }

    public static void deploy(GlowMessageWriter writer,
            Path target,
            String appName,
            Map<String, String> env,
            Set<Layer> layers,
            Set<Layer> metadataOnlyLayers,
            boolean ha,
            Map<String, String> extraEnv,
            Map<String, String> buildExtraEnv,
            Map<Layer, Set<Env>> requiredBuildTime,
            Set<String> disabledDeployers,
            Path initScript,
            Path cliScript,
            OpenShiftConfiguration config,
            MavenRepoManager mvnResolver) throws Exception {
        Set<Layer> allLayers = new LinkedHashSet<>();
        allLayers.addAll(layers);
        allLayers.addAll(metadataOnlyLayers);
        Map<String, String> actualEnv = new TreeMap<>();
        Map<String, String> actualBuildEnv = new TreeMap<>();
        OpenShiftClient osClient = new KubernetesClientBuilder().build().adapt(OpenShiftClient.class);
        writer.info("\nConnected to OpenShift cluster");
        // First create the future route to the application, can be needed by deployers
        Route route = new RouteBuilder().withNewMetadata().withName(appName).
                endMetadata().withNewSpec().
                withTo(new RouteTargetReference("Service", appName, 100)).
                withTls(new TLSConfig().toBuilder().withTermination("edge").
                        withInsecureEdgeTerminationPolicy("Redirect").build()).endSpec().build();
        osClient.routes().resource(route).createOr(NonDeletingOperation::update);
        Utils.persistResource(target, route, appName + "-route.yaml");
        String host = osClient.routes().resource(route).get().getSpec().getHost();
        // Done route creation
        List<Deployer> deployers = getEnabledDeployers(disabledDeployers);
        for (Deployer d : deployers) {
            boolean isDisabled = isDisabled(d.getName(), disabledDeployers);
            for (Layer l : allLayers) {
                if (d.getSupportedLayers().contains(l.getName())) {
                    if (!isDisabled) {
                        writer.info("\nFound deployer " + d.getName() + " for " + l.getName());
                    } else {
                        writer.warn("\nThe deployer " + d.getName() + " has been disabled");
                    }
                    actualEnv.putAll(isDisabled ? Collections.emptyMap(): d.deploy(writer, target, osClient, env, host, appName, l.getName(), extraEnv));
                    Set<Env> buildEnv =  requiredBuildTime.get(l);
                    if (buildEnv != null) {
                        Set<String> names = new HashSet<>();
                        for(Env e : buildEnv) {
                            if (!buildExtraEnv.containsKey(e.getName())) {
                                names.add(e.getName());
                            }
                        }
                        actualBuildEnv.putAll(d.handleBuildTimeDefault(names, mvnResolver));
                    }
                    break;
                }
            }
        }
        actualEnv.put("APPLICATION_ROUTE_HOST", host);
        actualEnv.putAll(extraEnv);
        if (!disabledDeployers.isEmpty()) {
            writer.warn("The following environment variables will be set in the " + appName + " deployment. Make sure that the required env variables for the disabled deployer(s) have been set:\n");
        } else {
            writer.warn("The following environment variables will be set in the " + appName + " deployment:\n");
        }
        for (Entry<String, String> entry : actualEnv.entrySet()) {
            writer.warn(entry.getKey() + "=" + entry.getValue());
        }
        // Can be overriden by user
        actualBuildEnv.putAll(buildExtraEnv);
        createBuild(writer, target, osClient, appName, initScript, cliScript, actualBuildEnv, config);
        writer.info("Deploying application image on OpenShift");
        createAppDeployment(writer, target, osClient, appName, actualEnv, ha);
        writer.info("Application route: https://" + host + ("ROOT.war".equals(appName) ? "" : "/" + appName));
    }

    private static void createBuild(GlowMessageWriter writer,
            Path target,
            OpenShiftClient osClient,
            String name,
            Path initScript,
            Path cliScript,
            Map<String, String> buildExtraEnv,
            OpenShiftConfiguration config) throws Exception {
        String serverImageName = doServerImageBuild(writer, target, osClient, buildExtraEnv, config);
        doAppImageBuild(serverImageName, writer, target, osClient, name, initScript, cliScript, config);
    }

    private static boolean packageInitScript(Path initScript, Path cliScript, Path target) throws Exception {
        if (initScript != null || cliScript != null) {
            Path extensions = target.resolve("extensions");
            Files.createDirectories(extensions);
            StringBuilder initExecution = new StringBuilder();
            initExecution.append("#!/bin/bash").append("\n");
            if (initScript != null) {
                initExecution.append("echo \"Calling initialization script\"").append("\n");
                Path init = extensions.resolve("init-script.sh");
                Files.copy(initScript, init);
                initExecution.append("sh $JBOSS_HOME/extensions/init-script.sh").append("\n");
            }
            if (cliScript != null) {
                initExecution.append("echo \"Calling CLI script\"").append("\n");
                Path cli = extensions.resolve("cli-script.cli");
                Files.copy(cliScript, cli);
                initExecution.append("cat $JBOSS_HOME/extensions/cli-script.cli >> \"${CLI_SCRIPT_FILE}\"");
            }
            Path postconfigure = extensions.resolve("postconfigure.sh");
            Files.write(postconfigure, initExecution.toString().getBytes());
            return true;
        }
        return false;
    }

    private static boolean isDisabled(String name, Set<String> disabledDeployers) {
        return disabledDeployers.contains("ALL") || disabledDeployers.contains(name);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static Map<String, String> createLabels(Path target, Path provisioning, OpenShiftConfiguration osConfig) throws Exception {
        GalleonBuilder provider = new GalleonBuilder();
        Path dir = target.resolve("tmp").resolve("tmpHome");
        Files.createDirectory(dir);
        StringBuilder fps = new StringBuilder();
        Map<String, String> labels = new HashMap<>();
        try (Provisioning p = provider.newProvisioningBuilder(provisioning).setInstallationHome(dir).build()) {
            GalleonProvisioningConfig config = provider.newProvisioningBuilder(provisioning).setInstallationHome(dir).build().loadProvisioningConfig(provisioning);
            GalleonConfigurationWithLayers cl = config.getDefinedConfig(new ConfigId("standalone", "standalone.xml"));
            for (String s : cl.getIncludedLayers()) {
                labels.put(osConfig.getLabelRadical() + ".layer." + s, "");
            }
            for (String s : cl.getExcludedLayers()) {
                labels.put(osConfig.getLabelRadical() + ".excluded.layer." + s, "");
            }
            for (GalleonFeaturePackConfig gfpc : config.getFeaturePackDeps()) {
                if (fps.length() != 0) {
                    fps.append("_");
                }
                String producerName = gfpc.getLocation().getProducerName();
                producerName = producerName.replaceAll("::zip", "");
                int i = producerName.indexOf(":");
                if (i > 0) {
                    producerName = producerName.substring(i + 1);
                }
                producerName = producerName.replaceAll(":", "-");
                labels.put(osConfig.getLabelRadical() + ".feature-pack." + producerName, "");
            }
        }
        return labels;
    }

    private static String doServerImageBuild(GlowMessageWriter writer, Path target, OpenShiftClient osClient,
            Map<String, String> buildExtraEnv,
            OpenShiftConfiguration config) throws Exception {
        // To compute a hash we need build time env variables
        StringBuilder contentBuilder = new StringBuilder();
        Path provisioning = target.resolve("galleon").resolve("provisioning.xml");
        contentBuilder.append(Files.readString(provisioning, Charset.forName("UTF-8")));
        for (Entry<String, String> entry : buildExtraEnv.entrySet()) {
            contentBuilder.append(entry.getKey() + "=" + entry.getValue());
        }
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] encodedhash = digest.digest(contentBuilder.toString().getBytes());
        String key = bytesToHex(encodedhash);
        String serverImageName = config.getServerImageNameRadical() + key;

        ImageStream stream = new ImageStreamBuilder().withNewMetadata().withName(serverImageName).
                endMetadata().withNewSpec().withLookupPolicy(new ImageLookupPolicy(Boolean.TRUE)).endSpec().build();
        // check if it exists
        ImageStream existingStream = osClient.imageStreams().resource(stream).get();
        if (existingStream == null) {
            writer.info("\nBuilding server image (this can take up to few minutes)...");
            // zip deployment and provisioning.xml to be pushed to OpenShift
            Path file = target.resolve("tmp").resolve("openshiftServer.zip");
            if (Files.exists(file)) {
                Files.delete(file);
            }
            // First do a build of the naked server
            Path stepOne = target.resolve("tmp").resolve("step-one");
            Files.createDirectories(stepOne);
            IoUtils.copy(target.resolve("galleon"), stepOne.resolve("galleon"));
            ZipUtils.zip(stepOne, file);
            stream = stream.toBuilder().editOrNewMetadata().withLabels(createLabels(target, provisioning, config)).endMetadata().build();
            osClient.imageStreams().resource(stream).createOr(NonDeletingOperation::update);
            Utils.persistResource(target, stream, serverImageName + "-image-stream.yaml");
            BuildConfigBuilder builder = new BuildConfigBuilder();
            ObjectReference ref = new ObjectReference();
            ref.setKind("ImageStreamTag");
            ref.setName(serverImageName + ":latest");
            List<EnvVar> vars = new ArrayList<>();
            vars.add(new EnvVar().toBuilder().withName("GALLEON_USE_LOCAL_FILE").withValue("true").build());
            if (!buildExtraEnv.isEmpty()) {
                writer.warn("\nThe following environment variables have been set in the " + serverImageName + " buildConfig:\n");
                for (Map.Entry<String, String> entry : buildExtraEnv.entrySet()) {
                    String val = buildExtraEnv.get(entry.getKey());
                    writer.warn(entry.getKey()+"="+entry.getValue());
                    vars.add(new EnvVar().toBuilder().withName(entry.getKey()).withValue(val == null ? entry.getValue() : val).build());
                }
            }
            BuildConfig buildConfig = builder.
                    withNewMetadata().withName(serverImageName + "-build").endMetadata().withNewSpec().
                    withNewOutput().
                    withNewTo().
                    withKind("ImageStreamTag").
                    withName(serverImageName + ":latest").endTo().
                    endOutput().withNewStrategy().withNewSourceStrategy().withNewFrom().withKind("DockerImage").
                    withName(config.getBuilderImage()).endFrom().
                    withIncremental(true).
                    withEnv(vars).
                    endSourceStrategy().endStrategy().withNewSource().
                    withType("Binary").endSource().endSpec().build();
            osClient.buildConfigs().resource(buildConfig).createOr(NonDeletingOperation::update);
            Utils.persistResource(target, buildConfig, serverImageName + "-build-config.yaml");

            Build build = osClient.buildConfigs().withName(serverImageName + "-build").instantiateBinary().fromFile(file.toFile());
            BuildWatcher buildWatcher = new BuildWatcher(writer);
            try (Watch watcher = osClient.builds().withName(build.getMetadata().getName()).watch(buildWatcher)) {
                buildWatcher.await();
            }
            if(buildWatcher.isFailed()) {
                osClient.imageStreams().resource(stream).delete();
                throw new Exception("Server image build has failed. Check the OpenShift build log.");
            }
        }
        return serverImageName;
    }

    private static void doAppImageBuild(String serverImageName,
            GlowMessageWriter writer,
            Path target,
            OpenShiftClient osClient,
            String name,
            Path initScript,
            Path cliScript,
            OpenShiftConfiguration config) throws Exception {
        // Now step 2
        // From the server image, do a docker build, copy the server and copy in it the deployments and init file.
        Path stepTwo = target.resolve("tmp").resolve("step-two");
        IoUtils.copy(target.resolve("deployments"), stepTwo.resolve("deployments"));
        StringBuilder dockerFileBuilder = new StringBuilder();
        dockerFileBuilder.append("FROM " + config.getRuntimeImage() + "\n");
        dockerFileBuilder.append("COPY --chown=jboss:root /server $JBOSS_HOME\n");
        dockerFileBuilder.append("COPY --chown=jboss:root deployments/* $JBOSS_HOME/standalone/deployments\n");
        if (packageInitScript(initScript, cliScript, stepTwo)) {
            dockerFileBuilder.append("COPY --chown=jboss:root extensions $JBOSS_HOME/extensions\n");
            dockerFileBuilder.append("RUN chmod ug+rwx $JBOSS_HOME/extensions/postconfigure.sh\n");
        }

        dockerFileBuilder.append("RUN chmod -R ug+rwX $JBOSS_HOME\n");

        Path dockerFile = stepTwo.resolve("Dockerfile");
        Files.write(dockerFile, dockerFileBuilder.toString().getBytes());
        Path file2 = target.resolve("tmp").resolve("openshiftApp.zip");
        if (Files.exists(file2)) {
            Files.delete(file2);
        }
        ZipUtils.zip(stepTwo, file2);
        writer.info("\nBuilding application image...");
        ImageStream appStream = new ImageStreamBuilder().withNewMetadata().withName(name).
                endMetadata().withNewSpec().withLookupPolicy(new ImageLookupPolicy(Boolean.TRUE)).endSpec().build();
        osClient.imageStreams().resource(appStream).createOr(NonDeletingOperation::update);
        BuildConfigBuilder builder = new BuildConfigBuilder();
        ObjectReference ref = new ObjectReference();
        ref.setKind("ImageStreamTag");
        ref.setName(serverImageName + ":latest");
        ImageSourcePath srcPath = new ImageSourcePathBuilder().withSourcePath("/opt/server").withDestinationDir(".").build();
        ImageSource imageSource = new ImageSourceBuilder().withFrom(ref).withPaths(srcPath).build();
        BuildConfig buildConfig2 = builder.
                withNewMetadata().withName(name + "-build").endMetadata().withNewSpec().
                withNewOutput().
                withNewTo().
                withKind("ImageStreamTag").
                withName(name + ":latest").endTo().
                endOutput().
                withNewSource().withType("Binary").withImages(imageSource).endSource().
                withNewStrategy().withNewDockerStrategy().withNewFrom().withKind("DockerImage").
                withName("quay.io/wildfly/wildfly-runtime:latest").endFrom().
                withDockerfilePath("./Dockerfile").
                endDockerStrategy().endStrategy().endSpec().build();
        osClient.buildConfigs().resource(buildConfig2).createOr(NonDeletingOperation::update);
        Utils.persistResource(target, buildConfig2, name + "-build-config.yaml");

        Build build = osClient.buildConfigs().withName(name + "-build").instantiateBinary().fromFile(file2.toFile());
        CountDownLatch latch = new CountDownLatch(1);
        BuildWatcher buildWatcher = new BuildWatcher(writer);
        try (Watch watcher = osClient.builds().withName(build.getMetadata().getName()).watch(buildWatcher)) {
            buildWatcher.await();
        }
        if (buildWatcher.isFailed()) {
            osClient.imageStreams().resource(appStream).delete();
            throw new Exception("Application image build has failed. Check the OpenShift build log.");
        }
    }
}
