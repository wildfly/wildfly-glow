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

import java.nio.file.Path;
import org.wildfly.glow.error.ErrorIdentificationSession;

import java.util.Map;
import java.util.Set;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;

public class ScanResults implements AutoCloseable {

    private final GlowSession glowSession;
    private final Set<Layer> discoveredLayers;
    private final Set<Layer> excludedLayers;
    private final Layer baseLayer;
    private final Set<Layer> decorators;
    private final Provisioning provisioning;
    private final GalleonProvisioningConfig config;
    private final ErrorIdentificationSession errorSession;
    private final Map<AddOn, String> disabledAddOns;
    private final Set<AddOn> enabledAddOns;
    private final Suggestions suggestions;
    private final Set<String> excludedPackages;
    private final Map<Layer, Set<String>> excludedFeatures;
    ScanResults(GlowSession glowSession,
            Set<Layer> discoveredLayers,
            Set<Layer> excludedLayers,
            Layer baseLayer,
            Set<Layer> decorators,
            Provisioning provisioning,
            GalleonProvisioningConfig config,
            Set<AddOn> enabledAddOns,
            Map<AddOn, String> disabledAddOns,
            Suggestions suggestions,
            ErrorIdentificationSession errorSession,
            Set<String> excludedPackages,
            Map<Layer, Set<String>> excludedFeatures) {
        this.glowSession = glowSession;
        this.discoveredLayers = discoveredLayers;
        this.excludedLayers = excludedLayers;
        this.baseLayer = baseLayer;
        this.decorators = decorators;
        this.provisioning = provisioning;
        this.config = config;
        this.disabledAddOns = disabledAddOns;
        this.enabledAddOns = enabledAddOns;
        this.suggestions = suggestions;
        this.errorSession = errorSession;
        this.excludedPackages = excludedPackages;
        this.excludedFeatures = excludedFeatures;
    }

    public Set<Layer> getDiscoveredLayers() {
        return discoveredLayers;
    }

    public Set<Layer> getExcludedLayers() {
        return excludedLayers;
    }

    public Layer getBaseLayer() {
        return baseLayer;
    }

    public Set<Layer> getDecorators() {
        return decorators;
    }

    public Provisioning getProvisioning() {
        return provisioning;
    }
    public GalleonProvisioningConfig getProvisioningConfig() {
        return config;
    }

    public Suggestions getSuggestions() {
        return suggestions;
    }

    public ErrorIdentificationSession getErrorSession() {
        return errorSession;
    }

    public Map<AddOn, String> getDisabledAddOns() {
        return disabledAddOns;
    }

    public Set<AddOn> getEnabledAddOns() {
        return enabledAddOns;
    }

    public OutputContent outputConfig(Path target, String dockerImageName) throws Exception {
        return glowSession.outputConfig(this, target, dockerImageName);
    }

    public void outputInformation() throws Exception {
        outputInformation(GlowMessageWriter.DEFAULT);
    }

    public void outputCompactInformation() throws Exception {
        outputCompactInformation(GlowMessageWriter.DEFAULT);
    }

    public void outputInformation(GlowMessageWriter writer) throws Exception {
        ScanResultsPrinter printer = new ScanResultsPrinter(writer);
        glowSession.outputInformation(printer, this);
    }

    public void outputCompactInformation(GlowMessageWriter writer) throws Exception {
        ScanResultsPrinter printer = new ScanResultsPrinter(writer);
        glowSession.outputCompactInformation(printer, this);
    }

    public String getCompactInformation() throws Exception {
        ScanResultsPrinter printer = new ScanResultsPrinter(GlowMessageWriter.DEFAULT);
        return glowSession.getCompactInformation(printer, this);
    }

    @Override
    public void close() {
        provisioning.close();
    }

    public Set<String> getExcludedPackages() {
        return excludedPackages;
    }

    public Map<Layer, Set<String>> getExcludedFeatures() {
        return excludedFeatures;
    }
}
