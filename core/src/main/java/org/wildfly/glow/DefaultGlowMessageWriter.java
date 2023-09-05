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
package org.wildfly.glow;

class DefaultGlowMessageWriter implements GlowMessageWriter {

    static final boolean VERBOSE = Boolean.getBoolean("verbose");

    private final GlowMessageWriter delegate;

    DefaultGlowMessageWriter() {
        boolean useAnsiColours = System.console() != null && System.getenv().get("TERM") != null;
        delegate = useAnsiColours ? new AnsiColourMessageWriter() : new GlowMessageWriter() {
        };
    }

    @Override
    public void info(Object s) {
        delegate.info(s);
    }

    @Override
    public void warn(Object s) {
        delegate.warn(s);
    }

    @Override
    public void error(Object s) {
        delegate.error(s);
    }

    @Override
    public void trace(Object s) {
        delegate.trace(s);
    }

    @Override
    public boolean isVerbose() {
        return VERBOSE;
    }

    private static class AnsiColourMessageWriter implements GlowMessageWriter {

        private static final String ANSI_RESET = "\u001B[0m";
        private static final String ANSI_RED = "\u001B[31m";
        private static final String ANSI_YELLOW = "\u001B[33m";

        @Override
        public void info(Object s) {
            GlowMessageWriter.super.info(s);
        }

        @Override
        public void warn(Object s) {
            GlowMessageWriter.super.warn(ANSI_YELLOW + s + ANSI_RESET);
        }

        @Override
        public void error(Object s) {
            GlowMessageWriter.super.error(ANSI_RED + s + ANSI_RESET);
        }
    }
}
