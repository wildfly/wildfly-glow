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

import org.wildfly.glow.error.ErrorLevel;
import org.wildfly.glow.error.IdentifiedError;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;

public class ScanResultsPrinter {

    private final GlowMessageWriter writer;

    public ScanResultsPrinter(GlowMessageWriter writer) {

        this.writer = writer;
    }

    void print(ScanArguments arguments, ScanResults scanResults) throws Exception {
        if (arguments.isCompact()) {
            printCompact(arguments, scanResults);
        } else {
            detailed(arguments, scanResults);
        }
    }

    void printCompact(ScanArguments arguments, ScanResults scanResults) throws Exception {
        writer.info(getCompactInformation(arguments, scanResults));
    }

    String getCompactInformation(ScanArguments arguments, ScanResults scanResults) throws Exception {
        StringBuilder compactBuilder = new StringBuilder();
        if (!arguments.getExecutionProfiles().isEmpty()) {
            compactBuilder.append(new TreeSet<>(arguments.getExecutionProfiles()));
        }
        compactBuilder.append(new TreeSet<>(scanResults.getDiscoveredLayers())).append("==>");
        compactBuilder.append(scanResults.getBaseLayer());
        for (Layer l : new TreeSet<>(scanResults.getDecorators())) {
            compactBuilder.append(",").append(l.getName());
        }
        for (Layer l : new TreeSet<>(scanResults.getExcludedLayers())) {
            compactBuilder.append(",-").append(l.getName());
        }
        return compactBuilder.toString();
    }

