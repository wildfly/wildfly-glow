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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.glow.AddOn;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.Layer;
import org.wildfly.glow.LayerMapping;
import org.wildfly.glow.Utils;
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

    @Override
    public Integer call() throws Exception {
        System.out.println("Wildfly Glow is retrieving add-ons...");
        MavenRepoManager resolver = MavenResolver.newMavenResolver();
        UniverseResolver universeResolver = UniverseResolver.builder().addArtifactResolver(resolver).build();
        String context = Arguments.BARE_METAL_EXECUTION_CONTEXT;
        if (cloud.orElse(false)) {
            context = Arguments.CLOUD_EXECUTION_CONTEXT;
        }
        try (ProvisioningLayout<FeaturePackLayout> layout = Utils.buildLayout(context,
                provisioningXml.orElse(null), wildflyServerVersion.orElse(null), GlowMessageWriter.DEFAULT, wildflyPreview.orElse(false))) {
            Map<String, Layer> all;
            try {
                all = Utils.getAllLayers(universeResolver, layout, new HashMap<>());
            } finally {
                layout.close();
            }
            LayerMapping mapping = Utils.buildMapping(all, Collections.emptySet());
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, Set<AddOn>> entry : mapping.getAddOnFamilyMembers().entrySet()) {
                builder.append("* @|bold ").append(entry.getKey()).append("|@ add-ons:\n");
                for (AddOn member : mapping.getAddOnFamilyMembers().get(entry.getKey())) {
                    if (!member.getName().endsWith(":default")) {
                        builder.append(" - ").append(member.getName()).append(member.getDescription() == null ? "" : ": " + member.getDescription()).append("\n");
                    }
                }
            }
            System.out.println(CommandLine.Help.Ansi.AUTO.string(builder.toString()));
            System.out.println(CommandLine.Help.Ansi.AUTO.string("@|bold Add-ons can be set using the|@ @|fg(yellow) " + Constants.ADD_ONS_OPTION + "=<list of add-ons>|@ @|bold option of the|@ @|fg(yellow) " + Constants.SCAN_COMMAND + "|@ @|bold command|@"));
        }
        return 0;
    }
}
