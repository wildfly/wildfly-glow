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
package org.wildfly.glow.cli.support;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

import picocli.CommandLine;

public abstract class AbstractCommand implements Callable<Integer> {

    @CommandLine.Option(
            names = {Constants.BATCH_OPTION_SHORT, Constants.BATCH_OPTION}
    )
    private boolean batch;

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
    protected boolean verbose;

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    private PrintWriter stdout;

    private PrintWriter stderr;
    private CommandLine.Help.Ansi ansi;

    public void print() {
        final PrintWriter writer = getStdout();
        writer.println();
    }

    public void print(final Object msg) {
        final PrintWriter writer = getStdout();
        writer.println(format(String.valueOf(msg)));
    }

    public void print(final String fmt, final Object... args) {
        print(0, fmt, args);
    }

    @SuppressWarnings("SameParameterValue")
    public void print(final int padding, final Object message) {
        final PrintWriter writer = getStdout();
        if (padding > 0) {
            writer.printf("%1$" + padding + "s", " ");
        }
        writer.println(message);
    }

    public void print(final int padding, final String fmt, final Object... args) {
        print(getStdout(), padding, fmt, args);
    }

    public void printError(final String fmt, final Object... args) {
        print(getStderr(), 0, "@|fg(red) " + fmt + "|@", args);
    }

    public PrintWriter getStdout() {
        if (stdout == null) {
            stdout = spec.commandLine().getOut();
        }
        return stdout;
    }

    String format(final String fmt, final Object... args) {
        if (ansi == null) {
            ansi = batch ? CommandLine.Help.Ansi.OFF : spec.commandLine().getColorScheme().ansi();
        }
        return format(ansi, String.format(fmt, args));
    }

    String format(final CommandLine.Help.Ansi ansi, final String value) {
        return ansi.string(value);
    }

    public PrintWriter getStderr() {
        if (stderr == null) {
            stderr = spec.commandLine().getErr();
        }
        return stderr;
    }

    private void print(final PrintWriter writer, final int padding, final String fmt, final Object... args) {
        if (padding > 0) {
            writer.printf("%1$" + padding + "s", " ");
        }
        writer.println(format(fmt, args));
    }
}
