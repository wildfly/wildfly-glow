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

import org.jboss.galleon.universe.FeaturePackLocation.FPID;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author jdenise
 */
public class Layer implements Comparable<Layer> {

    private final String name;
    private final Set<Layer> dependencies = new TreeSet<>();
    private final Map<String, String> properties = new HashMap<>();
    private final Map<LayerMapping.RULE, Set<String>> matchingRules = new HashMap<>();
    private final Set<FPID> featurePacks = new HashSet<>();
    private AddOn addOn;
    private final Set<String> bringDatasources = new TreeSet<>();
    private boolean isAutomaticInjection;
    private final Set<String> configuration = new TreeSet<>();
    private final Set<String> expectFamilies = new TreeSet<>();
    private boolean banned;

    Layer(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.name);
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
        final Layer other = (Layer) obj;
        return Objects.equals(this.name, other.name);
    }

    @Override
    public int compareTo(Layer t) {
        return name.compareTo(t.name);
    }

    /**
     * @return the dependencies
     */
    public Set<Layer> getDependencies() {
        return dependencies;
    }

    /**
     * @return the properties
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * @return the matching rules
     */
    public Map<LayerMapping.RULE, Set<String>> getMatchingRules() {
        return matchingRules;
    }

    /**
     * @return the featurePacks
     */
    public Set<FPID> getFeaturePacks() {
        return featurePacks;
    }

    /**
     * @return the addOn
     */
    public AddOn getAddOn() {
        return addOn;
    }

    /**
     * @param addOn the addOn to set
     */
    public void setAddOn(AddOn addOn) {
        this.addOn = addOn;
    }

    /**
     * @return the bringDatasources
     */
    public Set<String> getBringDatasources() {
        return bringDatasources;
    }

    /**
     * @return the isAutomaticInjection
     */
    public boolean isIsAutomaticInjection() {
        return isAutomaticInjection;
    }

    /**
     * @param isAutomaticInjection the isAutomaticInjection to set
     */
    public void setIsAutomaticInjection(boolean isAutomaticInjection) {
        this.isAutomaticInjection = isAutomaticInjection;
    }

    /**
     * @return the configuration
     */
    public Set<String> getConfiguration() {
        return configuration;
    }

    /**
     * @return the expectFamily
     */
    public Set<String> getExpectFamilies() {
        return expectFamilies;
    }

    /**
     * @param expectFamily the expectFamily to set
     */
    public void addExpectFamily(String expectFamily) {
        expectFamilies.add(expectFamily);
    }

    /**
     * @return the isBanned
     */
    public boolean isBanned() {
        return banned;
    }

    /**
     * @param isBanned the isBanned to set
     */
    public void setBanned(boolean isBanned) {
        this.banned = isBanned;
    }
}
