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
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
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
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.glow.ConfigurationResolver;
import org.wildfly.glow.Env;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.Layer;
import org.wildfly.glow.ScanResults;
/**
 *
 * @author jdenise
 */
public class OpenShiftSupport {
    private static final String DEPLOYMENT_RESOURCE_DIR = "deployment";
    private static final String BUILD_RESOURCE_DIR = "build";
    private static final String IMAGES_RESOURCE_DIR = "images";
    private static final String DEPLOYMENTS_DIR = "deployments";
    private static final String DOCKER_DIR = "docker";
    private static final String EXTENSIONS_DIR = "extensions";
    private static final String TMP_DIR = "tmp";
    private static final String DOCKER_SERVER_DIR = "server";
    private static final String DOCKER_APP_DIR = "app";
    private static final String DEPLOYERS_RESOURCE_DIR = "deployers";
    private static final String RESOURCES_DIR = "resources";
    private static final String WILDFLY_GLOW_SERVER_IMAGE_REPOSITORY = "WILDFLY_GLOW_SERVER_IMAGE_REPOSITORY";
    private static final String WILDFLY_GLOW_APP_IMAGE_REPOSITORY = "WILDFLY_GLOW_APP_IMAGE_REPOSITORY";
    private static final String IMAGE_PROPERTIES_FILE = "images.properties";

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

    public static Path getDeploymentDirectory(Path target) throws IOException {
        return createResourcesDirectory(target, DEPLOYMENT_RESOURCE_DIR);
    }

    public static Path getBuildDirectory(Path target) throws IOException {
        return createResourcesDirectory(target, BUILD_RESOURCE_DIR);
    }

    public static Path getImagesDirectory(Path target) throws IOException {
        return createResourcesDirectory(target, IMAGES_RESOURCE_DIR);
    }

    public static Path getDockerServerDirectory(Path target) throws IOException {
        return createDockerDirectory(target, DOCKER_SERVER_DIR);
    }

    public static Path getDockerAppDirectory(Path target) throws IOException {
        return createDockerDirectory(target, DOCKER_APP_DIR);
    }

    public static Path getExtensionsDirectory(Path target) throws IOException {
        Path ext = createDockerDirectory(target, DOCKER_APP_DIR).resolve(EXTENSIONS_DIR);
        Files.createDirectories(ext);
        return ext;
    }

    public static Path getDeploymentsDirectory(Path target) throws IOException {
        Path deps = createDockerDirectory(target, DOCKER_APP_DIR).resolve(DEPLOYMENTS_DIR);
        Files.createDirectories(deps);
        return deps;
    }

    public static Path getDockerDirectory(Path target) throws IOException {
        Path dir = target.resolve(DOCKER_DIR);
        Files.createDirectories(dir);
        return dir;
    }

    public static Path getTmpDirectory(Path target) throws IOException {
        Path dir = target.resolve(TMP_DIR);
        Files.createDirectories(dir);
        return dir;
    }

    public static Path getDeployersDirectory(Path target) throws IOException {
        return createResourcesDirectory(target, DEPLOYERS_RESOURCE_DIR);
    }

    private static Path createResourcesDirectory(Path path, String name) throws IOException {
        Path dir = getResourcesDirectory(path).resolve(name);
        Files.createDirectories(dir);
        return dir;
    }
    private static Path getResourcesDirectory(Path path) throws IOException {
        Path dir = path.resolve(RESOURCES_DIR);
        Files.createDirectories(dir);
        return dir;
    }
    private static Path createDockerDirectory(Path path, String name) throws IOException {
        Path dir = getDockerDirectory(path).resolve(name);
        Files.createDirectories(dir);
        return dir;
    }

