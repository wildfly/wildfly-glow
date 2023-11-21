/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
package org.wildfly.glow;

import org.jboss.galleon.Constants;
import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.galleon.xml.ProvisioningXmlWriter;
import org.wildfly.glow.error.ErrorIdentificationSession;
import org.wildfly.glow.error.IdentifiedError;
import org.wildfly.glow.windup.WindupSupport;
import org.wildfly.plugins.bootablejar.ArtifactLog;
import org.wildfly.plugins.bootablejar.BootableJarSupport;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jboss.galleon.universe.FeaturePackLocation.ProducerSpec;
import static org.wildfly.glow.OutputFormat.BOOTABLE_JAR;
import static org.wildfly.glow.OutputFormat.DOCKER_IMAGE;

import static org.wildfly.glow.error.ErrorLevel.ERROR;

/**
 *
 * @author jdenise
 */
public class GlowSession {

    public static final Path OFFLINE_ZIP = Paths.get("glow-offline.zip");
    public static final Path OFFLINE_CONTENT = Paths.get("glow-offline-content");
    public static final Path OFFLINE_DOCS_DIR = OFFLINE_CONTENT.resolve("docs");
    public static final Path OFFLINE_FEATURE_PACKS_DIR = OFFLINE_CONTENT.resolve("feature-packs");
    public static final String STANDALONE_PROFILE = "standalone";

    private final MavenRepoManager resolver;
    private final Arguments arguments;
    private final GlowMessageWriter writer;

    private GlowSession(MavenRepoManager resolver, Arguments arguments, GlowMessageWriter writer) {
        this.resolver = resolver;
        this.arguments = arguments;
        this.writer = writer;
    }

    public static void goOffline(MavenRepoManager resolver, GoOfflineArguments arguments, GlowMessageWriter writer) throws Exception {
        if (!(arguments instanceof Arguments)) {
            throw new IllegalArgumentException("Please use the API to create the GoOfflineArguments instance");
        }
        GlowSession session = new GlowSession(resolver, (Arguments) arguments, writer);
        session.goOffline();
    }

    private void goOffline() throws Exception {
        if (Files.exists(OFFLINE_CONTENT)) {
            IoUtils.recursiveDelete(OFFLINE_CONTENT);
        }
        UniverseResolver universeResolver = UniverseResolver.builder().addArtifactResolver(resolver).build();

        try (ProvisioningLayout<FeaturePackLayout> layout = Utils.buildLayout(arguments.getExecutionContext(),
                arguments.getProvisioningXML(), arguments.getVersion(), writer, arguments.isTechPreview())) {
            Utils.exportOffline(universeResolver, layout);
        }
        Files.deleteIfExists(OFFLINE_ZIP);
        ZipUtils.zip(OFFLINE_CONTENT, OFFLINE_ZIP);
        IoUtils.recursiveDelete(OFFLINE_CONTENT);
    }

    public static ScanResults scan(MavenRepoManager resolver, ScanArguments arguments, GlowMessageWriter writer) throws Exception {
        if (!(arguments instanceof Arguments)) {
            throw new IllegalArgumentException("Please use the API to create the ScanArguments instance");
        }
        GlowSession session = new GlowSession(resolver, (Arguments) arguments, writer);
        return session.scan();
    }