    private void detailed(ScanArguments arguments, ScanResults scanResults) throws Exception {
        writer.info("context: " + arguments.getExecutionContext());
        StringBuilder profileBuilder = new StringBuilder();
        profileBuilder.append("enabled profile: ");
        if (!arguments.getExecutionProfiles().isEmpty()) {
            for (String p : arguments.getExecutionProfiles()) {
                profileBuilder.append(p);
            }
        } else {
            profileBuilder.append("none");
        }
        writer.info(profileBuilder);
        if(arguments.getConfigStability() != null) {
            writer.info("config stability: " + arguments.getConfigStability().toString());
        }
        if(arguments.getPackageStability() != null) {
            writer.info("package stability: " + arguments.getPackageStability().toString());
        }
        writer.info("galleon discovery");
        StringBuilder builder = new StringBuilder();
        builder.append("- feature-packs").append("\n");
        for (GalleonFeaturePackConfig fp : scanResults.getProvisioningConfig().getFeaturePackDeps()) {
            builder.append("   ").append(fp.getLocation()).append("\n");
        }
        builder.append("- layers").append("\n");
        builder.append("   ").append(scanResults.getBaseLayer()).append("\n");
        for (Layer l : scanResults.getDecorators()) {
            builder.append("   ").append(l.getName() + (scanResults.getExcludedFeatures().containsKey(l) ? " [WARNING: contains content at a lower stability level]" : "")).append("\n");
        }
        if (!scanResults.getExcludedLayers().isEmpty()) {
            builder.append("- excluded-layers\n");
            for (Layer l : scanResults.getExcludedLayers()) {
                builder.append("   ").append(l.getName()).append("\n");
            }
        }
        writer.info(builder);
        if (arguments.isVerbose()) {
            StringBuilder rulesBuilder = new StringBuilder();
            rulesBuilder.append("\nlayers inclusion rules").append("\n");
            rulesBuilder.append("* ").append(scanResults.getBaseLayer()).append("\n");
            for (LayerMapping.RULE rule : scanResults.getBaseLayer().getMatchingRules().keySet()) {
                Set<String> str = scanResults.getBaseLayer().getMatchingRules().get(rule);
                rulesBuilder.append("  - ").append(rule).append((str == null || str.isEmpty()) ? "" : ": " + str).append("\n");
            }
            for (Layer l : scanResults.getDecorators()) {
                rulesBuilder.append("* ").append(l.getName()).append("\n");
                for (LayerMapping.RULE rule : l.getMatchingRules().keySet()) {
                    Set<String> str = l.getMatchingRules().get(rule);
                    rulesBuilder.append("  - ").append(rule).append((str == null || str.isEmpty()) ? "" : ": " + str).append("\n");
                }
            }
            writer.info(rulesBuilder.toString());
        }
        if (!scanResults.getEnabledAddOns().isEmpty()) {
            writer.info("enabled add-ons");
            StringBuilder addOnsBuilder = new StringBuilder();
            for (AddOn l : scanResults.getEnabledAddOns()) {
                addOnsBuilder.append("- ").append(l.getName()).append(l.getDescription() != null ? " : " + l.getDescription() : "").append("\n");
            }
            writer.info(addOnsBuilder);
        }
        if (!scanResults.getDisabledAddOns().isEmpty()) {
            writer.info("disabled add-ons");
            StringBuilder disabledBuilder = new StringBuilder();
            for (Map.Entry<AddOn, String> l : scanResults.getDisabledAddOns().entrySet()) {
                disabledBuilder.append("- ").append(l.getKey().getName()).append(": ").append(l.getValue()).append("\n");
            }
            writer.info(disabledBuilder);
        }
        if (arguments.getConfigStability() != null || arguments.getPackageStability() != null) {
            boolean needCR = false;
            if (!scanResults.getExcludedFeatures().isEmpty()) {
                writer.info("The following features would be disabled if provisioning a server at the '"
                        + arguments.getConfigStability().toString() + "' stability level for configs:");
                needCR = true;
            }
            if(!scanResults.getExcludedPackages().isEmpty()) {
                writer.info("The following packages would be disabled if provisioning a server at the '"
                        + arguments.getPackageStability().toString() + "' stability level for packages:");
                needCR = true;
            }
            if (!scanResults.getExcludedFeatures().isEmpty()) {
                for (Layer l : scanResults.getExcludedFeatures().keySet()) {
                    writer.info(l.getName() + " features:");
                    for (String f : scanResults.getExcludedFeatures().get(l)) {
                        writer.info("- " + f);
                    }
                }
            }
            if (!scanResults.getExcludedPackages().isEmpty()) {
                writer.info("packages:");
                for (String p : scanResults.getExcludedPackages()) {
                    writer.info("- " + p);
                }
            }
            if (needCR) {
                writer.info("");
            }
        }
        List<StringBuilder> fixBuilders = new ArrayList<>();
        List<StringBuilder> errorBuilders = new ArrayList<>();
        List<StringBuilder> warnBuilders = new ArrayList<>();
        for (IdentifiedError error : scanResults.getErrorSession().getErrors()) {
            if (error.isFixed()) {
                StringBuilder fixBuilder = new StringBuilder();
                fixBuilder.append("* ").append(error.getDescription()).append(" is fixed\n");
                fixBuilder.append("  - ").append(error.getFixMessage()).append("\n");
                fixBuilders.add(fixBuilder);
            } else {
                StringBuilder errorBuilder = new StringBuilder();
                errorBuilder.append("* ").append(error.getDescription()).append("\n");
                if (!error.getPossibleAddons().isEmpty()) {
                    errorBuilder.append("  To correct this error, enable one of the following add-ons:\n");
                    for (AddOn addOn : error.getPossibleAddons()) {
                        errorBuilder.append("  - ").append(addOn.getName()).append("\n");
                    }
                }
                if (error.getErrorLevel() == ErrorLevel.ERROR) {
                    errorBuilders.add(errorBuilder);
                } else {
                    warnBuilders.add(errorBuilder);
                }
            }
        }

        if (!errorBuilders.isEmpty()) {
            writer.info("identified errors");
            for (StringBuilder errorBuilder : errorBuilders) {
                writer.error(errorBuilder);
            }
        }

        if (!warnBuilders.isEmpty()) {
            writer.info("possible issues");
            for (StringBuilder warnBuilder : warnBuilders) {
                writer.warn(warnBuilder);
            }
        }
        if (!fixBuilders.isEmpty()) {
            writer.info("identified fixes");
            for (StringBuilder fixBuilder : fixBuilders) {
                writer.info(fixBuilder);
            }
        }

        if (!scanResults.getSuggestions().getStronglySuggestedConfigurations().isEmpty()) {
            writer.warn("strongly suggested configuration at runtime");
            for(Map.Entry<Layer, Set<Env>> entry : scanResults.getSuggestions().getStronglySuggestedConfigurations().entrySet()) {
                writer.warn(buildSuggestions(entry.getKey(), entry.getValue()));
            }
            writer.warn("");
        }

        if (!scanResults.getSuggestions().getBuildTimeRequiredConfigurations().isEmpty()) {
            writer.warn("configuration that must be set at provisioning time");
            for(Map.Entry<Layer, Set<Env>> entry : scanResults.getSuggestions().getBuildTimeRequiredConfigurations().entrySet()) {
                writer.warn(buildSuggestions(entry.getKey(), entry.getValue()));
            }
            writer.warn("");
        }

        String suggestedConfigs = buildSuggestions(scanResults.getSuggestions().getSuggestedConfigurations());
        String suggestedBuildTimeConfigs = buildSuggestions(scanResults.getSuggestions().getBuildTimeConfigurations());

        if (arguments.isSuggest()) {
            writer.info("suggestions");
            if (scanResults.getSuggestions().getPossibleAddOns().isEmpty() && scanResults.getSuggestions().getPossibleProfiles().isEmpty() && suggestedConfigs.isEmpty() && suggestedBuildTimeConfigs.isEmpty()) {
                writer.info("none");
            } else {
                if (!suggestedBuildTimeConfigs.isEmpty()) {
                    writer.info("\n* you could set the following configuration at provisioning time");
                    writer.info(suggestedBuildTimeConfigs);
                }
                if (!suggestedConfigs.isEmpty()) {
                    writer.info("\n* you could set the following configuration at runtime");
                    writer.info(suggestedConfigs);
                }
                if (!scanResults.getSuggestions().getPossibleAddOns().isEmpty()) {
                    writer.info("* you could enable the following add-ons:");
                    Map<String, Set<AddOn>> sortedAddOns = new TreeMap<>();
                    for (AddOn addOn : scanResults.getSuggestions().getPossibleAddOns()) {
                        Set<AddOn> addons = sortedAddOns.get(addOn.getFamily());
                        if (addons == null) {
                            addons = new TreeSet<>();
                            sortedAddOns.put(addOn.getFamily(), addons);
                        }
                        addons.add(addOn);
                    }
                    StringBuilder possibleBuilder = new StringBuilder();
                    for (String family : sortedAddOns.keySet()) {
                        possibleBuilder.append("  - ").append(family).append(" add-ons:\n");
                        for (AddOn l : sortedAddOns.get(family)) {
                            possibleBuilder.append("    - ").append(l.getName()).append(l.getDescription() != null ? " : "
                                    + l.getDescription() : "").append("\n");
                        }
                    }
                    writer.info(possibleBuilder);
                }
                if (!scanResults.getSuggestions().getPossibleProfiles().isEmpty()) {
                    writer.info("* you could enable profiles:");
                    StringBuilder profilesBuilder = new StringBuilder();
                    for (String l : scanResults.getSuggestions().getPossibleProfiles()) {
                        profilesBuilder.append("  - ").append(l).append("\n");
                    }
                    writer.info(profilesBuilder);
                }
            }
        } else {
            if (!scanResults.getSuggestions().getPossibleAddOns().isEmpty() || !scanResults.getSuggestions().getPossibleAddOns().isEmpty() || !suggestedConfigs.isEmpty() || !suggestedBuildTimeConfigs.isEmpty()) {
                writer.info("Some suggestions have been found. You could enable suggestions with the " + (arguments.isCli() ? "--suggest" : "<suggest>true</suggest>") + " option.");
            }
        }
    }

