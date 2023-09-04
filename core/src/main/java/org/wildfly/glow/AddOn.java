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
package org.wildfly.glow;

import org.wildfly.glow.error.Fix;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author jdenise
 */
public class AddOn implements Comparable<AddOn> {

    private final String name;
    private final String family;
    private final String description;
    private final Set<Layer> layersAlwaysIncluded = new TreeSet<>();
    private final Set<Layer> layers = new TreeSet<>();
    private final Set<Layer> layersThatExpectAllDependencies = new TreeSet<>();
    private final Map<Layer, Set<Layer>> layersThatExpectSomeDependencies = new HashMap<>();
    private final Map<String, Fix> fixes = new HashMap<>();
    private final boolean isDefault;
    private final String associatedNonDefault;
    AddOn(String name, String family, String description) {
        this.name = name;
        this.family = family;
        this.description = description;
        isDefault = name.endsWith(":default");
        if(isDefault) {
            int i = name.lastIndexOf(":");
            associatedNonDefault = name.substring(0, i);
        } else {
            associatedNonDefault = name;
        }

    }

    public boolean isDefault() {
        return isDefault;
    }

    public String getAssociatedNonDefault() {
        return associatedNonDefault;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the famility
     */
    public String getFamily() {
        return family;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the layers
     */
    public Set<Layer> getLayers() {
        return layers;
    }

    /**
     * @return the fixes
     */
    public Map<String, Fix> getFixes() {
        return fixes;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + Objects.hashCode(this.name);
        hash = 59 * hash + Objects.hashCode(this.family);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AddOn other = (AddOn) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return Objects.equals(this.family, other.family);
    }

    /**
     * @return the layersThatExpectAllDependencies
     */
    public Set<Layer> getLayersThatExpectAllDependencies() {
        return layersThatExpectAllDependencies;
    }

    /**
     * @return the layersThatExpectSomeDependencies
     */
    public Map<Layer, Set<Layer>> getLayersThatExpectSomeDependencies() {
        return layersThatExpectSomeDependencies;
    }

    @Override
    public int compareTo(AddOn t) {
        return name.compareTo(t.name);
    }

    /**
     * @return the layersAlwaysIncluded
     */
    public Set<Layer> getLayersAlwaysIncluded() {
        return layersAlwaysIncluded;
    }

}