    private static void createAppDeployment(GlowMessageWriter writer, Path target,
            OpenShiftClient osClient, String appName, Map<String, String> env, boolean ha,
            OpenShiftConfiguration config,
            String deploymentKind, String appImageTag) throws Exception {
        Map<String, String> matchLabels = new HashMap<>();
        matchLabels.put(Deployer.LABEL, appName);
        IntOrString value = new IntOrString();
        value.setValue(8080);
        Service service = new ServiceBuilder().withNewMetadata().withLabels(createCommonLabels(config)).withName(appName).endMetadata().
                withNewSpec().withPorts(new ServicePort().toBuilder().withProtocol("TCP").
                        withPort(8080).
                        withTargetPort(value).build()).withType("ClusterIP").withSessionAffinity("None").withSelector(matchLabels).endSpec().build();
        if (osClient != null) {
            osClient.services().resource(service).createOr(NonDeletingOperation::update);
        }

        Utils.persistResource(getDeploymentDirectory(target), service, appName + "-service.yaml");

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
            writer.info("\nHA enabled, 2 replicas will be started.");
            IntOrString v = new IntOrString();
            v.setValue(8888);
            Service pingService = new ServiceBuilder().withNewMetadata().withLabels(createCommonLabels(config)).withName(appName + "-ping").endMetadata().
                    withNewSpec().withPorts(new ServicePort().toBuilder().withProtocol("TCP").
                            withPort(8888).
                            withName("ping").
                            withTargetPort(v).build()).
                    withClusterIP("None").withPublishNotReadyAddresses().withIpFamilies("IPv4").
                    withInternalTrafficPolicy("Cluster").withClusterIPs("None").
                    withType("ClusterIP").withIpFamilyPolicy("SingleStack").
                    withSessionAffinity("None").withSelector(matchLabels).endSpec().build();
            if (osClient != null) {
                osClient.services().resource(pingService).createOr(NonDeletingOperation::update);
            }
            Utils.persistResource(getDeploymentDirectory(target), pingService, appName+"-ping-service.yaml");
        }
        Container container = new Container();
        container.setName(appName);
        String imageName = osClient == null ? (WILDFLY_GLOW_APP_IMAGE_REPOSITORY + ":" + appImageTag) : (appName + ":latest");
        container.setImage(imageName);
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

