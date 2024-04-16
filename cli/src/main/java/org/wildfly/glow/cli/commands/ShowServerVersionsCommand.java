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
import org.wildfly.glow.FeaturePacks;
import picocli.CommandLine;

@CommandLine.Command(
        name = Constants.SHOW_SERVER_VERSIONS_COMMAND,
        sortOptions = true
)
public class ShowServerVersionsCommand extends AbstractCommand {

    @Override
    public Integer call() throws Exception {
        print(FeaturePacks.getAllVersions());
        print("@|bold WildFly server version can be set using the|@ @|fg(yellow) %s=<server version>|@ @|bold option of the|@ @|fg(yellow) %s|@ @|bold command|@", Constants.SERVER_VERSION_OPTION, Constants.SCAN_COMMAND);
        return 0;
    }
}