    public ScanResults scan() throws Exception {

        Set<Layer> layers = new LinkedHashSet<>();
        Set<AddOn> possibleAddOns = new TreeSet<>();
        ErrorIdentificationSession errorSession = new ErrorIdentificationSession();

        UniverseResolver universeResolver = UniverseResolver.builder().addArtifactResolver(resolver).build();

        if (Files.exists(OFFLINE_ZIP)) {
            Files.createDirectories(OFFLINE_CONTENT);
            ZipUtils.unzip(OFFLINE_ZIP, OFFLINE_CONTENT);
        }

        ProvisioningLayoutFactory factory = ProvisioningLayoutFactory.getInstance();
        ProvisioningConfig pConfig = Utils.buildProvisioningConfig(factory, arguments.getExecutionContext(),
                arguments.getProvisioningXML(), arguments.getVersion(), writer, arguments.isTechPreview());
        try (ProvisioningLayout<FeaturePackLayout> layout = factory.newConfigLayout(pConfig)) {

            // BUILD MODEL
            Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies = new HashMap<>();
            Map<String, Layer> all
                    = Utils.getAllLayers(universeResolver, layout, fpDependencies);
            LayerMapping mapping = Utils.buildMapping(all, arguments.getExecutionProfiles());
            if (mapping.getDefaultBaseLayer() == null) {
                throw new IllegalArgumentException("No base layer found, WildFly Glow doesn't support WildFly server version. "
                        + "You must upgrade to a more recent WildFly version.");
            }
            // END BUILD MODEL

            // VALIDATE USER INPUTS
            for (String s : arguments.getUserEnabledAddOns()) {
                if (!mapping.getAddOns().containsKey(s)) {
                    throw new Exception("Unknown add-on " + s);
                }
            }
            Set<String> allProfiles = Utils.getAllProfiles(all);
            for (String p : arguments.getExecutionProfiles()) {
                if (!allProfiles.contains(p)) {
                    throw new Exception("Unknown profile " + p);
                }
            }
            if (arguments.isCloud() && OutputFormat.BOOTABLE_JAR.equals(arguments.getOutput())) {
                writer.info("NOTE. In a cloud context, Bootable JAR "
                        + "packaging is not taken into account, a server is installed in the image.");
            }
            // END VALIDATE USER INPUTS

            // DISCOVERY
            if (arguments.getBinaries() != null && !arguments.getBinaries().isEmpty()) {
                Path windup = WindupSupport.getWindupMapping();
                if (windup == null) {
                    for (Path d : arguments.getBinaries()) {
                        //System.out.println("SCAN " + d);
                        try (DeploymentScanner deploymentScanner = new DeploymentScanner(d, arguments.isVerbose(), arguments.getExcludeArchivesFromScan())) {
                            deploymentScanner.scan(mapping, layers, all, errorSession);
                        }
                    }
                } else {
                    for (Path d : arguments.getBinaries()) {
                        layers.addAll(WindupSupport.getLayers(all, windup, d));
                    }
                }
            }

            if (!arguments.getManualLayers().isEmpty()) {
                for (String manualLayer : arguments.getManualLayers()) {
                    Layer foundLayer = all.get(manualLayer);
                    if (foundLayer == null) {
                        throw new IllegalArgumentException("Manual layer '" + manualLayer + "' does not exist in the set of layers");
                    }
                    if (layers.contains(foundLayer)) {
                        throw new IllegalArgumentException("Layer '" + foundLayer + "' manually added has already been discovered in the deployment. It must be removed.");
                    }
                    layers.add(foundLayer);
                }
            }

            if (!arguments.getLayersForJndi().isEmpty()) {
                for (String layer : arguments.getLayersForJndi()) {
                    Layer foundLayer = all.get(layer);
                    if (foundLayer == null) {
                        throw new IllegalArgumentException("Layer '" + layer + "' added due to JNDI lookup does not exist in the set of layers");
                    }
                    if (layers.contains(foundLayer)) {
                        throw new IllegalArgumentException("Layer '" + layer + "' added due to JNDI lookup has already been discovered in the deployment. It must be removed.");
                    }
                    layers.add(foundLayer);
                }
            }

            Map<Layer, Set<Layer>> ret = findBaseLayer(mapping, all);
            Layer baseLayer = ret.keySet().iterator().next();
            // We create a set of all fine grain layers from the basic layers
            // Needed to identify layers that could be required to be excluded due to profile.
            Set<Layer> allBaseLayers = new TreeSet<>();
            allBaseLayers.addAll(layers);
            allBaseLayers.addAll(Utils.getTransitiveDependencies(all, baseLayer, new HashSet<>()));
            for (Layer s : layers) {
                allBaseLayers.addAll(Utils.getTransitiveDependencies(all, s, new HashSet<>()));
            }

            // Force layers inclusion when add-on explicitly enabled.
            for (AddOn addOn : mapping.getAddOns().values()) {
                boolean enabled = arguments.getUserEnabledAddOns().contains(addOn.getName());
                if (enabled) {
                    for (Layer l : addOn.getLayers()) {
                        if (!l.isBanned()) {
                            layers.add(l);
                            allBaseLayers.add(l);
                            Set<Layer> dependencies = all.get(l.getName()).getDependencies();
                            layers.addAll(dependencies);
                            allBaseLayers.addAll(dependencies);
                        }
                    }
                }
            }
            // Handle Layers bound to addOn inclusion or suggest addOns
            for (String addOnName : mapping.getAddOns().keySet()) {
                AddOn addOn = mapping.getAddOns().get(addOnName);
                boolean enabled = arguments.getUserEnabledAddOns().contains(addOn.getName());
                for (Layer layer : addOn.getLayersThatExpectAllDependencies()) {
                    if (!allBaseLayers.contains(layer) && !layer.isBanned()) {
                        if (allBaseLayers.containsAll(layer.getDependencies())) {
                            if (enabled) {
                                layers.add(layer);
                                allBaseLayers.add(layer);
                            } else {
                                possibleAddOns.add(addOn);
                            }
                        }
                    }
                }
                for (Layer layer : addOn.getLayersThatExpectSomeDependencies().keySet()) {
                    if (!allBaseLayers.contains(layer) && !layer.isBanned()) {
                        Set<Layer> expectDeps = addOn.getLayersThatExpectSomeDependencies().get(layer);
                        if (expectDeps != null && allBaseLayers.containsAll(expectDeps)) {
                            if (enabled) {
                                layers.add(layer);
                                allBaseLayers.add(layer);
                            } else {
                                possibleAddOns.add(addOn);
                            }
                        }
                    }
                }
                for (Layer layer : addOn.getLayersAlwaysIncluded()) {
                    if (enabled && !layer.isBanned()) {
                        layers.add(layer);
                        allBaseLayers.add(layer);
                    } else {
                        possibleAddOns.add(addOn);
                    }
                }
            }

            // Add Layers that are included if all there dependencies have been included
            for (Layer layer : mapping.getLayersIncludedIfAllDeps()) {
                if (!allBaseLayers.contains(layer) && !layer.isBanned()) {
                    if (allBaseLayers.containsAll(layer.getDependencies())) {
                        layers.add(layer);
                        allBaseLayers.add(layer);
                    }
                }
            }
            // Add Layers that are included if some of there dependencies have been included
            for (Layer layer : mapping.getLayersIncludedIfSomeDeps().keySet()) {
                if (!allBaseLayers.contains(layer) && !layer.isBanned()) {
                    if (allBaseLayers.containsAll(mapping.getLayersIncludedIfSomeDeps().get(layer))) {
                        layers.add(layer);
                        allBaseLayers.add(layer);
                    }
                }
            }
            // Add layers that are included at the FP level (model.xml).
            for (Layer layer : all.values()) {
                if (layer.isIsAutomaticInjection() && !layer.isBanned()) {
                    allBaseLayers.add(layer);
                }
            }
            // END DISCOVERY

            // PROFILES
            // If no profile enabled, suggest profiles for layer that could be profiled
            Set<String> possibleProfiles = new TreeSet<>();
            if (arguments.getExecutionProfiles().isEmpty()) {
                for (Layer l : allBaseLayers) {
                    for (String p : mapping.getAllProfilesLayers().keySet()) {
                        Set<Layer> layersInProfile = mapping.getAllProfilesLayers().get(p);
                        if (layersInProfile.contains(l)) {
                            possibleProfiles.add(p);
                            break;
                        }
                    }
                }
            }
            Set<Layer> profileLayers = new TreeSet<>();
            Set<Layer> excludedLayers = new TreeSet<>();
            for (Layer l : allBaseLayers) {
                Layer toInclude = mapping.getActiveProfilesLayers().get(l.getName());
                if (toInclude != null) {
                    profileLayers.add(toInclude);
                    excludedLayers.add(l);
                }
            }
            // Order is important. Decorators must be added first.
            // Then layers bound to a profile
            Set<Layer> decorators = new LinkedHashSet<>();
            for (Layer s : layers) {
                if (!ret.get(baseLayer).contains(s)) {
                    decorators.add(s);
                }
            }
            // Add profile based layer to the decorators
            // That is the way wildfly design its layers today
            decorators.addAll(profileLayers);

            // If we have layers as decorator that we also need to exclude, just remove them from decorators and excluded set
            Iterator<Layer> decoratorsIt = decorators.iterator();
            while (decoratorsIt.hasNext()) {
                Layer l = decoratorsIt.next();
                if (excludedLayers.contains(l)) {
                    //System.out.println("This decorator is also excluded, just not add it. " + l);
                    excludedLayers.remove(l);
                    decoratorsIt.remove();
                }
            }
            // END PROFILES

            // In case some layers fixed the found initial errors
            //errorSession.refreshErrors(allBaseLayers);
            // ADD-ON
            // Findout the set of enabled add-ons
            Set<AddOn> allEnabledAddOns = new TreeSet<>();
            for (Layer layer : allBaseLayers) {
                if (layer.getAddOn() != null) {
                    allEnabledAddOns.add(layer.getAddOn());
                }
            }
            // Fix addOns
            Map<AddOn, String> disabledAddOns = new TreeMap<>();
            fixAddOns(errorSession, layers, mapping, allEnabledAddOns, possibleAddOns, disabledAddOns, arguments);
            // END ADD-ON

            // DECORATORS CLEANUP
            // Filter out decorator layers that are dependencies inside found layers.
            Set<Layer> filteredLayers = new LinkedHashSet<>();
            filteredLayers.addAll(decorators);
            Iterator<Layer> it = filteredLayers.iterator();
            while (it.hasNext()) {
                Layer l = it.next();
                for (Layer s : decorators) {
                    if (l.equals(s)) {
                        continue;
                    }
                    Set<Layer> deps = Utils.getTransitiveDependencies(all, s, new HashSet<>());
                    if (deps.contains(l)) {
                        it.remove();
                        break;
                    }
                }
            }
            decorators = filteredLayers;
            // END DECORATORS CLEANUP

            // Compute the possible configurations
            // strongly suggested means that the layer has been discovered directly in the deployment
            // and that its required configuration should be applied.
            Map<Layer, Set<Env>> suggestedConfigurations = new TreeMap<>();
            Map<Layer, Set<Env>> stronglySuggestedConfigurations = new TreeMap<>();
            for (Layer l : allBaseLayers) {
                if (!excludedLayers.contains(l)) {
                    if (!l.getConfiguration().isEmpty()) {
                        if (layers.contains(l)) {
                            Set<Env> requiredSet = new TreeSet<>();
                            Set<Env> notRequiredSet = new TreeSet<>();
                            for (String c : l.getConfiguration()) {
                                URI uri = new URI(c);
                                Set<Env> envs = EnvHandler.retrieveEnv(uri);
                                for (Env e : envs) {
                                    if (e.isRequired()) {
                                        requiredSet.add(e);
                                    } else {
                                        notRequiredSet.add(e);
                                    }
                                }
                            }
                            if (!requiredSet.isEmpty()) {
                                stronglySuggestedConfigurations.put(l, requiredSet);
                            }
                            if (!notRequiredSet.isEmpty()) {
                                suggestedConfigurations.put(l, notRequiredSet);
                            }
                        } else {
                            Set<Env> envs = new TreeSet<>();
                            for (String c : l.getConfiguration()) {
                                envs.addAll(EnvHandler.retrieveEnv(new URI(c)));
                            }
                            suggestedConfigurations.put(l, envs);
                        }
                    }
                }
            }

            // Remove layers from output that are metadata-only
            for (Layer metadataOnly : mapping.getMetadataOnly()) {
                if (decorators.contains(metadataOnly)) {
                    decorators.remove(metadataOnly);
                }
                if (layers.contains(metadataOnly)) {
                    layers.remove(metadataOnly);
                }
            }
            // END cleanup

            Map<Layer, Set<Env>> stronglySuggestConfigFixes = errorSession.refreshErrors(allBaseLayers, mapping, allEnabledAddOns);
            for(Layer l : stronglySuggestConfigFixes.keySet()) {
                Set<Env> envs = stronglySuggestedConfigurations.get(l);
                if(envs == null) {
                    envs = new TreeSet<>();
                    stronglySuggestedConfigurations.put(l, envs);
                }
                envs.addAll(stronglySuggestConfigFixes.get(l));
            }
            // Identify the active feature-packs.
            ProvisioningConfig activeConfig = buildProvisioningConfig(pConfig, layout,
                    universeResolver, allBaseLayers, baseLayer, decorators, excludedLayers, fpDependencies, arguments.getConfigName());

            Suggestions suggestions = new Suggestions(suggestedConfigurations,
                    stronglySuggestedConfigurations, possibleAddOns, possibleProfiles);
            ScanResults scanResults = new ScanResults(
                    this,
                    layers,
                    excludedLayers,
                    baseLayer,
                    decorators,
                    activeConfig,
                    allEnabledAddOns,
                    disabledAddOns,
                    suggestions,
                    errorSession);

            return scanResults;
        } finally {
            IoUtils.recursiveDelete(OFFLINE_CONTENT);
        }
    }

