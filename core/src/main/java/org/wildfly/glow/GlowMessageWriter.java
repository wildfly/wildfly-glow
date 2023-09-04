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

public interface GlowMessageWriter {

    default void info(Object s) {
        System.out.println(s);
    }

    default void info(String format, Object... args) {
        info(String.format(format, args));
    }

    default void warn(Object s) {
        System.out.println(s);
    }

    default void warn(String format, Object... args) {
        warn(String.format(format, args));
    }

    default void error(Object s) {
        System.out.println(s);
    }

    default void error(String format, Object... args) {
        error(String.format(format, args));
    }

    default void trace(Object s) {
        System.out.println(s);
    }

    default void trace(String format, Object... args) {
        trace(String.format(format, args));
    }

    default boolean isVerbose() {
        return false;
    }

    GlowMessageWriter DEFAULT = new DefaultGlowMessageWriter();
}
