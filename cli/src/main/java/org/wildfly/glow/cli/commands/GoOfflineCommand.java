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

import org.wildfly.glow.cli.support.AbstractCommand;
import org.wildfly.glow.cli.support.Constants;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.wildfly.glow.Arguments;
import static org.wildfly.glow.Arguments.CLOUD_EXECUTION_CONTEXT;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import static org.wildfly.glow.GlowSession.OFFLINE_ZIP;
import org.wildfly.glow.ScanArguments.Builder;
import org.wildfly.glow.maven.MavenResolver;

import picocli.CommandLine;

@CommandLine.Command(
        name = Constants.GO_OFFLINE_COMMAND,
        sortOptions = true
)
public class GoOfflineCommand extends AbstractCommand {

    @CommandLine.Option(names = {Constants.CLOUD_OPTION_SHORT, Constants.CLOUD_OPTION})
    Optional<Boolean> cloud;

    @CommandLine.Option(names = {Constants.WILDFLY_PREVIEW_OPTION_SHORT, Constants.WILDFLY_PREVIEW_OPTION})
    Optional<Boolean> wildflyPreview;

    @CommandLine.Option(names = {Constants.SERVER_VERSION_OPTION_SHORT, Constants.SERVER_VERSION_OPTION}, paramLabel = "<server version>")
    Optional<String> wildflyServerVersion;

    @CommandLine.Option(names = Constants.INPUT_FEATURE_PACKS_FILE_OPTION, paramLabel = "<provisioning file path>")
    Optional<Path> provisioningXml;

    @CommandLine.Option(names = {Constants.SPACES_OPTION_SHORT, Constants.SPACES_OPTION}, split = ",", paramLabel = Constants.SPACES_OPTION_LABEL)
    Set<String> spaces = new LinkedHashSet<>();

    @Override
    public Integer call() throws Exception {
        print("Wildfly Glow is assembling offline content...");
        Builder builder = Arguments.scanBuilder();
        if (cloud.orElse(false)) {
            builder.setExecutionContext(CLOUD_EXECUTION_CONTEXT);
        }
        if (wildflyPreview.orElse(false)) {
            builder.setTechPreview(true);
        }
        if (wildflyServerVersion.isPresent()) {
            builder.setVersion(wildflyServerVersion.get());
        }
        builder.setVerbose(verbose);
        if (provisioningXml.isPresent()) {
            builder.setProvisoningXML(provisioningXml.get());
        }
        if (!spaces.isEmpty()) {
            builder.setSpaces(spaces);
        }
        GlowSession.goOffline(MavenResolver.newMavenResolver(), builder.build(), GlowMessageWriter.DEFAULT);
        print("Offline zip file %s generated", OFFLINE_ZIP);
        return 0;
    }
}
