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

import org.wildfly.glow.cli.support.ExecutionExceptionHandler;
import java.util.Arrays;
import org.wildfly.glow.cli.support.AbstractCommand;
import org.wildfly.glow.cli.commands.MainCommand;
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
            final boolean isVerbose = Arrays.stream(args).anyMatch(s -> s.equals("-vv") || s.equals("--verbose"));
            commandLine.setExecutionExceptionHandler(new ExecutionExceptionHandler(isVerbose, command));
            int exitCode = commandLine.execute(args);
            System.exit(exitCode);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
    }
}
