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

import java.net.URI;
import java.nio.file.Files;
import org.wildfly.glow.cli.support.AbstractCommand;
import org.wildfly.glow.cli.support.Constants;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.glow.AddOn;
import org.wildfly.glow.Arguments;
import static org.wildfly.glow.FeaturePacks.URL_PROPERTY;
import org.wildfly.glow.Layer;
import org.wildfly.glow.LayerMapping;
import org.wildfly.glow.MetadataProvider;
import org.wildfly.glow.ProvisioningUtils;
import org.wildfly.glow.ScanArguments;
import org.wildfly.glow.Space;
import org.wildfly.glow.WildFlyMavenMetadataProvider;
import org.wildfly.glow.WildFlyMetadataProvider;
import org.wildfly.glow.cli.support.CLIConfigurationResolver;
import org.wildfly.glow.maven.MavenResolver;
import picocli.CommandLine;

@CommandLine.Command(
        name = Constants.SHOW_ADD_ONS_COMMAND,
        sortOptions = true
)
public class ShowAddOnsCommand extends AbstractCommand {

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
        print("Wildfly Glow is retrieving add-ons...");
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
        Path tmpMetadataDirectory = null;
        MetadataProvider metadataProvider;
        try {
            String prop = System.getProperty(URL_PROPERTY);
            if (prop == null) {
                tmpMetadataDirectory = Files.createTempDirectory("wildfly-glow-metadata");
                metadataProvider = new WildFlyMavenMetadataProvider(repoManager, tmpMetadataDirectory);
            } else {
                tmpMetadataDirectory = null;
                metadataProvider = new WildFlyMetadataProvider(new URI(prop));
            }
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
            showAddOns(Space.DEFAULT, context, provisioningXml.orElse(null), wildflyServerVersion.isEmpty(), wildflyServerVersion.orElse(null),
                    wildflyPreview.orElse(false), channels, repoManager, metadataProvider);
            String vers = wildflyServerVersion.isPresent() ? wildflyServerVersion.get() : metadataProvider.getLatestVersion();
            for (String spaceName : spaces) {
                Set<String> versions = metadataProvider.getAllVersions(spaceName);
                if (versions.contains(vers)) {
                    Space space = metadataProvider.getSpace(spaceName);
                    showAddOns(space, context, provisioningXml.orElse(null), wildflyServerVersion.isEmpty(), wildflyServerVersion.orElse(null),
                            wildflyPreview.orElse(false), channels, repoManager, metadataProvider);
                }
            }
            print("@|bold Add-ons can be set using the|@ @|fg(yellow) %s=<list of add-ons>|@ @|bold option of the|@ @|fg(yellow) %s|@ @|bold command|@", Constants.ADD_ONS_OPTION, Constants.SCAN_COMMAND);

            return 0;
        } finally {
            if (tmpMetadataDirectory != null) {
                IoUtils.recursiveDelete(tmpMetadataDirectory);
            }
        }
    }

    public void showAddOns(Space space, String context, Path provisioningXml, boolean isLatest,
            String serverVersion, boolean isPreview, List<Channel> channels, MavenRepoManager repoManager, MetadataProvider metadataProvider) throws Exception {
        CLIConfigurationResolver resolver = new CLIConfigurationResolver();
        ProvisioningUtils.ProvisioningConsumer consumer = new ProvisioningUtils.ProvisioningConsumer() {
            @Override
            public void consume(Space space, GalleonProvisioningConfig provisioning, Map<String, Layer> all,
                    LayerMapping mapping, Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies) throws Exception {
                if (Space.DEFAULT.equals(space)) {
                    defaultSpaceFpDependencies = fpDependencies;
                }
                StringBuilder builder = new StringBuilder();
                builder.append("\nAdd-ons found in the @|bold ").append(space.getName()).append("|@ space:\n");
                if (provisioning == null) {
                    builder.append("- No Add-ons.");
                } else {
                    boolean foundAddOns = false;
                    for (Map.Entry<String, Set<AddOn>> entry : mapping.getAddOnFamilyMembers().entrySet()) {
                        StringBuilder addOnFamilyBuilder = new StringBuilder();
                        for (AddOn member : mapping.getAddOnFamilyMembers().get(entry.getKey())) {
                            boolean ignore = false;
                            if (!Space.DEFAULT.equals(space)) {
                                // Only keep addOns that are not defined in feature-packs from the default space.
                                for(Layer l : member.getLayers()) {
                                    for(FPID fpid : l.getFeaturePacks()) {
                                        for (FPID dfpid : defaultSpaceFpDependencies.keySet()) {
                                            if (dfpid.getProducer().equals(fpid.getProducer())) {
                                                ignore = true;
                                                break;
                                            }
                                        }
                                        if (ignore) {
                                            break;
                                        }
                                    }
                                    if(ignore) {
                                        break;
                                    }
                                }
                            }
                            if (!member.getName().endsWith(":default") && !ignore) {
                                foundAddOns = true;
                                String deployer = resolver.getPossibleDeployer(member.getLayers());
                                addOnFamilyBuilder.append(" - ").append(member.getName()).append((deployer == null ? "" : " @|bold (supported by " + deployer + " deployer)|@")).append(member.getDescription() == null ? "" : ": " + member.getDescription()).append("%n");
                            }
                        }
                        if (addOnFamilyBuilder.length() != 0) {
                          builder.append("* @|bold ").append(entry.getKey()).append("|@ add-ons:%n");
                          builder.append(addOnFamilyBuilder.toString());
                        }
                    }
                    if (!foundAddOns) {
                        builder.append("- No Add-ons.");
                    }
                }
                print(builder.toString());
            }

        };
        ProvisioningUtils.traverseProvisioning(space, consumer, context, provisioningXml, isLatest, serverVersion,
                isPreview, channels, repoManager, metadataProvider);
    }
}
