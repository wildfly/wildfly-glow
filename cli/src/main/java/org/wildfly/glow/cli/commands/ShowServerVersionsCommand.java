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
import java.util.LinkedHashSet;
import java.util.Set;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.glow.cli.support.AbstractCommand;
import org.wildfly.glow.cli.support.Constants;
import org.wildfly.glow.MetadataProvider;
import org.wildfly.glow.Space;
import org.wildfly.glow.WildFlyMavenMetadataProvider;
import org.wildfly.glow.maven.MavenResolver;
import picocli.CommandLine;

@CommandLine.Command(
        name = Constants.SHOW_SERVER_VERSIONS_COMMAND,
        sortOptions = true
)
public class ShowServerVersionsCommand extends AbstractCommand {

    @CommandLine.Option(names = {Constants.SPACES_OPTION_SHORT, Constants.SPACES_OPTION}, split = ",", paramLabel = Constants.SPACES_OPTION_LABEL)
    Set<String> spaces = new LinkedHashSet<>();

    @Override
    public Integer call() throws Exception {
        print("WildFly server versions in the " + Space.DEFAULT.getName() + " space:");
        Path tmpMetadataDirectory = Files.createTempDirectory("glow-metadata");
        try {
            MetadataProvider metadataProvider = new WildFlyMavenMetadataProvider(MavenResolver.newMavenResolver(), tmpMetadataDirectory);
            print(metadataProvider.getAllVersions());
            for (String space : spaces) {
                print("WildFly server versions in the " + space + " space:");
                print(metadataProvider.getAllVersions(space));
            }
            print("@|bold WildFly server version can be set using the|@ @|fg(yellow) %s=<server version>|@ @|bold option of the|@ @|fg(yellow) %s|@ @|bold command|@", Constants.SERVER_VERSION_OPTION, Constants.SCAN_COMMAND);
            return 0;
        } finally {
            IoUtils.recursiveDelete(tmpMetadataDirectory);
        }
    }
}
