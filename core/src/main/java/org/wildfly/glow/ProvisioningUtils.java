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
package org.wildfly.glow;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.channel.Channel;
import static org.wildfly.glow.GlowSession.OFFLINE_CONTENT;

/**
 *
 * @author jdenise
 */
public class ProvisioningUtils {

    public interface ProvisioningConsumer {

        void consume(Space space, GalleonProvisioningConfig provisioning, Map<String, Layer> all,
                LayerMapping mapping, Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies) throws Exception;
    }

    public static void traverseProvisioning(Space space, ProvisioningConsumer consumer,
            String executionContext, Path provisioningXML, boolean isLatest, String wildflyServerVersion, boolean wildflyPreview, List<Channel> channels, MavenRepoManager resolver) throws Exception {
        UniverseResolver universeResolver = UniverseResolver.builder().addArtifactResolver(resolver).build();
        GalleonBuilder provider = new GalleonBuilder();
        provider.addArtifactResolver(resolver);
        String vers = wildflyServerVersion != null ? wildflyServerVersion : FeaturePacks.getLatestVersion();
        Provisioning provisioning = null;
        try {
            GalleonProvisioningConfig config = Utils.buildOfflineProvisioningConfig(provider, GlowMessageWriter.DEFAULT);
            if (config == null) {
                if (provisioningXML == null) {
                    provisioningXML = FeaturePacks.getFeaturePacks(space, vers, executionContext, wildflyPreview);
                }
                provisioning = provider.newProvisioningBuilder(provisioningXML).build();
                config = provisioning.loadProvisioningConfig(provisioningXML);
            } else {
                provisioning = provider.newProvisioningBuilder(config).build();
            }
            Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies = new HashMap<>();
            Map<String, Layer> all = Utils.getAllLayers(config, universeResolver, provisioning, fpDependencies);
            LayerMapping mapping = org.wildfly.glow.Utils.buildMapping(all, Collections.emptySet());
            consumer.consume(space, config, all, mapping, fpDependencies);
        } finally {
            IoUtils.recursiveDelete(OFFLINE_CONTENT);
            if (provisioning != null) {
                provisioning.close();
            }
        }
    }
}