    OutputContent outputConfig(ScanResults scanResults, Path target, String dockerImageName) throws Exception {
        if (arguments.getOutput() == null) {
            throw new IllegalStateException("No output format set");
        }
        Map<OutputContent.OutputFile, Path> files = new HashMap<>();
        if (!OutputFormat.PROVISIONING_XML.equals(arguments.getOutput())) {
            if (scanResults.getErrorSession().hasErrors()) {
                writer.warn("You are provisioning a server although some errors still exist. You should first fix them.");
            }
            Path generatedArtifact = provisionServer(arguments.getBinaries(),
                    scanResults.getProvisioningConfig(), resolver, arguments.getOutput(),
                    arguments.isCloud(), target);
            switch (arguments.getOutput()) {
                case DOCKER_IMAGE: {
                    // generate docker image
                    dockerImageName = dockerImageName == null ? DockerSupport.getImageName(generatedArtifact.getFileName().toString()) : dockerImageName;
                    Path origDockerFile = DockerSupport.buildApplicationImage(dockerImageName, generatedArtifact, arguments, writer);
                    IoUtils.recursiveDelete(generatedArtifact);
                    Files.createDirectories(target);
                    Path dockerFile = target.resolve("Dockerfile");
                    Files.copy(origDockerFile, dockerFile);
                    Files.delete(origDockerFile);
                    files.put(OutputContent.OutputFile.DOCKER_FILE,dockerFile.toAbsolutePath());
                    break;
                }
                case BOOTABLE_JAR: {
                    files.put(OutputContent.OutputFile.BOOTABLE_JAR_FILE, generatedArtifact.toAbsolutePath());
                    break;
                }
                case SERVER: {
                    files.put(OutputContent.OutputFile.SERVER_DIR, generatedArtifact.toAbsolutePath());
                    break;
                }
            }
        } else {
            Files.createDirectories(target);
            Path prov = target.resolve("provisioning.xml");
            try (FileWriter fileWriter = new FileWriter(prov.toFile())) {
                ProvisioningXmlWriter.getInstance().write(scanResults.getProvisioningConfig(), fileWriter);
            }
            files.put(OutputContent.OutputFile.PROVISIONING_XML_FILE, prov.toAbsolutePath());
        }
        StringBuilder envFileContent = new StringBuilder();
        if (!scanResults.getSuggestions().getStronglySuggestedConfigurations().isEmpty() ||
                (arguments.isSuggest() && !scanResults.getSuggestions().getSuggestedConfigurations().isEmpty())) {
            envFileContent.append("Environment variables to set. ").append(System.lineSeparator());
        }
        if (!scanResults.getSuggestions().getStronglySuggestedConfigurations().isEmpty()) {
            envFileContent.append(buildEnvs(scanResults.getSuggestions().
                    getStronglySuggestedConfigurations(), true)).append(System.lineSeparator());
        }
        if (arguments.isSuggest() && !scanResults.getSuggestions().getSuggestedConfigurations().isEmpty()) {
            envFileContent.append(buildEnvs(scanResults.getSuggestions().
                    getSuggestedConfigurations(), false)).append(System.lineSeparator());
        }
        if (envFileContent.length() != 0) {
            if (!Files.exists(target)) {
               Files.createDirectories(target);
            }
            Path p = target.resolve("configuration.env");
            Files.write(p, envFileContent.toString().getBytes());
            files.put(OutputContent.OutputFile.ENV_FILE, p.toAbsolutePath());
        }
        return new OutputContent(files, dockerImageName);
    }

