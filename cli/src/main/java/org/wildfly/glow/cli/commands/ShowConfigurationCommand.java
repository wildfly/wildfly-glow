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

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.FeaturePacks;
import org.wildfly.glow.Layer;
import org.wildfly.glow.LayerMapping;
import org.wildfly.glow.cli.CLIArguments;

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

    @Override
    public Integer call() throws Exception {
        print("Wildfly Glow is retrieving known provisioning configuration...");
        String context = Arguments.BARE_METAL_EXECUTION_CONTEXT;
        if (cloud.orElse(false)) {
            context = Arguments.CLOUD_EXECUTION_CONTEXT;
        }
        String finalContext = context;
        boolean isLatest = wildflyServerVersion.isEmpty();
        String vers = wildflyServerVersion.isPresent() ? wildflyServerVersion.get() : FeaturePacks.getLatestVersion();
        CommandsUtils.ProvisioningConsumer consumer = new CommandsUtils.ProvisioningConsumer() {
            @Override
            public void consume(GalleonProvisioningConfig provisioning, Map<String, Layer> all,
                    LayerMapping mapping, Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies) throws Exception {
                String configStr = CLIArguments.dumpConfiguration(fpDependencies, finalContext, vers, all,
                        mapping, provisioning, isLatest, wildflyPreview.orElse(false));
                print(configStr);
            }
        };
        CommandsUtils.buildProvisioning(consumer, context, provisioningXml.orElse(null), wildflyServerVersion.isEmpty(), context, wildflyPreview.orElse(false));

        return 0;
    }
}
