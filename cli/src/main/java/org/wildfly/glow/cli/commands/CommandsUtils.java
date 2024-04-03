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
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.channel.ChannelSession;
import org.wildfly.glow.FeaturePacks;
import org.wildfly.glow.GlowMessageWriter;
import static org.wildfly.glow.GlowSession.OFFLINE_CONTENT;
import org.wildfly.glow.Layer;
import org.wildfly.glow.LayerMapping;
import org.wildfly.glow.Utils;
import org.wildfly.glow.maven.ChannelMavenArtifactRepositoryManager;
import org.wildfly.glow.maven.MavenResolver;

/**
 *
 * @author jdenise
 */
public class CommandsUtils {

    public interface ProvisioningConsumer {

        void consume(GalleonProvisioningConfig provisioning, Map<String, Layer> all,
                LayerMapping mapping, Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies) throws Exception;
    }

    public static void buildProvisioning(ProvisioningConsumer consumer,
            String executionContext, Path provisioningXML, boolean isLatest, String wildflyServerVersion, boolean wildflyPreview, Path channelsFile) throws Exception {
        MavenRepoManager resolver = null;
        if (channelsFile != null) {
            if (!Files.exists(channelsFile)) {
                throw new Exception(channelsFile + " file doesn't exist");
            }
            ChannelSession session = MavenResolver.buildChannelSession(channelsFile);
            resolver = new ChannelMavenArtifactRepositoryManager(session);
        } else {
            resolver = MavenResolver.newMavenResolver();
        }
        UniverseResolver universeResolver = UniverseResolver.builder().addArtifactResolver(resolver).build();
        GalleonBuilder provider = new GalleonBuilder();
        provider.addArtifactResolver(resolver);
        String vers = wildflyServerVersion != null ? wildflyServerVersion : FeaturePacks.getLatestVersion();
        Provisioning provisioning = null;
        try {
            GalleonProvisioningConfig config = Utils.buildOfflineProvisioningConfig(provider, GlowMessageWriter.DEFAULT);
            if (config == null) {
                if (provisioningXML == null) {
                    provisioningXML = FeaturePacks.getFeaturePacks(vers, executionContext, wildflyPreview);
                }
                provisioning = provider.newProvisioningBuilder(provisioningXML).build();
                config = provisioning.loadProvisioningConfig(provisioningXML);
            } else {
                provisioning = provider.newProvisioningBuilder(config).build();
            }
            Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies = new HashMap<>();
            Map<String, Layer> all = Utils.getAllLayers(config, universeResolver, provisioning, fpDependencies);
            LayerMapping mapping = org.wildfly.glow.Utils.buildMapping(all, Collections.emptySet());
            consumer.consume(config, all, mapping, fpDependencies);
        } finally {
            IoUtils.recursiveDelete(OFFLINE_CONTENT);
            if (provisioning != null) {
                provisioning.close();
            }
        }
    }
}