    private static String buildSuggestions(Map<Layer, Set<Env>> map) throws URISyntaxException, IOException {
        StringBuilder suggestedConfigsBuilder = new StringBuilder();
        for (Layer l : map.keySet()) {
            suggestedConfigsBuilder.append(buildSuggestions(l, map.get(l)));
        }
        return suggestedConfigsBuilder.toString();
    }

    private static String buildSuggestions(Layer layer, Set<Env> envs) throws URISyntaxException, IOException {
        StringBuilder suggestedConfigsBuilder = new StringBuilder();
        Set<Env> envVars = new TreeSet<>();
        Set<Env> properties = new TreeSet<>();
        Iterator<Env> it = envs.iterator();
        while (it.hasNext()) {
            Env e = it.next();
            if (e.isProperty()) {
                properties.add(e);
            } else {
                envVars.add(e);
            }
        }
        if (!envVars.isEmpty()) {
            suggestedConfigsBuilder.append("\n").append(layer.getName()).append(" environment variables:\n");
            Iterator<Env> it2 = envVars.iterator();
            while (it2.hasNext()) {
                Env e = it2.next();
                suggestedConfigsBuilder.append(" - ").append(e.getName()).append("=").append(e.getDescription());
                if (it2.hasNext()) {
                    suggestedConfigsBuilder.append("\n");
                }
            }
        }
        if (!properties.isEmpty()) {
            suggestedConfigsBuilder.append("\n").append(layer.getName()).append(" system properties:\n");
            Iterator<Env> it2 = properties.iterator();
            while (it2.hasNext()) {
                Env e = it2.next();
                suggestedConfigsBuilder.append(" -D").append(e.getName()).append("=").append(e.getDescription());
                if (it2.hasNext()) {
                    suggestedConfigsBuilder.append("\n");
                }
            }
        }
        return suggestedConfigsBuilder.toString();
    }}
