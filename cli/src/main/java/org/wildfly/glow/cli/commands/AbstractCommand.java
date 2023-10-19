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

import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.CommandLine;

public abstract class AbstractCommand implements Callable<Integer> {

    @SuppressWarnings("unused")
    @CommandLine.Option(
            names = {Constants.HELP_OPTION_SHORT, Constants.HELP_OPTION},
            usageHelp = true
    )
    boolean help;

    @SuppressWarnings("unused")
    @CommandLine.Option(
            names = {Constants.VERBOSE_OPTION_SHORT, Constants.VERBOSE_OPTION}
    )
    Optional<Boolean> verbose;
}
