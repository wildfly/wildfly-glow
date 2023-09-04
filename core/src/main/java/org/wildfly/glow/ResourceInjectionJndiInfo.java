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

import java.util.Set;

public class ResourceInjectionJndiInfo {
    private String resourceClassName;
    private final String injectionPoint;
    private final String jndiName;
    private final Set<Layer> layers;

    public ResourceInjectionJndiInfo(Set<Layer> layers, String resourceClassName, String injectionPoint, String jndiName) {
        this.resourceClassName = resourceClassName;
        this.injectionPoint = injectionPoint;
        this.jndiName = jndiName;
        this.layers = layers;
    }

    public String getResourceClassName() {
        return resourceClassName;
    }

    public String getInjectionPoint() {
        return injectionPoint;
    }

    public String getJndiName() {
        return jndiName;
    }

    public Set<Layer> getLayers() {
        return layers;
    }

    @Override
    public String toString() {
        return "ResourceInjectionJndiInfo{" +
                "resourceClassName'" + resourceClassName + '\'' +
                ", injectionPoint='" + injectionPoint + '\'' +
                ", jndiName='" + jndiName + '\'' +
                ", layers=" + layers +
                '}';
    }
}