        Map<String, String> labels = createCommonLabels(config);
        labels.putAll(matchLabels);
        if (osClient != null) {
            writer.info("\nWaiting until the application " + deploymentKind + " is ready ...");
        }
        if (ha) {
            StatefulSet deployment = new StatefulSetBuilder().withNewMetadata().withLabels(labels).withName(appName).endMetadata().
                    withNewSpec().withReplicas(2).
                    withNewSelector().withMatchLabels(matchLabels).endSelector().
                    withNewTemplate().withNewMetadata().withLabels(labels).endMetadata().withNewSpec().
                    withContainers(container).withRestartPolicy("Always").
                    endSpec().endTemplate().withNewUpdateStrategy().withType("RollingUpdate").endUpdateStrategy().endSpec().build();
            if (osClient != null) {
                osClient.resources(StatefulSet.class).resource(deployment).createOr(NonDeletingOperation::update);
            }
            Utils.persistResource(getDeploymentDirectory(target), deployment, appName+ "-statefulset.yaml");
            if (osClient != null) {
                osClient.resources(StatefulSet.class).resource(deployment).waitUntilReady(5, TimeUnit.MINUTES);
            }
        } else {
            Deployment deployment = new DeploymentBuilder().withNewMetadata().withLabels(labels).withName(appName).endMetadata().
                    withNewSpec().withReplicas(1).
                    withNewSelector().withMatchLabels(matchLabels).endSelector().
                    withNewTemplate().withNewMetadata().withLabels(labels).endMetadata().withNewSpec().
                    withContainers(container).withRestartPolicy("Always").
                    endSpec().endTemplate().withNewStrategy().withType("RollingUpdate").endStrategy().endSpec().build();
            if (osClient != null) {
                osClient.resources(Deployment.class).resource(deployment).createOr(NonDeletingOperation::update);
            }
            Utils.persistResource(getDeploymentDirectory(target), deployment, appName + "-deployment.yaml");
            if (osClient != null) {
                osClient.resources(Deployment.class).resource(deployment).waitUntilReady(5, TimeUnit.MINUTES);
            }
        }
    }

    public static ConfigurationResolver.ResolvedEnvs getResolvedEnvs(Layer layer, Set<Env> input, Set<String> disabledDeployers, Set<String> enabledDeployers) throws Exception {
        ConfigurationResolver.ResolvedEnvs resolved = null;
        List<Deployer> deployers = getEnabledDeployers(disabledDeployers, enabledDeployers);
        for (Deployer d : deployers) {
            if (d.getSupportedLayers().contains(layer.getName())) {
                Set<Env> envs = d.getResolvedEnvs(input);
                if (envs != null && !envs.isEmpty()) {
                    resolved = new ConfigurationResolver.ResolvedEnvs("openshift/" + d.getName(), envs);
                    break;
                }
            }
        }
        return resolved;
    }

    public static String getPossibleDeployer(Set<Layer> layers, Set<String> disabledDeployers, Set<String> enabledDeployers) throws Exception {
        List<Deployer> deployers = getEnabledDeployers(disabledDeployers, enabledDeployers);
        for (Deployer d : deployers) {
            for (Layer l : layers) {
                if (d.getSupportedLayers().contains(l.getName())) {
                    return "openshift/" + d.getName();
                }
            }
        }
        return null;
    }

    private static List<Deployer> getEnabledDeployers(Set<String> disabledDeployers, Set<String> enabledDeployers) throws Exception {
        List<Deployer> existingDeployers = getAllDeployers(disabledDeployers, enabledDeployers);
        List<Deployer> deployers = new ArrayList<>();
        for (Deployer d : existingDeployers) {
            boolean isDisabled = isDisabled(d.getName(), disabledDeployers, enabledDeployers);
            if (!isDisabled) {
                deployers.add(d);
            }
        }
        return deployers;
    }

    private static List<Deployer> getAllDeployers(Set<String> disabledDeployers, Set<String> enabledDeployers) throws Exception {
        Map<String, Deployer> existingDeployers = new HashMap<>();
        List<Deployer> deployers = new ArrayList<>();
        for (Deployer d : ServiceLoader.load(Deployer.class)) {
            existingDeployers.put(d.getName(), d);
            deployers.add(d);
        }
        for (String disabled : disabledDeployers) {
            if (!"ALL".equals(disabled)) {
                if (!existingDeployers.containsKey(disabled)) {
                    throw new Exception("Invalid deployer to disable: " + disabled);
                }
            }
        }
        if (!enabledDeployers.isEmpty()) {
            if (!disabledDeployers.contains("ALL")) {
                throw new Exception("Enabled deployers is not empty although not ALL deployers are disabled.");
            }
        }
        for (String enabled : enabledDeployers) {
            if (!existingDeployers.containsKey(enabled)) {
                throw new Exception("Invalid deployer to enable: " + enabled);
            }
        }
        return deployers;
    }

    static final String generateValidName(String name) {
        name = name.toLowerCase();
        StringBuilder validName = new StringBuilder();
        char[] array = name.toCharArray();
        for (int i = 0; i < array.length; i++) {
            char c = array[i];
            // start with an alphabetic character
            if (i == 0) {
                if (c <= 97 || c >= 122) {
                    validName.append("app-");
                }
                validName.append(c);
            } else {
                // end with an alphabetic character
                if (i == array.length - 1) {
                    if ((c >= 48 && c <= 57) || (c >= 97 && c <= 122)) {
                        validName.append(c);
                    } else {
                        validName.append('0');
                    }
                } else {
                    // - allowed in the middle
                    if (c == '-') {
                        validName.append(c);
                    } else {
                        // a-z or 0-9
                        if ((c >= 48 && c <= 57) || (c >= 97 && c <= 122)) {
                            validName.append(c);
                        } else {
                            // Other character are replaced by -
                            validName.append('-');
                        }
                    }
                }
            }
        }
        return truncateValue(validName.toString());
    }

    private static String truncateValue(String val) {
        if (val.length() > 63) {
            val = val.substring(0, 63);
        }
        return val;
    }

    public static void deploy(List<Path> deployments,
            String applicationName,
            GlowMessageWriter writer,
            Path target,
            ScanResults scanResults,
            boolean ha,
            Map<String, String> extraEnv,
            Map<String, String> buildExtraEnv,
            Set<String> disabledDeployers,
            Set<String> enabledDeployers,
            Path initScript,
            Path cliScript,
            OpenShiftConfiguration config,
            MavenRepoManager mvnResolver,
            String stability,
            Map<String, String> serverImageBuildLabels, boolean dryRun, List<Channel> channels) throws Exception {
        Set<Layer> layers = scanResults.getDiscoveredLayers();
        Set<Layer> metadataOnlyLayers = scanResults.getMetadataOnlyLayers();
        Map<Layer, Set<Env>> requiredBuildTime = scanResults.getSuggestions().getBuildTimeRequiredConfigurations();
        String originalAppName = null;
        if (deployments != null && !deployments.isEmpty()) {
            Path deploymentsDir = getDeploymentsDirectory(target);
            Files.createDirectories(deploymentsDir);
            for (Path p : deployments) {
                Files.copy(p, deploymentsDir.resolve(p.getFileName()));
                int ext = p.getFileName().toString().lastIndexOf(".");
                if (applicationName == null || applicationName.isEmpty()) {
                    applicationName = p.getFileName().toString().substring(0, ext);
                    applicationName = generateValidName(applicationName);
                }
                if (originalAppName == null) {
                    originalAppName = p.getFileName().toString().substring(0, ext);
                }
            }
        } else {
            throw new Exception("No application to deploy to OpenShift");
        }
        Map<String, String> env = new HashMap<>();
        for (Set<Env> envs : scanResults.getSuggestions().getStronglySuggestedConfigurations().values()) {
            for (Env e : envs) {
                env.put(e.getName(), e.getDescription());
            }
        }
        Set<Layer> allLayers = new LinkedHashSet<>();
        allLayers.addAll(layers);
        allLayers.addAll(metadataOnlyLayers);
        Map<String, String> actualEnv = new TreeMap<>();
        Map<String, String> actualBuildEnv = new TreeMap<>();
        OpenShiftClient osClient = null;
        if(!dryRun) {
            osClient = new KubernetesClientBuilder().build().adapt(OpenShiftClient.class);
            writer.info("\nConnected to OpenShift cluster");
        }
        // First create the future route to the application, can be needed by deployers
        Route route = new RouteBuilder().withNewMetadata().withLabels(createCommonLabels(config)).withName(applicationName).
                endMetadata().withNewSpec().
                withTo(new RouteTargetReference("Service", applicationName, 100)).
                withTls(new TLSConfig().toBuilder().withTermination("edge").
                        withInsecureEdgeTerminationPolicy("Redirect").build()).endSpec().build();
        if (osClient != null) {
            osClient.routes().resource(route).createOr(NonDeletingOperation::update);
        }
        Utils.persistResource(getDeploymentDirectory(target), route, applicationName + "-route.yaml");
        String host = null;
        if(osClient != null) {
            host = osClient.routes().resource(route).get().getSpec().getHost();
        }
        // Done route creation

        List<Deployer> deployers = getAllDeployers(disabledDeployers, enabledDeployers);
        for (Deployer d : deployers) {
            boolean isDisabled = isDisabled(d.getName(), disabledDeployers, enabledDeployers);
            for (Layer l : allLayers) {
                if (d.getSupportedLayers().contains(l.getName())) {
                    if (!isDisabled) {
                        writer.info("\nFound deployer " + d.getName() + " for " + l.getName());
                    } else {
                        writer.warn("\nThe deployer " + d.getName() + " has been disabled");
                    }
                    actualEnv.putAll(isDisabled ? d.disabledDeploy(host, applicationName, host, env) : d.deploy(writer, target, osClient, env, host, applicationName, l.getName(), extraEnv, dryRun));
                    Set<Env> buildEnv = requiredBuildTime.get(l);
                    if (buildEnv != null) {
                        Set<String> names = new HashSet<>();
                        for (Env e : buildEnv) {
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
        if (!dryRun) {
            actualEnv.put("APPLICATION_ROUTE_HOST", host);
        }
        actualEnv.putAll(extraEnv);
        if (stability != null) {
            String val = actualEnv.get("SERVER_ARGS");
            String stabilityOption = "--stability=" + stability;
            boolean alreadySet = false;
            if (val == null) {
                val = stabilityOption;
            } else {
                if (val.contains("--stability")) {
                    alreadySet = true;
                } else {
                    val += " --stability" + stability;
                }
            }
            if (!alreadySet) {
                actualEnv.put("SERVER_ARGS", val);
            }
        }
        String deploymentKind = ha ? "StatefulSet" : "Deployment";
        if (!disabledDeployers.isEmpty()) {
            writer.warn("The following environment variables will be set in the " + applicationName + " " + deploymentKind + ". Make sure that the required env variables for the disabled deployer(s) have been set:\n");
        } else {
            writer.warn("The following environment variables will be set in the " + applicationName + " " + deploymentKind + ":\n");
        }
        if (ha) {
            actualEnv.put("JGROUPS_PING_PROTOCOL", "openshift.DNS_PING");
            actualEnv.put("OPENSHIFT_DNS_PING_SERVICE_PORT", "8888");
            actualEnv.put("OPENSHIFT_DNS_PING_SERVICE_NAME", applicationName + "-ping");
        }
        for (Entry<String, String> entry : actualEnv.entrySet()) {
            writer.warn(entry.getKey() + "=" + entry.getValue());
        }
        // Can be overriden by user
        actualBuildEnv.putAll(buildExtraEnv);
        Properties properties = new Properties();
        createBuild(writer, target, osClient, applicationName, initScript, cliScript, actualBuildEnv, config, serverImageBuildLabels, properties, channels);
        if (!dryRun) {
            writer.info("Deploying application image on OpenShift");
        }
        String appImageTag = null;
        if( osClient == null) {
            appImageTag = generateClientImageHash(deployments, target, buildExtraEnv, initScript, cliScript, channels);
            properties.setProperty("app-image-tag", appImageTag);
        }
        createAppDeployment(writer, target, osClient, applicationName, actualEnv, ha, config, deploymentKind, appImageTag);
        try(FileOutputStream out = new FileOutputStream(getDockerDirectory(target).resolve(IMAGE_PROPERTIES_FILE).toFile())) {
            properties.store(out, null);
        }
        if (dryRun) {
            writer.info("\nThe following generated content can be used to produce server and application container images and deploy to OpenShift.\n"+
                        "NOTE: Some editing is required. Check the following steps:\n");
            writer.info("* Directory '" + target.resolve("galleon") + "' contains the provisioning file used to provision a server.\n");
            writer.info("* Directory '" + getDockerServerDirectory(target) + "' contains the Dockerfile to build the server image. This Dockerfile expects that you first provision a server (e.g.: using Galleon CLI) in the directory '" + getDockerServerDirectory(target) + "' using the generated provisioning.xml.\n");
            writer.info("NOTE: The file '" + getDockerDirectory(target).resolve(IMAGE_PROPERTIES_FILE) + "' contains the server image tag that is expected by the application Dockerfile.\n");
            writer.info("* Directory '" + getDockerAppDirectory(target) + "' contains the Dockerfile to build the application image. Make sure to replace the '" + WILDFLY_GLOW_SERVER_IMAGE_REPOSITORY + "' string with the repository where the server image has been pushed (e.g.: 'quay.io/my-organization/wildfly-servers').\n");
            writer.info("NOTE: The file '" + getDockerDirectory(target).resolve(IMAGE_PROPERTIES_FILE) + "' contains the aplication image tag that is expected by the Deployment.\n");
            writer.info("* Directory '" + getResourcesDirectory(target) + "' contains the openshift resources. Make sure to replace the '" +WILDFLY_GLOW_APP_IMAGE_REPOSITORY +"' string with the repository where the application image has been pushed (e.g.: 'quay.io/my-organization/" + applicationName + "-image').\n");
        } else {
            writer.info("Application route: https://" + host + ( "ROOT.war".equals(applicationName) ? "" : "/" + originalAppName));
        }
        IoUtils.recursiveDelete(getTmpDirectory(target));
    }

    private static void createBuild(GlowMessageWriter writer,
            Path target,
            OpenShiftClient osClient,
            String appName,
            Path initScript,
            Path cliScript,
            Map<String, String> buildExtraEnv,
            OpenShiftConfiguration config,
            Map<String, String> serverImageBuildLabels, Properties properties, List<Channel>channels) throws Exception {
        if (osClient == null) {
            generateDockerServerImage(writer, target, buildExtraEnv, config);
            String serverImageTag = generateServerImageHash(target, buildExtraEnv, channels);
            properties.setProperty("server-image-tag", serverImageTag);
            doAppImageBuild(null, writer, target, osClient, appName, initScript, cliScript, config, serverImageTag);
        } else {
            String serverImageName = doServerImageBuild(writer, target, osClient, buildExtraEnv, config, serverImageBuildLabels, channels);
            doAppImageBuild(serverImageName, writer, target, osClient, appName, initScript, cliScript, config, null);
        }
    }

    private static boolean packageInitScript(boolean dryRun, Path initScript, Path cliScript, Path target) throws Exception {
        if (initScript != null || cliScript != null) {
            Path extensions = dryRun? getExtensionsDirectory(target) : target.resolve("extensions");
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

    private static boolean isDisabled(String name, Set<String> disabledDeployers, Set<String> enabledDeployers) {
        return !enabledDeployers.contains(name) && ( disabledDeployers.contains("ALL") || disabledDeployers.contains(name));
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

    private static Map<String, String> createCommonLabels(OpenShiftConfiguration osConfig) throws Exception {
        Map<String, String> labels = new HashMap<>();
        labels.put(truncateValue(osConfig.getLabelRadical()), "");
        return labels;
    }

    private static Map<String, String> createServerImageLabels(Path target, Path provisioning, OpenShiftConfiguration osConfig,
            Map<String, String> serverImageBuildLabels) throws Exception {
        GalleonBuilder provider = new GalleonBuilder();
        Path dir = getTmpDirectory(target).resolve("tmpHome");
        Map<String, String> labels = new HashMap<>();
        try {
            Files.createDirectories(dir);
            StringBuilder fps = new StringBuilder();
            try (Provisioning p = provider.newProvisioningBuilder(provisioning).setInstallationHome(dir).build()) {
                GalleonProvisioningConfig config = p.loadProvisioningConfig(provisioning);
                GalleonConfigurationWithLayers cl = config.getDefinedConfig(new ConfigId("standalone", "standalone.xml"));
                for (String s : cl.getIncludedLayers()) {
                    labels.put(truncateValue(osConfig.getLabelRadical() + ".layer." + s), "");
                }
                for (String s : cl.getExcludedLayers()) {
                    labels.put(truncateValue(osConfig.getLabelRadical() + ".excluded.layer." + s), "");
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
                    labels.put(truncateValue(osConfig.getLabelRadical() + "." + producerName), gfpc.getLocation().getBuild());
                }
            }

            for (Entry<String, String> entry : serverImageBuildLabels.entrySet()) {
                labels.put(truncateValue(entry.getKey()), truncateValue(entry.getValue()));
            }
        } finally {
            IoUtils.recursiveDelete(dir);
        }
        return labels;
    }

    private static Map<String, String> createDockerImageLabels(Path target, Path provisioning, OpenShiftConfiguration osConfig) throws Exception {
        GalleonBuilder provider = new GalleonBuilder();
        Path dir = getTmpDirectory(target).resolve("tmpHome");
        Map<String, String> labels = new HashMap<>();
        try {
            Files.createDirectories(dir);
            try (Provisioning p = provider.newProvisioningBuilder(provisioning).setInstallationHome(dir).build()) {
                GalleonProvisioningConfig config = p.loadProvisioningConfig(provisioning);
                GalleonConfigurationWithLayers cl = config.getDefinedConfig(new ConfigId("standalone", "standalone.xml"));
                for (String s : cl.getIncludedLayers()) {
                    String current = labels.get(osConfig.getLabelRadical() + ".layers");
                    labels.put(osConfig.getLabelRadical() + ".layers", (current == null ? "" : current + ",") +s);
                }
                for (String s : cl.getExcludedLayers()) {
                    String current = labels.get(osConfig.getLabelRadical() + ".excluded-layers");
                    labels.put(osConfig.getLabelRadical() + ".excluded-layers", (current == null ? "" : current+",") + s);
                }
                for (GalleonFeaturePackConfig gfpc : config.getFeaturePackDeps()) {
                    String producerName = gfpc.getLocation().getProducerName();
                    producerName = producerName.replaceAll("::zip", "");
                    int i = producerName.indexOf(":");
                    if (i > 0) {
                        producerName = producerName.substring(i + 1);
                    }
                    producerName = producerName.replaceAll(":", "_");
                    String version = gfpc.getLocation().getBuild();
                    if (version != null) {
                        producerName += "_" + version;
                    }
                    String current = labels.get(osConfig.getLabelRadical() + ".feature-packs");
                    labels.put(osConfig.getLabelRadical() + ".feature-packs",  (current == null ? "" : current+",") + producerName);
                }
            }
        } finally {
            IoUtils.recursiveDelete(dir);
        }
        return labels;
    }

    private static String generateServerImageHash(Path target,
            Map<String, String> buildExtraEnv, List<Channel> channels) throws IOException, NoSuchAlgorithmException {
        // To compute a hash we need build time env variables
        StringBuilder contentBuilder = new StringBuilder();
        Path provisioning = target.resolve("galleon").resolve("provisioning.xml");
        contentBuilder.append(Files.readString(provisioning, Charset.forName("UTF-8")));
        for (Entry<String, String> entry : buildExtraEnv.entrySet()) {
            contentBuilder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        if (channels != null && !channels.isEmpty()) {
            contentBuilder.append(ChannelMapper.toYaml(channels));
        }
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] encodedhash = digest.digest(contentBuilder.toString().getBytes());
        String key = bytesToHex(encodedhash);
        return key;
    }

    private static String generateClientImageHash(List<Path> deployments, Path target,
            Map<String, String> buildExtraEnv, Path initScript, Path cliScript, List<Channel> channels) throws IOException, NoSuchAlgorithmException {
        String server=generateServerImageHash(target, buildExtraEnv, channels);
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append(server);
        for (Path p : deployments) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] encodedhash = digest.digest(Files.readAllBytes(p));
            String key = bytesToHex(encodedhash);
            contentBuilder.append(key);
        }
        if (initScript != null) {
            contentBuilder.append(Files.readString(initScript, Charset.forName("UTF-8")));
        }
        if (cliScript != null) {
            contentBuilder.append(Files.readString(cliScript, Charset.forName("UTF-8")));
        }
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] encodedhash = digest.digest(contentBuilder.toString().getBytes());
        String key = bytesToHex(encodedhash);
        return key;
    }


    private static String doServerImageBuild(GlowMessageWriter writer, Path target, OpenShiftClient osClient,
            Map<String, String> buildExtraEnv,
            OpenShiftConfiguration config,
            Map<String, String> serverImageBuildLabels, List<Channel> channels) throws Exception {
        Path provisioning = target.resolve("galleon").resolve("provisioning.xml");
        String serverImageName = config.getServerImageNameRadical() + generateServerImageHash(target, buildExtraEnv, channels);
        ImageStream stream = new ImageStreamBuilder().withNewMetadata().withLabels(createCommonLabels(config)).withName(serverImageName).
                endMetadata().withNewSpec().withLookupPolicy(new ImageLookupPolicy(Boolean.TRUE)).endSpec().build();
        // check if it exists
        ImageStream existingStream = osClient.imageStreams().resource(stream).get();
        if (existingStream == null) {
            writer.info("\nBuilding server image (this can take up to few minutes)...");
            // zip deployment and provisioning.xml to be pushed to OpenShift
            Path file = getTmpDirectory(target).resolve("openshiftServer.zip");
            if (Files.exists(file)) {
                Files.delete(file);
            }
            // First do a build of the naked server
            Path stepOne = getTmpDirectory(target).resolve("step-one");
            Files.createDirectories(stepOne);
            IoUtils.copy(target.resolve("galleon"), stepOne.resolve("galleon"));
            ZipUtils.zip(stepOne, file);
            stream = stream.toBuilder().editOrNewMetadata().withLabels(createServerImageLabels(target, provisioning, config, serverImageBuildLabels)).endMetadata().build();
            osClient.imageStreams().resource(stream).createOr(NonDeletingOperation::update);
            Utils.persistResource(getBuildDirectory(target), stream, serverImageName + "-image-stream.yaml");
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
                    writer.warn(entry.getKey() + "=" + entry.getValue());
                    vars.add(new EnvVar().toBuilder().withName(entry.getKey()).withValue(val == null ? entry.getValue() : val).build());
                }
            }
            BuildConfig buildConfig = builder.
                    withNewMetadata().withLabels(createCommonLabels(config)).withName(serverImageName + "-build").endMetadata().withNewSpec().
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
            Utils.persistResource(getBuildDirectory(target), buildConfig, serverImageName + "-build-config.yaml");

            Build build = osClient.buildConfigs().withName(serverImageName + "-build").instantiateBinary().fromFile(file.toFile());
            BuildWatcher buildWatcher = new BuildWatcher(writer);
            try (Watch watcher = osClient.builds().withName(build.getMetadata().getName()).watch(buildWatcher)) {
                buildWatcher.await();
            }
            if (buildWatcher.isFailed()) {
                osClient.imageStreams().resource(stream).delete();
                throw new Exception("Server image build has failed. Check the OpenShift build log.");
            }
        }
        return serverImageName;
    }

    private static void generateDockerServerImage(GlowMessageWriter writer, Path target,
            Map<String, String> buildExtraEnv,
            OpenShiftConfiguration config) throws Exception {
        Path provisioning = target.resolve("galleon").resolve("provisioning.xml");
        Map<String, String> labels = createDockerImageLabels(target, provisioning, config);
        StringBuilder dockerFileBuilder = new StringBuilder();
        for(Entry<String, String> entry : labels.entrySet()) {
            dockerFileBuilder.append("LABEL ").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        if (!buildExtraEnv.isEmpty()) {
            writer.warn("\nThe following environment variables have been set in the server Dockerfile:\n");
            for (Map.Entry<String, String> entry : buildExtraEnv.entrySet()) {
                String val = buildExtraEnv.get(entry.getKey());
                writer.warn(entry.getKey() + "=" + entry.getValue());
                dockerFileBuilder.append("ENV ").append(entry.getKey()).append("=").append(val == null ? entry.getValue() : val).append("\n");
            }
        }
        dockerFileBuilder.append("FROM ").append(config.getRuntimeImage()).append("\n");
        dockerFileBuilder.append("COPY server $JBOSS_HOME\n");
        dockerFileBuilder.append("USER root\n");
        dockerFileBuilder.append("RUN chown -R jboss:root $JBOSS_HOME && chmod -R ug+rwX $JBOSS_HOME\n");
        dockerFileBuilder.append("USER jboss");
        Files.write(getDockerServerDirectory(target).resolve("Dockerfile"), dockerFileBuilder.toString().getBytes());
    }

    private static void doAppImageBuild(String serverImageName,
            GlowMessageWriter writer,
            Path target,
            OpenShiftClient osClient,
            String appName,
            Path initScript,
            Path cliScript,
            OpenShiftConfiguration config,
            String serverImageTag) throws Exception {
        // Now step 2
        // From the server image, do a docker build, copy the server and copy in it the deployments and init file.
        Path stepTwo = target;
        if (osClient != null) {
            stepTwo = getTmpDirectory(target).resolve("step-two");
            IoUtils.copy(getDeploymentsDirectory(target), stepTwo.resolve("deployments"));
        }
        StringBuilder dockerFileBuilder = new StringBuilder();
        if (osClient != null) {
            dockerFileBuilder.append("FROM ").append(config.getRuntimeImage()).append("\n");
            dockerFileBuilder.append("COPY --chown=jboss:root /server $JBOSS_HOME\n");
        } else {
            dockerFileBuilder.append("FROM ").append(WILDFLY_GLOW_SERVER_IMAGE_REPOSITORY).append(":").append(serverImageTag).append("\n");
        }
        dockerFileBuilder.append("COPY --chown=jboss:root deployments/* $JBOSS_HOME/standalone/deployments\n");
        if (packageInitScript(osClient == null, initScript, cliScript, stepTwo)) {
            dockerFileBuilder.append("COPY --chown=jboss:root extensions $JBOSS_HOME/extensions\n");
            dockerFileBuilder.append("RUN chmod ug+rwx $JBOSS_HOME/extensions/postconfigure.sh\n");
        }

        dockerFileBuilder.append("RUN chmod -R ug+rwX $JBOSS_HOME\n");
        Files.write(getDockerAppDirectory(target).resolve("Dockerfile"), dockerFileBuilder.toString().getBytes());
        Path file2 = null;
        if (osClient != null) {
            Path dockerFile = stepTwo.resolve("Dockerfile");
            Files.write(dockerFile, dockerFileBuilder.toString().getBytes());
            file2 = getTmpDirectory(target).resolve("openshiftApp.zip");
            if (Files.exists(file2)) {
                Files.delete(file2);
            }
            ZipUtils.zip(stepTwo, file2);
            writer.info("\nBuilding application image...");
        }
        ImageStream appStream = new ImageStreamBuilder().withNewMetadata().withLabels(createCommonLabels(config)).withName(appName).
                endMetadata().withNewSpec().withLookupPolicy(new ImageLookupPolicy(Boolean.TRUE)).endSpec().build();
        if (osClient != null) {
            osClient.imageStreams().resource(appStream).createOr(NonDeletingOperation::update);
            Utils.persistResource(getImagesDirectory(target), appStream, appName + "-image-stream.yaml");
        }
        BuildConfigBuilder builder = new BuildConfigBuilder();
        ObjectReference ref = new ObjectReference();
        ref.setKind("ImageStreamTag");
        ref.setName(serverImageName + ":latest");
        ImageSourcePath srcPath = new ImageSourcePathBuilder().withSourcePath("/opt/server").withDestinationDir(".").build();
        ImageSource imageSource = new ImageSourceBuilder().withFrom(ref).withPaths(srcPath).build();
        if (osClient != null) {
            BuildConfig buildConfig2 = builder.
                    withNewMetadata().withLabels(createCommonLabels(config)).withName(appName + "-build").endMetadata().withNewSpec().
                    withNewOutput().
                    withNewTo().
                    withKind("ImageStreamTag").
                    withName(appName + ":latest").endTo().
                    endOutput().
                    withNewSource().withType("Binary").withImages(imageSource).endSource().
                    withNewStrategy().withNewDockerStrategy().withNewFrom().withKind("DockerImage").
                    withName("quay.io/wildfly/wildfly-runtime:latest").endFrom().
                    withDockerfilePath("./Dockerfile").
                    endDockerStrategy().endStrategy().endSpec().build();
            osClient.buildConfigs().resource(buildConfig2).createOr(NonDeletingOperation::update);
            Utils.persistResource(getBuildDirectory(target), buildConfig2, appName + "-build-config.yaml");

            Build build = osClient.buildConfigs().withName(appName + "-build").instantiateBinary().fromFile(file2.toFile());
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
}
