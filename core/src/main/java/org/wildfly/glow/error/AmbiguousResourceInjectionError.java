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

package org.wildfly.glow.error;

import org.wildfly.glow.ResourceInjectionJndiInfo;

public class AmbiguousResourceInjectionError extends IdentifiedError {
    private ResourceInjectionJndiInfo info;
    public AmbiguousResourceInjectionError(String id, String description, ResourceInjectionJndiInfo info) {
        super(id, description, ErrorLevel.WARN);
        this.info = info;
    }

    public static IdentifiedError create(boolean verbose, String id, String description, ResourceInjectionJndiInfo info) {
        if (verbose) {
            description = description + ": " + info.getInjectionPoint();
        } else {
            description = description + ". " + JndiErrorIdentification.ENABLE_VERBOSE;
        }
        return new AmbiguousResourceInjectionError(id, description, info);
    }

    public ResourceInjectionJndiInfo getInfo() {
        return info;
    }

    @Override
    public String toString() {
        return "AmbiguousResourceInjectionError{" +
                "info=" + info +
                '}';
    }
}
