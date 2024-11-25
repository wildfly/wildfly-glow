/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.glow.cli.commands;

import java.nio.file.Files;
import org.wildfly.glow.cli.support.AbstractCommand;
import org.wildfly.glow.cli.support.Constants;
import org.wildfly.glow.ProvisioningUtils;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.FeaturePackLocation.ProducerSpec;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.glow.maven.MavenResolver;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.FeaturePacks;
import org.wildfly.glow.Layer;
import org.wildfly.glow.LayerMapping;
import org.wildfly.glow.ScanArguments;
import org.wildfly.glow.Space;
import org.wildfly.glow.deployment.openshift.api.Deployer;

import picocli.CommandLine;

@CommandLine.Command(
        name = Constants.SHOW_CONFIGURATION_COMMAND,
        sortOptions = true
)
public class ShowConfigurationCommand extends AbstractCommand {

    @CommandLine.Option(names = {Constants.CLOUD_OPTION_SHORT, Constants.CLOUD_OPTION})
    Optional<Boolean> cloud;

    @CommandLine.Option(names = {Constants.WILDFLY_PREVIEW_OPTION_SHORT, Constants.WILDFLY_PREVIEW_OPTION})
    Optional<Boolean> wildflyPreview;

    @CommandLine.Option(names = {Constants.SERVER_VERSION_OPTION_SHORT, Constants.SERVER_VERSION_OPTION}, paramLabel = Constants.SERVER_VERSION_OPTION_LABEL)
    Optional<String> wildflyServerVersion;

    @CommandLine.Option(names = Constants.INPUT_FEATURE_PACKS_FILE_OPTION, paramLabel = Constants.INPUT_FEATURE_PACKS_FILE_OPTION_LABEL)
    Optional<Path> provisioningXml;

    @CommandLine.Option(names = {Constants.CHANNELS_OPTION_SHORT, Constants.CHANNELS_OPTION}, paramLabel = Constants.CHANNELS_OPTION_LABEL)
    Optional<Path> channelsFile;

    @CommandLine.Option(names = {Constants.SPACES_OPTION_SHORT, Constants.SPACES_OPTION}, split = ",", paramLabel = Constants.SPACES_OPTION_LABEL)
    Set<String> spaces = new LinkedHashSet<>();

    private Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> defaultSpaceFpDependencies;

    @Override
    public Integer call() throws Exception {
        print("Wildfly Glow is retrieving known provisioning configuration...");
        StringBuilder ocBuilder = new StringBuilder();
        ocBuilder.append("\nDeployers enabled when provisioning to OpenShift:\n");
        for (Deployer d : ServiceLoader.load(Deployer.class)) {
            ocBuilder.append("* @|bold " + d.getName() + "|@. Enabled when the layer(s) " + d.getSupportedLayers() + " is/are discovered.\n");
        }
        print(ocBuilder.toString());
        StringBuilder spacesBuilder = new StringBuilder();
        spacesBuilder.append("\nSpaces from which more feature-packs can be used when scanning deployments (use the " + Constants.SPACES_OPTION + " option to enable the space(s):\n");
        for(Space space : FeaturePacks.getAllSpaces()) {
            spacesBuilder.append("* @|bold " + space.getName() + "|@. " + space.getDescription() + "\n");
        }
        print(spacesBuilder.toString());

        String context = Arguments.BARE_METAL_EXECUTION_CONTEXT;
        if (cloud.orElse(false)) {
            context = Arguments.CLOUD_EXECUTION_CONTEXT;
        }
        if (wildflyPreview.orElse(false)) {
            if (channelsFile.isPresent()) {
                throw new Exception(Constants.WILDFLY_PREVIEW_OPTION + "can't be set when " + Constants.CHANNELS_OPTION + " is set.");
            }
        }
        if (wildflyServerVersion.isPresent()) {
            if (channelsFile.isPresent()) {
                throw new Exception(Constants.SERVER_VERSION_OPTION + "can't be set when " + Constants.CHANNELS_OPTION + " is set.");
            }
        }
        String finalContext = context;
        boolean isLatest = wildflyServerVersion.isEmpty();
        String vers = wildflyServerVersion.isPresent() ? wildflyServerVersion.get() : FeaturePacks.getLatestVersion();
        ProvisioningUtils.ProvisioningConsumer consumer = new ProvisioningUtils.ProvisioningConsumer() {
            @Override
            public void consume(Space space, GalleonProvisioningConfig provisioning, Map<String, Layer> all,
                    LayerMapping mapping, Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies) throws Exception {
                if (Space.DEFAULT.equals(space)) {
                    defaultSpaceFpDependencies = fpDependencies;
                }
                String configStr = dumpConfiguration(space, fpDependencies, finalContext, vers, all,
                        mapping, provisioning, isLatest, wildflyPreview.orElse(false), provisioningXml.orElse(null));
                print(configStr);
            }
        };
        ScanArguments.Builder builder = Arguments.scanBuilder();
        MavenRepoManager repoManager;
        List<Channel> channels = Collections.emptyList();
        if (channelsFile.isPresent()) {
            String content = Files.readString(channelsFile.get());
            channels = ChannelMapper.fromString(content);
            builder.setChannels(channels);
            repoManager = MavenResolver.newMavenResolver(channels);
        } else {
            repoManager = MavenResolver.newMavenResolver();
        }
        ProvisioningUtils.traverseProvisioning(Space.DEFAULT, consumer, context, provisioningXml.orElse(null), wildflyServerVersion.isEmpty(), vers, wildflyPreview.orElse(false), channels, repoManager);
        for(String spaceName : spaces) {
            Set<String> versions = FeaturePacks.getAllVersions(spaceName);
            if (versions.contains(vers)) {
                Space space = FeaturePacks.getSpace(spaceName);
                ProvisioningUtils.traverseProvisioning(space, consumer, context, provisioningXml.orElse(null), wildflyServerVersion.isEmpty(), vers, wildflyPreview.orElse(false), channels, repoManager);
            }
        }
        return 0;
    }