    private String buildEnvs(Map<Layer, Set<Env>> map, boolean isRequired) {
        StringBuilder envFileContent = new StringBuilder();
        for (Entry<Layer, Set<Env>> entry : map.entrySet()) {
            envFileContent.append(System.lineSeparator()).append(System.lineSeparator()).
                    append(isRequired ? "# Env required by the layer " : "# Env suggested by the layer ").
                    append(entry.getKey().getName()).append(System.lineSeparator());
            for (Env env : entry.getValue()) {
                envFileContent.append("# ").append(env.getName()).append("=").append(env.getDescription()).append(System.lineSeparator());
            }
        }
        return envFileContent.toString();
    }

    void outputInformation(ScanResultsPrinter scanResultsPrinter, ScanResults scanResults) throws Exception {
        scanResultsPrinter.print(arguments, scanResults);
    }

    void outputCompactInformation(ScanResultsPrinter scanResultsPrinter, ScanResults scanResults) throws Exception {
        scanResultsPrinter.printCompact(arguments, scanResults);
    }

    String getCompactInformation(ScanResultsPrinter scanResultsPrinter, ScanResults scanResults) throws Exception {
        return scanResultsPrinter.getCompactInformation(arguments, scanResults);
    }

    private Path provisionServer(List<Path> binaries, ProvisioningConfig activeConfig,
            MavenRepoManager resolver, OutputFormat format, boolean isCloud, Path target) throws Exception {
        Path tmpDir = null;
        Path originalTarget = target;
        if (OutputFormat.BOOTABLE_JAR.equals(format)) {
            // We create a tmp directory in which the server is provisioned
            tmpDir = Files.createTempDirectory("wildfly-glow-bootable");
            target = tmpDir;
        } else {
            IoUtils.recursiveDelete(target);
        }
        Path ret = target;
        try {
            Utils.provisionServer(activeConfig, target.toAbsolutePath(), resolver, writer);

            if (!binaries.isEmpty()) {
                for (Path binary : binaries) {
                    Path deploymentTarget = target.resolve("standalone").
                            resolve("deployments").resolve(binary.getFileName().toString());
                    writer.info("Copy " + binary + " to " + deploymentTarget);
                    Files.copy(binary, deploymentTarget);
                }
            }
            if (OutputFormat.BOOTABLE_JAR.equals(format)) {
                String bootableJarName = "";
                if (!binaries.isEmpty()) {
                    for (Path binary : binaries) {
                        int i = binary.getFileName().toString().lastIndexOf(".");
                        bootableJarName = bootableJarName + binary.getFileName().toString().substring(0, i);
                    }
                } else {
                    bootableJarName = "hollow";
                }
                String vers = arguments.getVersion() == null ? FeaturePacks.getLatestVersion() : arguments.getVersion();
                Path targetJarFile = originalTarget.toAbsolutePath().resolve(bootableJarName + "-" + vers + "-" + BootableJarSupport.BOOTABLE_SUFFIX + ".jar");
                ret = targetJarFile;
                Files.deleteIfExists(targetJarFile);
                BootableJarSupport.packageBootableJar(targetJarFile, originalTarget.toAbsolutePath(),
                        activeConfig, tmpDir.toAbsolutePath(),
                        resolver,
                        new MessageWriter() {
                    @Override
                    public void verbose(Throwable cause, CharSequence message) {
                        if (writer.isVerbose()) {
                            writer.trace(message);
                        }
                    }

                    @Override
                    public void print(Throwable cause, CharSequence message) {
                        writer.info(message);
                    }

                    @Override
                    public void error(Throwable cause, CharSequence message) {
                        writer.error(message);
                    }

                    @Override
                    public boolean isVerboseEnabled() {
                        return writer.isVerbose();
                    }

                    @Override
                    public void close() throws Exception {
                    }

                }, new ArtifactLog() {
                    @Override
                    public void info(FeaturePackLocation.FPID fpid, MavenArtifact a) {
                        writer.info("Found artifact " + a);
                    }

                    @Override
                    public void debug(FeaturePackLocation.FPID fpid, MavenArtifact a) {
                        if (writer.isVerbose()) {
                            writer.trace("Found artifact " + a);
                        }
                    }
                }, null);
            }
        } finally {
            if (tmpDir != null) {
                IoUtils.recursiveDelete(tmpDir);
            }
        }
        return ret;
    }

