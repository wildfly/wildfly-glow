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
package org.wildfly.glow.cli.commands;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HTTPGetAction;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
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
import io.fabric8.openshift.api.model.TagReferenceBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.wildfly.glow.AddOn;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.Layer;
import org.wildfly.glow.deployment.openshift.api.Deployer;
import org.wildfly.glow.deployment.openshift.api.Utils;
/**
 *
 * @author jdenise
 */
class OpenShiftSupport {

    private static void createAppDeployment(GlowMessageWriter writer, Path target, OpenShiftClient osClient, String name, Map<String, String> env, boolean ha) throws Exception {
        writer.info("Deploying application image on OpenShift");
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

        writer.info("Waiting until the application is ready ...");
        osClient.resources(Deployment.class).resource(deployment).waitUntilReady(5, TimeUnit.MINUTES);
    }

    static void deploy(GlowMessageWriter writer, Path target, String appName, Map<String, String> env, Set<Layer> layers, Set<AddOn> addOns, boolean ha,
            Map<String, String> extraEnv, Set<String> disabledDeployers, Path initScript) throws Exception {
        Map<String, String> actualEnv = new TreeMap<>();
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
        for (Deployer d : existingDeployers.values()) {
            boolean deployed = false;
            boolean isDisabled = isDisabled(d.getName(), disabledDeployers);
            for (Layer l : layers) {
                if (d.getSupportedLayers().contains(l.getName())) {
                    deployed = true;
                    if (!isDisabled) {
                        writer.info("Found deployer " + d.getName() + " for " + l.getName());
                    } else {
                        writer.warn("The deployer " + d.getName() + " has been disabled");
                    }
                    actualEnv.putAll(isDisabled ? d.disabledDeploy(host, appName, l.getName(), env) : d.deploy(writer, target, osClient, env, host, appName, l.getName()));
                    break;
                }
            }
            if (!deployed) {
                for (AddOn ao : addOns) {
                    if (ao.getFamily().equals(d.getSupportedAddOnFamily())
                            && d.getSupportedAddOns().contains(ao.getName())) {
                        if (!isDisabled) {
                            writer.info("Found deployer " + d.getName() + " for " + ao.getName());
                        } else {
                            writer.warn("The deployer " + d.getName() + " has been disabled");
                        }
                        actualEnv.putAll(isDisabled ? d.disabledDeploy(host, appName, ao.getName(), env) : d.deploy(writer, target, osClient, env, host, appName, ao.getName()));
                        break;
                    }
                }
            }
        }

        createBuild(writer, target, osClient, appName, initScript);
        actualEnv.put("APPLICATION_ROUTE_HOST", host);
        actualEnv.putAll(extraEnv);
        if (!actualEnv.isEmpty()) {
            if (!disabledDeployers.isEmpty()) {
                writer.warn("\nThe following environment variables have been set in the " + appName + " deployment. WARN: Some of them need possibly to be updated in the deployment:\n");
            } else {
                writer.warn("\nThe following environment variables have been set in the " + appName + " deployment:\n");
            }
            for (Entry<String, String> entry : actualEnv.entrySet()) {
                writer.warn(entry.getKey() + "=" + entry.getValue());
            }
            writer.warn("\n");
        }
        createAppDeployment(writer, target, osClient, appName, actualEnv, ha);
        writer.info("\nApplication route: https://" + host + ("ROOT.war".equals(appName) ? "" : "/" + appName));
    }

    static void createBuild(GlowMessageWriter writer, Path target, OpenShiftClient osClient, String name, Path initScript) throws Exception {
        String serverImageName = doServerImageBuild(writer, target, osClient);
        doAppImageBuild(serverImageName, writer, target, osClient, name, initScript);
    }