    private String dumpConfiguration(Space space, Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies,
            String context, String serverVersion, Map<String, Layer> allLayers,
            LayerMapping mapping, GalleonProvisioningConfig config, boolean isLatest, boolean techPreview, Path provisioningXml) throws Exception {
        StringBuilder builder = new StringBuilder();
        if (config == null) {
            builder.append("\nFeature-packs in the @|bold ").append(space.getName()).append("|@ space:\n");
            builder.append("No feature-packs found in the " + space.getName() + " space for context " + context + ", server version " +serverVersion + ".");
            return builder.toString();
        }

        if (Space.DEFAULT.equals(space)) {
            if (provisioningXml == null) {
                builder.append("Execution context: ").append(context).append("\n");
                builder.append("Server version: ").append(serverVersion).append(isLatest ? " (latest)" : "").append("\n");
                builder.append("Tech Preview: ").append(techPreview).append("\n");
            } else {
                builder.append("Input provisioning.xml file: ").append(provisioningXml).append("\n");
            }
        }
        builder.append("\nFeature-packs in the @|bold ").append(space.getName()).append("|@ space:\n");
        Set<FeaturePackLocation.ProducerSpec> topLevel = new LinkedHashSet<>();
        Map<ProducerSpec, FPID> featurepacks = new LinkedHashMap<>();
        for(GalleonFeaturePackConfig fp : config.getFeaturePackDeps()) {
            topLevel.add(fp.getLocation().getProducer());
            for(FPID fpid : fpDependencies.keySet()) {
                if(fpid.getProducer().equals(fp.getLocation().getProducer())) {
                    featurepacks.put(fp.getLocation().getProducer(), fpid);
                    break;
                }
            }
        }
        for(ProducerSpec p : featurepacks.keySet()) {
            FPID id = featurepacks.get(p);
            builder.append("\nFeature-pack: ").append("@|bold ").append(id).append("|@\n");
            builder.append("Contained layers: ");
            Set<String> layers = new TreeSet<>();
            Set<FeaturePackLocation.ProducerSpec> deps = fpDependencies.get(id);
            for(Layer l : allLayers.values()) {
                if(l.getFeaturePacks().contains(id)) {
                    layers.add(l.getName());
                }
                if(deps != null) {
                    for (FeaturePackLocation.ProducerSpec dep : deps) {
                        boolean inDefaultSpace = false;
                        if (!Space.DEFAULT.equals(space)) {
                            for (FPID fpid : defaultSpaceFpDependencies.keySet()) {
                                if (fpid.getProducer().equals(dep)) {
                                    inDefaultSpace = true;
                                    break;
                                }
                            }
                        }
                        if (!topLevel.contains(dep) && !inDefaultSpace) {
                            for (FeaturePackLocation.FPID fpid : l.getFeaturePacks()) {
                                if (fpid.getProducer().equals(dep)) {
                                    layers.add(l.getName());
                                }
                            }
                        }
                    }
                }
            }
            topLevel.addAll(deps);
            builder.append(layers).append("\n");
        }
        return builder.toString();
    }
}