    private static void fixAddOns(ErrorIdentificationSession errorSession,
            Set<Layer> layers,
            LayerMapping mapping,
            Set<AddOn> allEnabledAddOns,
            Set<AddOn> possibleAddOns,
            Map<AddOn, String> disabledAddOns,
            Arguments arguments) throws URISyntaxException, IOException {
        // Remove from the possible AddOns the addOns family that are complete
        Set<String> familyOfAddOnsComplete = new TreeSet<>();
        Map<String, Set<AddOn>> membersInFamily = new HashMap<>();
        Map<String, Set<AddOn>> defaultMembersInFamily = new HashMap<>();
        for (AddOn addOn : allEnabledAddOns) {
            if (addOn.isDefault()) {
                Set<AddOn> members = defaultMembersInFamily.get(addOn.getFamily());
                if (members == null) {
                    members = new TreeSet<>();
                    defaultMembersInFamily.put(addOn.getFamily(), members);
                }
                members.add(addOn);
            } else {
                Set<AddOn> members = membersInFamily.get(addOn.getFamily());
                if (members == null) {
                    members = new TreeSet<>();
                    membersInFamily.put(addOn.getFamily(), members);
                }
                members.add(addOn);
            }
        }
        Set<String> treatedFamily = new TreeSet<>();
        Map<String, Set<AddOn>> addOnsThatFixsCardinality = new HashMap<>();
        for (AddOn addOn : allEnabledAddOns) {
            Integer i = mapping.getAddOnsCardinalityInFamily().get(addOn.getFamily());
            if (i != null) {
                Set<AddOn> members = membersInFamily.get(addOn.getFamily());
                if (members.size() == i) {
                    familyOfAddOnsComplete.add(addOn.getFamily());
                    Set<AddOn> ao = addOnsThatFixsCardinality.get(addOn.getFamily());
                    if (ao == null) {
                        ao = new TreeSet<>();
                        addOnsThatFixsCardinality.put(addOn.getFamily(), ao);
                    }
                    ao.add(addOn);
                } else {
                    if (members.size() > i) {
                        if (!treatedFamily.contains(addOn.getFamily())) {
                            treatedFamily.add(addOn.getFamily());
                            boolean isError = true;
                            for (AddOn ao : members) {
                                for (AddOn ao2 : members) {
                                    if (!ao.equals(ao2)) {
                                        for (Layer l : ao2.getLayers()) {
                                            if (l.getDependencies().containsAll(ao.getLayers())) {
                                                isError = false;
                                                break;
                                            }
                                        }
                                    }
                                    if (!isError) {
                                        break;
                                    }
                                }
                                if (!isError) {
                                    break;
                                }
                            }
                            if (isError) {
                                errorSession.addError(new IdentifiedError("add-on cardinality violation", "add-ons family "
                                        + addOn.getFamily() + " accepts " + i + " members although " + members.size()
                                        + " are configured : " + members, ERROR));
                            }
                        }
                    }
                }
            }
        }
        Set<String> treatedDefaultFamily = new TreeSet<>();
        Set<String> disabledAddOnsDueToDefault = new TreeSet<>();
        for (AddOn addOn : allEnabledAddOns) {
            if (addOn.isDefault()) {
                Integer i = mapping.getAddOnsCardinalityInDefaultFamily().get(addOn.getFamily());
                if (i != null) {
                    Set<AddOn> members = defaultMembersInFamily.get(addOn.getFamily());
                    if (members.size() > i) {
                        if (!treatedDefaultFamily.contains(addOn.getFamily())) {
                            treatedDefaultFamily.add(addOn.getFamily());
                            errorSession.addError(new IdentifiedError("add-on cardinality violation", "default in add-ons family "
                                    + addOn.getFamily() + " accepts " + i + " members although " + members.size()
                                    + " are configured : " + members, ERROR));
                        }
                    }
                }
                //Disable associated addOn
                disabledAddOnsDueToDefault.add(addOn.getAssociatedNonDefault());
            }
        }

        Iterator<AddOn> iterator = possibleAddOns.iterator();
        while (iterator.hasNext()) {
            AddOn addOn = iterator.next();
            if (allEnabledAddOns.contains(addOn)) {
                iterator.remove();
            } else {
                if (familyOfAddOnsComplete.contains(addOn.getFamily())) {
                    disabledAddOns.put(addOn, "add-on family " + addOn.getFamily() + " is complete");
                    iterator.remove();
                } else {
                    // Never advise defaults
                    if (addOn.isDefault()) {
                        iterator.remove();
                    } else {
                        if (disabledAddOnsDueToDefault.contains(addOn.getName())) {
                            disabledAddOns.put(addOn, "default add-on " + addOn.getName() + ":default" + " is enabled");
                            iterator.remove();
                        }
                    }
                }
            }
        }
        // handle the case of layers expecting addOn
        for (Layer l : layers) {
            String familyExpected = l.getExpectFamily();
            if (familyExpected != null) {
                boolean found = false;
                for (AddOn addOn : allEnabledAddOns) {
                    if (addOn.getFamily().equals(familyExpected)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Set<AddOn> members = mapping.getAddOnFamilyMembers().get(familyExpected);
                    IdentifiedError err = new IdentifiedError("expected add-on not found",
                            "an add-on of the " + familyExpected + " family is expected by the " + l + " layer", ERROR);
                    err.getPossibleAddons().addAll(members);
                    errorSession.addError(err);
                }
            }
        }
        for (String family : addOnsThatFixsCardinality.keySet()) {
            Set<AddOn> addons = addOnsThatFixsCardinality.get(family);
            StringBuilder builder = new StringBuilder();
            for (AddOn ao : addons) {
                if (arguments.getUserEnabledAddOns().contains(ao.getName())) {
                    builder.append(Utils.getAddOnFix(ao, null));
                }
            }
            if (builder.length() != 0) {
                IdentifiedError err = new IdentifiedError("expected add-on not found", "expected add-on not found in family "
                        + family, ERROR);
                err.setFixed(builder.toString());
                errorSession.addError(err);
            }
        }
    }

    private static ProvisioningConfig buildProvisioningConfig(ProvisioningConfig input, ProvisioningLayout<FeaturePackLayout> layout,
            UniverseResolver universeResolver, Set<Layer> allBaseLayers,
            Layer baseLayer,
            Set<Layer> decorators,
            Set<Layer> excludedLayers,
            Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies,
            String configName) throws ProvisioningException {
        ProvisioningConfig.Builder activeConfig = ProvisioningConfig.builder();
        Map<FPID, FeaturePackConfig> map = new HashMap<>();
        Map<FPID, FPID> universeToGav = new HashMap<>();
        for (FeaturePackConfig cfg : input.getFeaturePackDeps()) {
            FeaturePackLocation.FPID loc = null;
            for (FeaturePackLocation.FPID f : fpDependencies.keySet()) {
                if (cfg.getLocation().getProducer().equals(f.getProducer())) {
                    loc = f;
                    break;
                }
            }
            if(loc == null) {
                throw new ProvisioningException("Input fp "+ cfg.getLocation() + " not found in resolved feature-packs " + fpDependencies.keySet());
            }
            map.put(loc, cfg);
            universeToGav.put(cfg.getLocation().getFPID(), loc);
        }
        Map<ProducerSpec, FeaturePackLocation.FPID> tmpFps = new HashMap<>();
        FeaturePackLocation.FPID baseFPID = universeToGav.get(input.getFeaturePackDeps().iterator().next().getLocation().getFPID());
        tmpFps.put(baseFPID.getProducer(), baseFPID);
        for (Layer l : allBaseLayers) {
            for(FPID fpid : l.getFeaturePacks()) {
                tmpFps.put(fpid.getProducer(), fpid);
            }
        }
        Set<FeaturePackLocation.FPID> activeFeaturePacks = new LinkedHashSet<>();
        // Order follow the one from the input
        for(FeaturePackConfig cfg : input.getFeaturePackDeps()) {
            FeaturePackLocation.FPID fpid = tmpFps.get(cfg.getLocation().getProducer());
            if (fpid != null) {
                activeFeaturePacks.add(fpid);
            }
        }
        // Remove dependencies that are not Main FP...
        //System.out.println("Active FP " + activeFeaturePacks);
        Set<FeaturePackLocation.FPID> toRemove = new HashSet<>();
        for (FeaturePackLocation.FPID fpid : activeFeaturePacks) {
            Set<FeaturePackLocation.ProducerSpec> deps = fpDependencies.get(fpid);
            //System.out.println(fpid + "DEPENDENCIES " + deps);
            if (deps != null) {
                for (FeaturePackLocation.ProducerSpec spec : deps) {
                    for (FeaturePackLocation.FPID af : activeFeaturePacks) {
                        if (spec.equals(af.getProducer()) && !af.getProducer().getName().equals(baseFPID.getProducer().getName())) {
                            //System.out.println("To remove..." + af);
                            toRemove.add(af);
                        }
                    }
                }
            }
        }
        activeFeaturePacks.removeAll(toRemove);
        for (FeaturePackLocation.FPID fpid : activeFeaturePacks) {
            // The input config included packages is to cover some wildfly tests corner cases.
            FeaturePackConfig inCfg = map.get(fpid);
            FeaturePackConfig.Builder cfgBuilder = FeaturePackConfig.builder(fpid.getLocation()).
                    setInheritConfigs(false).setInheritPackages(false);
            cfgBuilder.includeAllPackages(inCfg.getIncludedPackages());
            activeConfig.addFeaturePackDep(cfgBuilder.build());
        }
        ConfigModel.Builder builder = ConfigModel.builder("standalone", configName);
        builder.includeLayer(baseLayer.getName());
        for (Layer decorator : decorators) {
            builder.includeLayer(decorator.getName());
        }
        for (Layer ex : excludedLayers) {
            builder.excludeLayer(ex.getName());
        }
        activeConfig.addConfig(builder.build());
        activeConfig.addOption(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
        activeConfig.addOption("jboss-fork-embedded", "true");
        return activeConfig.build();
    }

    // Only use the default base layer
    private static Map<Layer, Set<Layer>> findBaseLayer(LayerMapping mapping, Map<String, Layer> all) {
        //Identify servers
        Map<Layer, Set<Layer>> roots = new HashMap<>();
        for (String k : all.keySet()) {
            Layer layer = all.get(k);
            String kind = layer.getProperties().get(LayerMetadata.KIND);
            if (kind != null && (kind.equals("base-layer") || kind.equals("default-base-layer"))) {
                roots.put(layer, Utils.getTransitiveDependencies(all, layer, new HashSet<>()));
            }
        }
        Map<Layer, Set<Layer>> ret = new HashMap<>();
        ret.put(mapping.getDefaultBaseLayer(), roots.get(mapping.getDefaultBaseLayer()));
        return ret;
    }

}
