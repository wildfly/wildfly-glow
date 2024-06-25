/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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
package org.wildfly.glow.cli.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.glow.AddOn;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.Layer;
import org.wildfly.glow.LayerMapping;
import org.wildfly.glow.ProvisioningUtils;
import org.wildfly.glow.ScanArguments;
import org.wildfly.glow.maven.MavenResolver;

/**
 *
 * @author jdenise
 */
public class Utils {

    public static void showAddOns(AbstractCommand cmd, String context, Path provisioningXml, boolean isLatest,
            String serverVersion, boolean isPreview, Path channelsFile) throws Exception {
        CLIConfigurationResolver resolver = new CLIConfigurationResolver();
        ProvisioningUtils.ProvisioningConsumer consumer = new ProvisioningUtils.ProvisioningConsumer() {
            @Override
            public void consume(GalleonProvisioningConfig provisioning, Map<String, Layer> all,
                    LayerMapping mapping, Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies) throws Exception {
                StringBuilder builder = new StringBuilder();
                for (Map.Entry<String, Set<AddOn>> entry : mapping.getAddOnFamilyMembers().entrySet()) {
                    builder.append("* @|bold ").append(entry.getKey()).append("|@ add-ons:%n");
                    for (AddOn member : mapping.getAddOnFamilyMembers().get(entry.getKey())) {
                        if (!member.getName().endsWith(":default")) {
                            String deployer = resolver.getPossibleDeployer(member.getLayers());
                            builder.append(" - ").append(member.getName()).append((deployer == null ? "" : " @|bold (supported by " + deployer + " deployer)|@")).append(member.getDescription() == null ? "" : ": " + member.getDescription()).append("%n");
                        }
                    }
                }
                cmd.print(builder.toString());
                cmd.print("@|bold Add-ons can be set using the|@ @|fg(yellow) %s=<list of add-ons>|@ @|bold option of the|@ @|fg(yellow) %s|@ @|bold command|@", Constants.ADD_ONS_OPTION, Constants.SCAN_COMMAND);

            }

        };
        ScanArguments.Builder builder = Arguments.scanBuilder();
        MavenRepoManager repoManager;
        List<Channel> channels = Collections.emptyList();
        if (channelsFile != null) {
            String content = Files.readString(channelsFile);
            channels = ChannelMapper.fromString(content);
            builder.setChannels(channels);
            repoManager = MavenResolver.newMavenResolver(channels);
        } else {
            repoManager = MavenResolver.newMavenResolver();
        }
        ProvisioningUtils.traverseProvisioning(consumer, context, provisioningXml, isLatest, serverVersion,
                isPreview, channels, repoManager);
    }

    public static void setSystemProperties(Set<String> systemProperties) throws Exception {
        if (!systemProperties.isEmpty()) {
            for (String p : systemProperties) {
                if (p.startsWith("-D")) {
                    int i = p.indexOf("=");
                    String propName;
                    String value = "";
                    if (i > 0) {
                        propName = p.substring(2, i);
                        value = p.substring(i + 1);
                    } else {
                        propName = p.substring(2);
                    }
                    System.setProperty(propName, value);
                } else {
                    throw new Exception("Invalid system property " + p + ". A property must start with -D");
                }
            }
        }
    }

    public static Map<String, String> handleOpenShiftEnvFile(Path envFile) throws Exception {
        Map<String, String> extraEnv = new HashMap<>();
        if (!Files.exists(envFile)) {
            throw new Exception(envFile + " file doesn't exist");
        }
        for (String l : Files.readAllLines(envFile)) {
            l = l.trim();
            if (!l.isEmpty() && !l.startsWith("#")) {
                int i = l.indexOf("=");
                if (i < 0 || i == l.length() - 1) {
                    throw new Exception("Invalid environment variable " + l + " in " + envFile);
                }
                extraEnv.put(l.substring(0, i), l.substring(i + 1));
            }
        }
        return extraEnv;
    }
}
