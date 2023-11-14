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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author jdenise
 */
public class LayerMapping {

    private final Map<String, Set<Layer>> constantPoolClassInfos = new HashMap<>();
    private final Map<String, Set<Layer>> annotations = new HashMap<>();
    private final Map<String, Layer> activeProfilesLayers = new HashMap<>();
    private final Map<String, Set<Layer>> allProfilesLayers = new HashMap<>();
    private Layer defaultBaseLayer;

    private final Set<Layer> layersIncludedIfAllDeps = new TreeSet<>();
    private final Map<Layer, Set<String>> layersIncludedIfSomeDeps = new HashMap<>();

    private final Set<Layer> metadataOnly = new TreeSet<>();
    private final Map<String, Set<AddOn>> addOnFamilyMembers = new TreeMap<>();
    private final Map<String, AddOn> addOns = new HashMap<>();
    private final Map<String, Set<AddOn>> fixedByAddons = new HashMap<>();
    private final Map<String, Integer> addOnsCardinalityInFamily = new HashMap<>();
    private final Map<String, Integer> addOnsCardinalityInDefaultFamily = new HashMap<>();

    private final Map<Layer, String> noConfigurationConditions = new HashMap<>();
    private final Map<Layer, String> hiddenConditions = new HashMap<>();
    /**
     * @return the constantPoolClassInfos
     */
    public Map<String, Set<Layer>> getConstantPoolClassInfos() {
        return constantPoolClassInfos;
    }

    /**
     * @return the annotations
     */
    public Map<String, Set<Layer>> getAnnotations() {
        return annotations;
    }

    /**
     * @return the activeProfilesLayers
     */
    public Map<String, Layer> getActiveProfilesLayers() {
        return activeProfilesLayers;
    }

    /**
     * @return the allProfilesLayers
     */
    public Map<String, Set<Layer>> getAllProfilesLayers() {
        return allProfilesLayers;
    }

    /**
     * @return the defaultBaseLayer
     */
    public Layer getDefaultBaseLayer() {
        return defaultBaseLayer;
    }

    /**
     * @param defaultBaseLayer the defaultBaseLayer to set
     */
    public void setDefaultBaseLayer(Layer defaultBaseLayer) {
        this.defaultBaseLayer = defaultBaseLayer;
    }

    /**
     * @return the layersIncludedIfAllDeps
     */
    public Set<Layer> getLayersIncludedIfAllDeps() {
        return layersIncludedIfAllDeps;
    }

    /**
     * @return the layersIncludedIfSomeDeps
     */
    public Map<Layer, Set<String>> getLayersIncludedIfSomeDeps() {
        return layersIncludedIfSomeDeps;
    }

    /**
     * @return the metadataOnly
     */
    public Set<Layer> getMetadataOnly() {
        return metadataOnly;
    }

    /**
     * @return the addOnFamilyMembers
     */
    public Map<String, Set<AddOn>> getAddOnFamilyMembers() {
        return addOnFamilyMembers;
    }

    /**
     * @return the addOns
     */
    public Map<String, AddOn> getAddOns() {
        return addOns;
    }

    /**
     * @return the fixedByAddons
     */
    public Map<String, Set<AddOn>> getFixedByAddons() {
        return fixedByAddons;
    }

    /**
     * @return the addOnsCardinalityInFamily
     */
    public Map<String, Integer> getAddOnsCardinalityInFamily() {
        return addOnsCardinalityInFamily;
    }

    /**
     * @return the addOnsCardinalityInDefaultFamily
     */
    public Map<String, Integer> getAddOnsCardinalityInDefaultFamily() {
        return addOnsCardinalityInDefaultFamily;
    }

    /**
     * @return the noConfigurationConditions
     */
    public Map<Layer, String> getNoConfigurationConditions() {
        return noConfigurationConditions;
    }

    /**
     * @return the hiddenConditions
     */
    public Map<Layer, String> getHiddenConditions() {
        return hiddenConditions;
    }

    public static String cleanupKey(String key) {
        if (key.startsWith(LayerMetadata.HIDDEN_IF)) {
            key = key.substring(key.indexOf(LayerMetadata.HIDDEN_IF) + LayerMetadata.HIDDEN_IF.length() + 1, key.length());
        } else {
            if (key.startsWith(LayerMetadata.NO_CONFIGURATION_IF)) {
                key = key.substring(key.indexOf(LayerMetadata.NO_CONFIGURATION_IF) + LayerMetadata.NO_CONFIGURATION_IF.length() + 1, key.length());
            }
        }
        return key;
    }

    public static boolean isCondition(String k) {
        return k.startsWith(LayerMetadata.HIDDEN_IF) || k.startsWith(LayerMetadata.NO_CONFIGURATION_IF);
    }
}
