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
import org.jboss.galleon.config.ProvisioningConfig;
import org.wildfly.glow.error.ErrorIdentificationSession;

import java.util.Map;
import java.util.Set;

public class ScanResults {

    private final GlowSession glowSession;
    private final Set<Layer> discoveredLayers;
    private final Set<Layer> excludedLayers;
    private final Layer baseLayer;
    private final Set<Layer> decorators;
    private final ProvisioningConfig provisioningConfig;
    private final ErrorIdentificationSession errorSession;
    private final Map<AddOn, String> disabledAddOns;
    private final Set<AddOn> enabledAddOns;
    private final Suggestions suggestions;

    ScanResults(GlowSession glowSession,
            Set<Layer> discoveredLayers,
            Set<Layer> excludedLayers,
            Layer baseLayer,
            Set<Layer> decorators,
            ProvisioningConfig provisioningConfig,
            Set<AddOn> enabledAddOns,
            Map<AddOn, String> disabledAddOns,
            Suggestions suggestions,
            ErrorIdentificationSession errorSession) {
        this.glowSession = glowSession;
        this.discoveredLayers = discoveredLayers;
        this.excludedLayers = excludedLayers;
        this.baseLayer = baseLayer;
        this.decorators = decorators;
        this.provisioningConfig = provisioningConfig;
        this.disabledAddOns = disabledAddOns;
        this.enabledAddOns = enabledAddOns;
        this.suggestions = suggestions;
        this.errorSession = errorSession;
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

    public ProvisioningConfig getProvisioningConfig() {
        return provisioningConfig;
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

    public Path outputConfig(Path target, String dockerImageName) throws Exception {
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
}
