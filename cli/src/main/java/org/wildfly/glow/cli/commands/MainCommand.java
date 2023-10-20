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

import java.util.ResourceBundle;
import org.wildfly.glow.Version;

import picocli.CommandLine;

@CommandLine.Command(name = Constants.WILDFLY_GLOW, resourceBundle = "UsageMessages",
        versionProvider = MainCommand.VersionProvider.class)
public class MainCommand extends AbstractCommand {

    @SuppressWarnings("unused")
    @CommandLine.Option(names = {Constants.VERSION_OPTION_SHORT, Constants.VERSION_OPTION}, versionHelp = true)
    boolean version;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        // print welcome message - this is not printed when -h option is set
        ResourceBundle usageBundle = ResourceBundle.getBundle("UsageMessages");

        print(usageBundle.getString("glow.welcomeMessage"));
        return 0;
    }

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            return new String[] {Version.getVersion()};
        }
    }
}
