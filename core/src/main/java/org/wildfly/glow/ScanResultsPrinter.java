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

import org.jboss.galleon.config.FeaturePackConfig;
import org.wildfly.glow.error.ErrorLevel;
import org.wildfly.glow.error.IdentifiedError;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
        writer.info("\ngalleon discovery");
        writer.info("- raw basic layers: " + scanResults.getDiscoveredLayers());
        StringBuilder builder = new StringBuilder();
        builder.append("- feature-packs").append("\n");
        for (FeaturePackConfig fp : scanResults.getProvisioningConfig().getFeaturePackDeps()) {
            builder.append("   ").append(fp.getLocation()).append("\n");
        }
        builder.append("- layers").append("\n");
        builder.append("   ").append(scanResults.getBaseLayer()).append("\n");
        for (Layer l : scanResults.getDecorators()) {
            builder.append("   ").append(l.getName()).append("\n");
        }
        if (!scanResults.getExcludedLayers().isEmpty()) {
            builder.append("- excluded-layers\n");
            for (Layer l : scanResults.getExcludedLayers()) {
                builder.append("   ").append(l.getName()).append("\n");
            }
        }
        writer.info(builder);

        if (!scanResults.getEnabledAddOns().isEmpty()) {
            writer.info("enabled add-ons");
            StringBuilder addOnsBuilder = new StringBuilder();
            for (AddOn l : scanResults.getEnabledAddOns()) {
                addOnsBuilder.append("- ").append(l.getName()).append(l.getDescription() != null ? " : " + l.getDescription() : "").append("\n");
            }
            writer.info(addOnsBuilder);
        }
        writer.info("disabled add-ons");
        if (scanResults.getDisabledAddOns().isEmpty()) {
            writer.info("none\n");
        } else {
            StringBuilder disabledBuilder = new StringBuilder();
            for (Map.Entry<AddOn, String> l : scanResults.getDisabledAddOns().entrySet()) {
                disabledBuilder.append("- ").append(l.getKey().getName()).append(": ").append(l.getValue()).append("\n");
            }
            writer.info(disabledBuilder);
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

        writer.info("identified errors");
        if (errorBuilders.isEmpty()) {
            writer.info("none\n");
        } else {
            for (StringBuilder errorBuilder : errorBuilders) {
                writer.error(errorBuilder);
            }
        }
        writer.info("possible issues");
        if (warnBuilders.isEmpty()) {
            writer.info("none\n");
        } else {
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
            writer.warn("strongly suggested configuration");
            for(Map.Entry<Layer, Set<Env>> entry : scanResults.getSuggestions().getStronglySuggestedConfigurations().entrySet()) {
                writer.warn(buildSuggestions(entry.getKey(), entry.getValue()));
            }
        }

        writer.info("suggestions");
        String suggestedConfigs = buildSuggestions(scanResults.getSuggestions().getSuggestedConfigurations());

        if (arguments.isSuggest()) {
            if (scanResults.getSuggestions().getPossibleAddOns().isEmpty() && scanResults.getSuggestions().getPossibleProfiles().isEmpty() && suggestedConfigs.isEmpty()) {
                writer.info("none");
            } else {
                if (!suggestedConfigs.isEmpty()) {
                    writer.info("\n* you could set the following env variables");
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
            if (!scanResults.getSuggestions().getPossibleAddOns().isEmpty() || !scanResults.getSuggestions().getPossibleAddOns().isEmpty() || !suggestedConfigs.isEmpty()) {
                writer.info("some suggestions have been found. You could enable suggestions with --suggest option.");
            } else {
                writer.info("none");
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
        suggestedConfigsBuilder.append("\n").append(layer.getName()).append(":\n");
        for (Env e : envs) {
            suggestedConfigsBuilder.append(" - ").append(e.getName()).append("=").append(e.getDescription()).append("\n");
        }
        return suggestedConfigsBuilder.toString();
    }}