    private static void packageInitScript(Path initScript, Path target) throws Exception {
        Path extensions = target.resolve("extensions");
        Files.createDirectories(extensions);
        Path postconfigure = extensions.resolve("postconfigure.sh");
        Files.copy(initScript, postconfigure);
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

    static Map<String, String> createLabels(Path target, Path provisioning) throws Exception {
        GalleonBuilder provider = new GalleonBuilder();
        Path dir = target.resolve("tmp").resolve("tmpHome");
        Files.createDirectory(dir);
        StringBuilder fps = new StringBuilder();
        Map<String, String> labels = new HashMap<>();
        try (Provisioning p = provider.newProvisioningBuilder(provisioning).setInstallationHome(dir).build()) {
            GalleonProvisioningConfig config = provider.newProvisioningBuilder(provisioning).setInstallationHome(dir).build().loadProvisioningConfig(provisioning);
            GalleonConfigurationWithLayers cl = config.getDefinedConfig(new ConfigId("standalone", "standalone.xml"));
            for(String s : cl.getIncludedLayers()) {
                labels.put("org.wildfly.glow.layer."+s,"");
            }
            for(String s : cl.getExcludedLayers()) {
                labels.put("org.wildfly.glow.excluded.layer."+s,"");
            }
            for (GalleonFeaturePackConfig gfpc : config.getFeaturePackDeps()) {
                if (fps.length() != 0) {
                    fps.append("_");
                }
                String producerName = gfpc.getLocation().getProducerName();
                producerName = producerName.replaceAll("::zip", "");
                int i = producerName.indexOf(":");
                if(i > 0) {
                    producerName = producerName.substring(i+1);
                }
                producerName = producerName.replaceAll(":", "-");
                labels.put("org.wildfly.glow.feature-pack."+producerName,"");
            }
        }
        return labels;
    }

    static String doServerImageBuild(GlowMessageWriter writer, Path target, OpenShiftClient osClient) throws Exception {
        Path provisioning = target.resolve("galleon").resolve("provisioning.xml");
        byte[] content = Files.readAllBytes(provisioning);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(content);
        String key = bytesToHex(encodedhash);
        String serverImageName = "wildfly-server-" + key;

        ImageStream stream = new ImageStreamBuilder().withNewMetadata().withName(serverImageName).
                endMetadata().withNewSpec().withLookupPolicy(new ImageLookupPolicy(Boolean.TRUE)).endSpec().build();
        // check if it exists
        ImageStream existingStream = osClient.imageStreams().resource(stream).get();
        if (existingStream == null) {
            writer.info("\nBuilding server image (this can take up to few minutes the first time)...");
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
            stream = stream.toBuilder().editOrNewMetadata().withLabels(createLabels(target, provisioning)).endMetadata().build();
            osClient.imageStreams().resource(stream).createOr(NonDeletingOperation::update);
            Utils.persistResource(target, stream, serverImageName + "-image-stream.yaml");
            BuildConfigBuilder builder = new BuildConfigBuilder();
            ObjectReference ref = new ObjectReference();
            ref.setKind("ImageStreamTag");
            ref.setName(serverImageName + ":latest");
            BuildConfig buildConfig = builder.
                    withNewMetadata().withName(serverImageName + "-build").endMetadata().withNewSpec().
                    withNewOutput().
                    withNewTo().
                    withKind("ImageStreamTag").
                    withName(serverImageName + ":latest").endTo().
                    endOutput().withNewStrategy().withNewSourceStrategy().withNewFrom().withKind("DockerImage").
                    withName("quay.io/wildfly/wildfly-s2i:latest").endFrom().
                    withIncremental(true).
                    withEnv(new EnvVar().toBuilder().withName("GALLEON_USE_LOCAL_FILE").withValue("true").build()).
                    endSourceStrategy().endStrategy().withNewSource().
                    withType("Binary").endSource().endSpec().build();
            osClient.buildConfigs().resource(buildConfig).createOr(NonDeletingOperation::update);
            Utils.persistResource(target, buildConfig, serverImageName + "-build-config.yaml");

            Build build = osClient.buildConfigs().withName(serverImageName + "-build").instantiateBinary().fromFile(file.toFile());
            CountDownLatch latch = new CountDownLatch(1);
            try (Watch watcher = osClient.builds().withName(build.getMetadata().getName()).watch(getBuildWatcher(writer, latch))) {
                latch.await();
            }
        }
        return serverImageName;
    }

    static void doAppImageBuild(String serverImageName, GlowMessageWriter writer, Path target, OpenShiftClient osClient, String name, Path initScript) throws Exception {
        // Now step 2
        // From the server image, do a docker build, copy the server and copy in it the deployments and init file.
        Path stepTwo = target.resolve("tmp").resolve("step-two");
        IoUtils.copy(target.resolve("deployments"), stepTwo.resolve("deployments"));
        StringBuilder dockerFileBuilder = new StringBuilder();
        dockerFileBuilder.append("FROM wildfly-runtime:latest\n");
        dockerFileBuilder.append("COPY --chown=jboss:root /server $JBOSS_HOME\n");
        dockerFileBuilder.append("COPY --chown=jboss:root deployments/* $JBOSS_HOME/standalone/deployments\n");

        if (initScript != null) {
            packageInitScript(initScript, stepTwo);
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
        ImageStream runtimeStream = new ImageStreamBuilder().withNewMetadata().withName("wildfly-runtime").
                endMetadata().withNewSpec().
                addToTags(0, new TagReferenceBuilder()
                        .withName("latest")
                        .withFrom(new ObjectReferenceBuilder()
                                .withKind("DockerImage")
                                .withName("quay.io/wildfly/wildfly-runtime:latest")
                                .build())
                        .build()).
                withLookupPolicy(new ImageLookupPolicy(Boolean.TRUE)).endSpec().build();
        osClient.imageStreams().resource(runtimeStream).createOr(NonDeletingOperation::update);
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
                withNewStrategy().withNewDockerStrategy().withNewFrom().withKind("ImageStream").
                withName("wildfly-runtime").endFrom().
                withDockerfilePath("./Dockerfile").
                endDockerStrategy().endStrategy().endSpec().build();
        osClient.buildConfigs().resource(buildConfig2).createOr(NonDeletingOperation::update);
        Utils.persistResource(target, buildConfig2, name + "-build-config.yaml");

        Build build = osClient.buildConfigs().withName(name + "-build").instantiateBinary().fromFile(file2.toFile());
        CountDownLatch latch = new CountDownLatch(1);
        try (Watch watcher = osClient.builds().withName(build.getMetadata().getName()).watch(getBuildWatcher(writer, latch))) {
            latch.await();
        }
    }

    private static Watcher<Build> getBuildWatcher(GlowMessageWriter writer, final CountDownLatch latch) {
        return new Watcher<Build>() {
            @Override
            public void eventReceived(Action action, Build build) {
                //buildHolder.set(build);
                String phase = build.getStatus().getPhase();
                if ("Running".equals(phase)) {
                    writer.info("Build is running...");
                }
                if ("Complete".equals(phase)) {
                    writer.info("Build is complete.");
                    latch.countDown();
                }
            }

            @Override
            public void onClose(WatcherException cause) {
            }
        };
    }
}
