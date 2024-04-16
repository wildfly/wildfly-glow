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
import org.wildfly.glow.cli.support.Utils;
import org.wildfly.glow.cli.support.Constants;
import java.nio.file.Path;
import java.util.Optional;
import org.wildfly.glow.Arguments;
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

    @Override
    public Integer call() throws Exception {
        print("Wildfly Glow is retrieving add-ons...");
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
        Utils.showAddOns(this, context, provisioningXml.orElse(null), wildflyServerVersion.isEmpty(), wildflyServerVersion.orElse(null),
                wildflyPreview.orElse(false), channelsFile.orElse(null));
        return 0;
    }
}
