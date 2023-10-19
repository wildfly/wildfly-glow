/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.glow.cli;

import org.wildfly.glow.cli.commands.AbstractCommand;
import picocli.CommandLine;


/**
 * Handles exceptions that happen during command executions.
 */
public class ExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {

    private final AbstractCommand command;
    private final boolean isVerbose;

    public ExecutionExceptionHandler(boolean isVerbose, AbstractCommand command) {
        this.isVerbose = isVerbose;
        this.command = command;
    }

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult)
            throws Exception {
        command.printError("ERROR: %s",  ex.getLocalizedMessage());
        if(isVerbose) {
            ex.printStackTrace(command.getStderr());
        }
        return 1;
    }

}
