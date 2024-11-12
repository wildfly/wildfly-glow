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
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.wildfly.glow.error.ErrorIdentificationSession;
import org.wildfly.glow.error.IdentifiedError;
import org.wildfly.glow.windup.WindupSupport;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.universe.FeaturePackLocation.ProducerSpec;
import static org.wildfly.glow.OutputFormat.BOOTABLE_JAR;
import static org.wildfly.glow.OutputFormat.DOCKER_IMAGE;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.GalleonFeaturePackRuntime;
import org.jboss.galleon.api.GalleonFeatureParamSpec;
import org.jboss.galleon.api.GalleonFeatureSpec;
import org.jboss.galleon.api.GalleonPackageRuntime;
import org.jboss.galleon.api.GalleonProvisioningRuntime;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayers;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayersBuilder;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import static org.wildfly.glow.error.ErrorLevel.ERROR;
import org.wildfly.plugin.tools.bootablejar.BootableJarSupport;

/**
 *
 * @author jdenise
 */
public class GlowSession {
    public static final Path OFFLINE_ZIP = Paths.get("glow-offline.zip");
    public static final Path OFFLINE_CONTENT = Paths.get("glow-offline-content");
    public static final Path OFFLINE_DOCS_DIR = OFFLINE_CONTENT.resolve("docs");
    public static final Path OFFLINE_FEATURE_PACKS_DIR = OFFLINE_CONTENT.resolve("feature-packs");
    public static final Path OFFLINE_FEATURE_PACK_DEPENDENCIES_DIR = OFFLINE_CONTENT.resolve("feature-pack-dependencies");
    public static final String STANDALONE_PROFILE = "standalone";
    private static final String GALLEON_DISABLE_CAPABILITIES_CHECK_PROPERTY = "org.jboss.galleon.internal.ignore.capability.providers";
    private final MavenRepoManager resolver;
    private final Arguments arguments;
    private final GlowMessageWriter writer;
    private final List<Channel> channels = new ArrayList<>();
    private GlowSession(MavenRepoManager resolver, Arguments arguments, GlowMessageWriter writer) throws Exception {
        this.arguments = arguments;
        this.writer = writer;
        MavenRepoManager repoManager = resolver;
        if (!Files.exists(OFFLINE_ZIP)) {
            if (arguments.getChannels() != null) {
                channels.addAll(arguments.getChannels());
            }
        }
        this.resolver = repoManager;
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
        GalleonBuilder provider = new GalleonBuilder();
        provider.addArtifactResolver(resolver);
        Provisioning provisioning = null;
        try {
            GalleonProvisioningConfig config = Utils.buildOfflineProvisioningConfig(provider, writer);
            if (config == null) {
                Path provisioningXML = arguments.getProvisioningXML();
                if (provisioningXML == null) {
                    provisioningXML = FeaturePacks.getFeaturePacks(arguments.getVersion(), arguments.getExecutionContext(), arguments.isTechPreview());
                }
                provisioning = provider.newProvisioningBuilder(provisioningXML).build();
                config = provisioning.loadProvisioningConfig(provisioningXML);
            } else {
                provisioning = provider.newProvisioningBuilder(config).build();
            }
            Utils.exportOffline(provisioning, config, universeResolver);
        } finally {
            if (provisioning != null) {
                provisioning.close();
            }
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
        Set<String> excludedPackages = new TreeSet<>();
        Map<Layer, Set<String>> excludedFeatures = new TreeMap<>();
        UniverseResolver universeResolver = UniverseResolver.builder().addArtifactResolver(resolver).build();

        if (Files.exists(OFFLINE_ZIP)) {
            Files.createDirectories(OFFLINE_CONTENT);
            ZipUtils.unzip(OFFLINE_ZIP, OFFLINE_CONTENT);
        }
        GalleonBuilder provider = new GalleonBuilder();
        provider.addArtifactResolver(resolver);
        Provisioning provisioning = null;
        GalleonProvisioningConfig config = Utils.buildOfflineProvisioningConfig(provider, writer);
        Path fakeHome = Files.createTempDirectory("wildfly-glow");
        try {
            if (config == null) {
                Path provisioningXML = arguments.getProvisioningXML();
                if (provisioningXML == null) {
                    provisioningXML = FeaturePacks.getFeaturePacks(arguments.getVersion(), arguments.getExecutionContext(), arguments.isTechPreview());
                }
                provisioning = provider.newProvisioningBuilder(provisioningXML).setInstallationHome(fakeHome).build();
                config = provisioning.loadProvisioningConfig(provisioningXML);
            } else {
                provisioning = provider.newProvisioningBuilder(config).setInstallationHome(fakeHome).build();
            }
            // Handle cases were no version is provided
            Map<ProducerSpec, FPID> fpVersions = new HashMap<>();
            Map<ProducerSpec, FPID> originalVersions = new HashMap<>();
            // Resolve feature-packs
            GalleonProvisioningConfig.Builder outputConfigBuilder = GalleonProvisioningConfig.builder();
            for (GalleonFeaturePackConfig dep : config.getFeaturePackDeps()) {
                FeaturePackLocation.FPID fpid = Utils.toMavenCoordinates(dep.getLocation().getFPID(), universeResolver);
                String[] coordinates = fpid.toString().split(":");
                String groupId = coordinates[0];
                String artifactId = coordinates[1];
                String version = null;
                MavenArtifact artifact = new MavenArtifact();
                artifact.setArtifactId(artifactId);
                artifact.setGroupId(groupId);
                if(coordinates.length >= 3) {
                    version = coordinates[2];
                }
                artifact.setVersion(version);
                artifact.setExtension("zip");
                resolver.resolve(artifact);
                FeaturePackLocation loc = dep.getLocation().replaceBuild(artifact.getVersion());
                outputConfigBuilder.addFeaturePackDep(loc);
                fpVersions.put(fpid.getProducer(), loc.getFPID());
                originalVersions.put(fpid.getProducer(), fpid);
            }
            config = outputConfigBuilder.build();
            // BUILD MODEL
            Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies = new HashMap<>();
            Map<String, Layer> all
                    = Utils.getAllLayers(config, universeResolver, provisioning, fpDependencies);
            LayerMapping mapping = Utils.buildMapping(all, arguments.getExecutionProfiles());
            if (mapping.getDefaultBaseLayer() == null) {
                throw new IllegalArgumentException("No base layer found, server version is not supported. "
                        + "You must upgrade to a more recent server version.");
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
                    LayerMapping.addRule(LayerMapping.RULE.EXPLICIT, foundLayer, null);
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
                    LayerMapping.addRule(LayerMapping.RULE.EXPLICIT, foundLayer, null);
                }
            }

            Map<Layer, Set<Layer>> ret = findBaseLayer(mapping, all);
            Layer baseLayer = ret.keySet().iterator().next();
            LayerMapping.addRule(LayerMapping.RULE.BASE_LAYER, baseLayer, null);
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
                            LayerMapping.addRule(LayerMapping.RULE.ADD_ON, l, null);
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
                                LayerMapping.addRule(LayerMapping.RULE.ADD_ON_REQUIRED_DEPENDENCIES_FOUND, layer, null);
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
                                LayerMapping.addRule(LayerMapping.RULE.ADD_ON_REQUIRED_DEPENDENCIES_FOUND, layer, null);
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
                        LayerMapping.addRule(LayerMapping.RULE.ADD_ON_ALWAYS_INCLUDED, layer, null);
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
                        LayerMapping.addRule(LayerMapping.RULE.ADD_ON_REQUIRED_DEPENDENCIES_FOUND, layer, null);
                    }
                }
            }
            // Add Layers that are included if some of there dependencies have been included
            for (Layer layer : mapping.getLayersIncludedIfSomeDeps().keySet()) {
                if (!allBaseLayers.contains(layer) && !layer.isBanned()) {
                    if (allBaseLayers.containsAll(mapping.getLayersIncludedIfSomeDeps().get(layer))) {
                        layers.add(layer);
                        allBaseLayers.add(layer);
                        LayerMapping.addRule(LayerMapping.RULE.ADD_ON_REQUIRED_DEPENDENCIES_FOUND, layer, null);
                    }
                }
            }
            // Add layers that are included at the FP level (model.xml).
            for (Layer layer : all.values()) {
                if (layer.isIsAutomaticInjection() && !layer.isBanned()) {
                    allBaseLayers.add(layer);
                    LayerMapping.addRule(LayerMapping.RULE.ALWAYS_INCLUDED, layer, null);
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
                    LayerMapping.addRule(LayerMapping.RULE.PROFILE_INCLUDED, toInclude, null);
                    excludedLayers.add(l);
                    LayerMapping.addRule(LayerMapping.RULE.PROFILE_EXCLUDED, l, null);
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
            Map<Layer, Set<Env>> buildTimeConfigurations = new TreeMap<>();
            Map<Layer, Set<Env>> buildTimeRequiredConfigurations = new TreeMap<>();
            for (Layer l : allBaseLayers) {
                if (!excludedLayers.contains(l)) {
                    if (!l.getConfiguration().isEmpty()) {
                        if (layers.contains(l)) {
                            Set<Env> requiredSet = new TreeSet<>();
                            Set<Env> notRequiredSet = new TreeSet<>();
                            Set<Env> buildTimeSet = new TreeSet<>();
                            Set<Env> buildTimeRequiredSet = new TreeSet<>();
                            for (String c : l.getConfiguration()) {
                                URI uri = new URI(c);
                                Set<Env> envs = EnvHandler.retrieveEnv(uri);
                                for (Env e : envs) {
                                    if (e.isRequired()) {
                                        if (e.isRuntime()) {
                                            requiredSet.add(e);
                                        } else {
                                            buildTimeRequiredSet.add(e);
                                        }
                                    } else {
                                        if (e.isRuntime()) {
                                            notRequiredSet.add(e);
                                        } else {
                                            buildTimeSet.add(e);
                                        }
                                    }
                                }
                            }
                            if (!requiredSet.isEmpty()) {
                                stronglySuggestedConfigurations.put(l, requiredSet);
                            }
                            if (!notRequiredSet.isEmpty()) {
                                suggestedConfigurations.put(l, notRequiredSet);
                            }
                            if (!buildTimeSet.isEmpty()) {
                                buildTimeConfigurations.put(l, buildTimeSet);
                            }
                            if (!buildTimeRequiredSet.isEmpty()) {
                                buildTimeRequiredConfigurations.put(l, buildTimeRequiredSet);
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
            Set<Layer> metadataOnlyLayers = new LinkedHashSet<>();
            // Remove layers from output that are metadata-only
            for (Layer metadataOnly : mapping.getMetadataOnly()) {
                if (decorators.contains(metadataOnly)) {
                    decorators.remove(metadataOnly);
                    metadataOnlyLayers.add(metadataOnly);
                }
                if (layers.contains(metadataOnly)) {
                    layers.remove(metadataOnly);
                    metadataOnlyLayers.add(metadataOnly);
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
            GalleonProvisioningConfig activeConfig = buildProvisioningConfig(config,
                    universeResolver, allBaseLayers, baseLayer, decorators, excludedLayers, fpDependencies, arguments.getConfigName(), arguments.getConfigStability(), arguments.getPackageStability(), originalVersions);

            // Handle stability
            String configStability = arguments.getConfigStability() == null ? arguments.getDefaultConfigStability() : arguments.getConfigStability();
            if (configStability != null) {
                List<Layer> checkLayers = new ArrayList<>();
                checkLayers.add(baseLayer);
                checkLayers.addAll(decorators);
                // Retrieve the features of each layer
                try {
                    System.setProperty(GALLEON_DISABLE_CAPABILITIES_CHECK_PROPERTY, "true");
                    for (Layer layer : checkLayers) {
                        try {
                            GalleonConfigurationWithLayers configLayers = GalleonConfigurationWithLayersBuilder.builder("standalone", "standalone.xml").includeLayer(layer.getName()).build();
                            GalleonProvisioningConfig.Builder config2Builder = GalleonProvisioningConfig.builder().addConfig(configLayers).addOption(Constants.CONFIG_STABILITY_LEVEL, Constants.STABILITY_EXPERIMENTAL);
                            for (GalleonFeaturePackConfig fp : activeConfig.getFeaturePackDeps()) {
                                config2Builder.addFeaturePackDep(GalleonFeaturePackConfig.
                                        builder(fp.getLocation(), false).setInheritConfigs(false).build());
                            }
                            GalleonProvisioningConfig config2 = config2Builder.build();

                            try (GalleonProvisioningRuntime rt = provisioning.getProvisioningRuntime(config2)) {
                                List<GalleonFeatureSpec> lst = rt.getAllFeatures();
                                for (GalleonFeatureSpec spec : lst) {
                                    String stab = spec.getStability();
                                    if (stab != null && !StabilitySupport.enables(configStability, stab)) {
                                        Set<String> set = excludedFeatures.get(layer);
                                        if (set == null) {
                                            set = new HashSet<>();
                                            excludedFeatures.put(layer, set);
                                        }
                                        set.add(spec.getName() + "[stability=" + spec.getStability() + "]");
                                    }
                                    for (GalleonFeatureParamSpec pspec : spec.getParams()) {
                                        String pstab = pspec.getStability();
                                        if (pstab != null && !StabilitySupport.enables(configStability, pstab)) {
                                            Set<String> set = excludedFeatures.get(layer);
                                            if (set == null) {
                                                set = new HashSet<>();
                                                excludedFeatures.put(layer, set);
                                            }
                                            set.add(spec.getName() + "." + pspec.getName() + "[stability=" + pspec.getStability() + "]");
                                        }
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            if (arguments.isVerbose()) {
                                ex.printStackTrace();
                            }
                            writer.warn("Got unexpected exception dealing with " + layer + " features. Exception" + ex + ". Please report the issue. Enable verbose to display the stack trace.");
                        }
                    }
                } finally {
                    System.clearProperty(GALLEON_DISABLE_CAPABILITIES_CHECK_PROPERTY);
                }
            }
            if(arguments.getPackageStability() != null) {
                // We must disable the stability to see all packages in the runtime
                GalleonProvisioningConfig config2 = GalleonProvisioningConfig.builder(activeConfig).
                        removeOption(Constants.STABILITY_LEVEL).
                        removeOption(Constants.CONFIG_STABILITY_LEVEL).
                        removeOption(Constants.PACKAGE_STABILITY_LEVEL).
                        addOption(Constants.STABILITY_LEVEL, arguments.getConfigStability() == null ? Constants.STABILITY_EXPERIMENTAL : arguments.getConfigStability()).build();
                try (GalleonProvisioningRuntime rt = provisioning.getProvisioningRuntime(config2)) {
                    for (GalleonFeaturePackRuntime fpr : rt.getGalleonFeaturePacks()) {
                        for (GalleonPackageRuntime prt : fpr.getGalleonPackages()) {
                            String packageStability = prt.getStability();
                            if (packageStability != null && !StabilitySupport.enables(arguments.getPackageStability(), packageStability)) {
                                excludedPackages.add(prt.getName() + "[stability="+packageStability+"]");
                            }
                        }
                    }
                }
            }
            Suggestions suggestions = new Suggestions(suggestedConfigurations,
                    stronglySuggestedConfigurations, buildTimeConfigurations, buildTimeRequiredConfigurations, possibleAddOns, possibleProfiles);
            ScanResults scanResults = new ScanResults(
                    this,
                    layers,
                    excludedLayers,
                    baseLayer,
                    decorators,
                    metadataOnlyLayers,
                    provisioning,
                    activeConfig,
                    allEnabledAddOns,
                    disabledAddOns,
                    suggestions,
                    errorSession,
                    excludedPackages,
                    excludedFeatures,
                    fpVersions,
                    channels
            );

            return scanResults;
        } finally {
            IoUtils.recursiveDelete(OFFLINE_CONTENT);
            IoUtils.recursiveDelete(fakeHome);
        }
    }

    OutputContent outputConfig(ScanResults scanResults, Path target, String dockerImageName) throws Exception {
        Provisioning provisioning = scanResults.getProvisioning();
        if (arguments.getOutput() == null) {
            throw new IllegalStateException("No output format set");
        }
        Map<OutputContent.OutputFile, Path> files = new HashMap<>();
        if (!OutputFormat.PROVISIONING_XML.equals(arguments.getOutput())) {
            if (scanResults.getErrorSession().hasErrors()) {
                writer.warn("You are provisioning a server although some errors still exist. You should first fix them.");
            }
        }
        if (!OutputFormat.PROVISIONING_XML.equals(arguments.getOutput()) &&
            !OutputFormat.OPENSHIFT.equals(arguments.getOutput())) {
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
            if (OutputFormat.OPENSHIFT.equals(arguments.getOutput())) {
                target = target.resolve("galleon");
            }
            Files.createDirectories(target);
            Path prov = target.resolve("provisioning.xml");
            provisioning.storeProvisioningConfig(scanResults.getProvisioningConfig(),prov);
            files.put(OutputContent.OutputFile.PROVISIONING_XML_FILE, prov.toAbsolutePath());
            if(!channels.isEmpty()) {
                String channelsContent = ChannelMapper.toYaml(channels);
                Path channelsFile = target.resolve("channel.yaml");
                Files.write(channelsFile, channelsContent.getBytes());
                files.put(OutputContent.OutputFile.CHANNEL_FILE, channelsFile.toAbsolutePath());
            }
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

    private Path provisionServer(List<Path> binaries, GalleonProvisioningConfig activeConfig,
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

                });
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

    private static GalleonProvisioningConfig buildProvisioningConfig(GalleonProvisioningConfig input,
            UniverseResolver universeResolver, Set<Layer> allBaseLayers,
            Layer baseLayer,
            Set<Layer> decorators,
            Set<Layer> excludedLayers,
            Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies,
            String configName, String configStability, String packageStability, Map<ProducerSpec, FPID> channelVersions) throws ProvisioningException {
        Map<ProducerSpec, GalleonFeaturePackConfig> map = new HashMap<>();
        Map<ProducerSpec, FPID> universeToGav = new HashMap<>();
        for (GalleonFeaturePackConfig cfg : input.getFeaturePackDeps()) {
            FeaturePackLocation.FPID loc = null;
            FeaturePackLocation.FPID fpid = Utils.toMavenCoordinates(cfg.getLocation().getFPID(), universeResolver);
            for (FeaturePackLocation.FPID f : fpDependencies.keySet()) {
                if (fpid.getProducer().equals(f.getProducer())) {
                    loc = fpid;
                    break;
                }
            }
            if(loc == null) {
                throw new ProvisioningException("Input fp "+ cfg.getLocation() + " not found in resolved feature-packs " + fpDependencies.keySet());
            }
            map.put(loc.getProducer(), cfg);
            universeToGav.put(cfg.getLocation().getProducer(), loc);
        }
        Map<ProducerSpec, FeaturePackLocation.FPID> tmpFps = new HashMap<>();
        FeaturePackLocation.FPID baseFPID = universeToGav.get(input.getFeaturePackDeps().iterator().next().getLocation().getProducer());
        tmpFps.put(baseFPID.getProducer(), baseFPID);
        for (Layer l : allBaseLayers) {
            for(FPID fpid : l.getFeaturePacks()) {
                tmpFps.put(fpid.getProducer(), fpid);
            }
        }
        Set<FeaturePackLocation.FPID> activeFeaturePacks = new LinkedHashSet<>();
        // Order follow the one from the input
        for(GalleonFeaturePackConfig cfg : input.getFeaturePackDeps()) {
            FeaturePackLocation.FPID gav = universeToGav.get(cfg.getLocation().getProducer());
            FeaturePackLocation.FPID fpid = tmpFps.get(gav.getProducer());
            if (fpid != null) {
                // Reset the version if ruled by channel
                FPID orig = channelVersions.get(cfg.getLocation().getProducer());
                if ( orig != null && orig.getLocation().isMavenCoordinates()) {
                    gav = gav.getLocation().replaceBuild(orig.getBuild()).getFPID();
                }
                activeFeaturePacks.add(gav);
            }
        }
        GalleonProvisioningConfig.Builder activeConfigBuilder = GalleonProvisioningConfig.builder();
        //List<GalleonFeaturePack> activeFPs = new ArrayList<>();
        for (FeaturePackLocation.FPID fpid : activeFeaturePacks) {
            GalleonFeaturePackConfig.Builder fpBuilder = GalleonFeaturePackConfig.builder(fpid.getLocation());
            fpBuilder.setInheritConfigs(false);
            fpBuilder.setInheritPackages(false);
            // The input config included packages is to cover some wildfly tests corner cases.
            GalleonFeaturePackConfig inCfg = map.get(fpid.getProducer());
            fpBuilder.includeAllPackages(inCfg.getIncludedPackages());

            //GalleonFeaturePack activeFP = new GalleonFeaturePack();
            //activeFP.setLocation(fpid.getLocation().toString());
            //activeFPs.add(activeFP);
            activeConfigBuilder.addFeaturePackDep(fpBuilder.build());
        }
        //ProvisioningContext activeContext = provisioning.buildProvisioningContext(activeFPs);

        GalleonConfigurationWithLayersBuilder configBuilder = GalleonConfigurationWithLayersBuilder.builder("standalone", configName);
        configBuilder.includeLayer(baseLayer.getName());
        for (Layer l : decorators) {
            configBuilder.includeLayer(l.getName());
        }
        for (Layer l : excludedLayers) {
            configBuilder.excludeLayer(l.getName());
        }
        activeConfigBuilder.addConfig(configBuilder.build());

        Map<String, String> options = new HashMap<>();
        options.put(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
        options.put("jboss-fork-embedded", "true");
        if (configStability != null) {
            options.put(Constants.CONFIG_STABILITY_LEVEL, configStability.toString());
        }
        if (packageStability != null) {
            options.put(Constants.PACKAGE_STABILITY_LEVEL, packageStability.toString());
        }
        activeConfigBuilder.addOptions(options);
        return activeConfigBuilder.build();
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
