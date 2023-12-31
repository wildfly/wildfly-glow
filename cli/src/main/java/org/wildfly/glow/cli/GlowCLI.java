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
package org.wildfly.glow.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.Layer;
import org.wildfly.glow.LayerMapping;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.Utils;
import org.wildfly.glow.cli.commands.AbstractCommand;
import org.wildfly.glow.maven.MavenResolver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.UniverseResolver;
import org.wildfly.glow.FeaturePacks;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.Version;

import static org.wildfly.glow.GlowSession.OFFLINE_CONTENT;
import org.wildfly.glow.cli.commands.CompletionCommand;
import org.wildfly.glow.cli.commands.GoOfflineCommand;
import org.wildfly.glow.cli.commands.ShowConfigurationCommand;
import org.wildfly.glow.cli.commands.MainCommand;
import org.wildfly.glow.cli.commands.ScanCommand;
import org.wildfly.glow.cli.commands.ShowAddOnsCommand;
import org.wildfly.glow.cli.commands.ShowServerVersionsCommand;
import picocli.CommandLine;

/**
 *
 * @author jdenise
 */
public class GlowCLI {

    public static void main(String[] args) throws Exception {
        final AbstractCommand command = new MainCommand();
        try {
            CommandLine commandLine = new CommandLine(command);
            commandLine.addSubcommand(new ScanCommand());
            commandLine.addSubcommand(new ShowAddOnsCommand());
            commandLine.addSubcommand(new ShowServerVersionsCommand());
            commandLine.addSubcommand(new ShowConfigurationCommand());
            commandLine.addSubcommand(new GoOfflineCommand());
            commandLine.addSubcommand(new CompletionCommand());
            commandLine.setUsageHelpAutoWidth(true);
            final boolean isVerbose = Arrays.stream(args).anyMatch(s -> s.equals("-vv") || s.equals("--verbose"));
            commandLine.setExecutionExceptionHandler(new ExecutionExceptionHandler(isVerbose, command));
            int exitCode = commandLine.execute(args);
            System.exit(exitCode);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
        CLIArguments arguments = CLIArguments.fromMainArguments(args);
        if (arguments.isVersion()) {
           command.print(Version.getVersion());
           return;
        }
        boolean dumpInfos = args.length == 0 || arguments.isHelp();
        if (dumpInfos || arguments.isDisplayConfigurationInfo()) {
            MavenRepoManager resolver = MavenResolver.newMavenResolver();
            UniverseResolver universeResolver = UniverseResolver.builder().addArtifactResolver(resolver).build();
            GalleonBuilder provider = new GalleonBuilder();
            provider.addArtifactResolver(resolver);
            Provisioning provisioning = null;
            try {
                GalleonProvisioningConfig config = Utils.buildOfflineProvisioningConfig(provider, GlowMessageWriter.DEFAULT);
                if (config == null) {
                    Path provisioningXML = FeaturePacks.getFeaturePacks(arguments.getVersion(), arguments.getExecutionContext(), arguments.isTechPreview());
                    provisioning = provider.newProvisioningBuilder(provisioningXML).build();
                    config = provisioning.loadProvisioningConfig(provisioningXML);
                } else {
                    provisioning = provider.newProvisioningBuilder(config).build();
                }

                Map<String, Layer> all = Utils.getAllLayers(config, universeResolver, provisioning, new HashMap<>());
                Set<String> profiles = Utils.getAllProfiles(all);
                LayerMapping mapping = Utils.buildMapping(all, Collections.emptySet());
                if (dumpInfos) {
                    CLIArguments.dumpInfos(profiles);
                } else {
                    boolean isLatest = arguments.getVersion() == null;
                    String serverVersion = isLatest ? FeaturePacks.getLatestVersion() : arguments.getVersion();
                    CLIArguments.dumpConfiguration(null, arguments.getExecutionContext(), serverVersion, all, mapping, config, isLatest, arguments.isTechPreview());
                }
            } finally {
                IoUtils.recursiveDelete(OFFLINE_CONTENT);
                if (provisioning != null) {
                    provisioning.close();
                }
            }
            return;
        }

        if (arguments.isGoOffline()) {
            GlowSession.goOffline(MavenResolver.newMavenResolver(), arguments, GlowMessageWriter.DEFAULT);
        } else {
            //Temp
            try (ScanResults scanResults = GlowSession.scan(MavenResolver.newMavenResolver(), arguments, GlowMessageWriter.DEFAULT)) {
                if (arguments.getOutput() == null) {
                    scanResults.outputInformation();
                } else {
                    scanResults.outputConfig(Paths.get("server"), null);
                }
            }
        }
    }
}
