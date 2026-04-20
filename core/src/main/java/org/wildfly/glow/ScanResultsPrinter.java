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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.wildfly.glow.ConfigurationResolver.ResolvedEnvs;

public class ScanResultsPrinter {

    private final GlowMessageWriter writer;
    private final ConfigurationResolver configResolver;

    public ScanResultsPrinter(GlowMessageWriter writer) {
        this(writer, null);
    }

    public ScanResultsPrinter(GlowMessageWriter writer, ConfigurationResolver configResolver) {
        this.writer = writer;
        this.configResolver = configResolver;
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
        if(arguments.getServerVariant() != null) {
            writer.info("WildFly variant: " + arguments.getServerVariant());
        }
        writer.info("galleon discovery");
        writer.info("- feature-packs");
        for (GalleonFeaturePackConfig fp : scanResults.getProvisioningConfig().getFeaturePackDeps()) {
            FPID fpid = scanResults.getFeaturePackVersions().get(fp.getLocation().getProducer());
            writer.info("   " + (fpid == null ? fp.getLocation() : fpid));
        }
        writer.info("- layers");
        writer.info("   " + scanResults.getBaseLayer());
        for (Layer l : scanResults.getDecorators()) {
            writer.info("   " + l.getName() + (scanResults.getExcludedFeatures().containsKey(l) ? " [WARNING: contains content at a lower stability level]" : ""));
        }
        if (!scanResults.getExcludedLayers().isEmpty()) {
            writer.info("- excluded-layers");
            for (Layer l : scanResults.getExcludedLayers()) {
                writer.info("   " + l.getName());
            }
        }
        if (arguments.isVerbose()) {
            writer.info(" ");
            writer.info("layers inclusion rules");
            writer.info("* " + scanResults.getBaseLayer());
            for (LayerMapping.RULE rule : scanResults.getBaseLayer().getMatchingRules().keySet()) {
                Set<String> str = scanResults.getBaseLayer().getMatchingRules().get(rule);
                writer.info("  - " + rule + ((str == null || str.isEmpty()) ? "" : ": " + str));
            }
            for (Layer l : scanResults.getDecorators()) {
                writer.info("* " + l.getName());
                for (LayerMapping.RULE rule : l.getMatchingRules().keySet()) {
                    Set<String> str = l.getMatchingRules().get(rule);
                    writer.info("  - " + rule + ((str == null || str.isEmpty()) ? "" : ": " + str));
                }
            }
        }
        if (!scanResults.getEnabledAddOns().isEmpty()) {
            writer.info("enabled add-ons");
            for (AddOn l : scanResults.getEnabledAddOns()) {
                writer.info("- " + l.getName() + (l.getDescription() != null ? " : " + l.getDescription() : ""));
            }
        }
        if (!scanResults.getDisabledAddOns().isEmpty()) {
            writer.info("disabled add-ons");
            for (Map.Entry<AddOn, String> l : scanResults.getDisabledAddOns().entrySet()) {
                writer.info("- " + l.getKey().getName() + ": " + l.getValue());
            }
        }
        List<String> fixBuilders = new ArrayList<>();
        List<String> errorBuilders = new ArrayList<>();
        List<String> warnBuilders = new ArrayList<>();
        for (IdentifiedError error : scanResults.getErrorSession().getErrors()) {
            if (error.isFixed()) {
                fixBuilders.add("* " + error.getDescription() + " is fixed");
                fixBuilders.add("  - " + error.getFixMessage());
            } else {
                List<String> errorMessages;
                if (error.getErrorLevel() == ErrorLevel.ERROR) {
                    errorMessages = errorBuilders;
                } else {
                    errorMessages = warnBuilders;
                }
                errorMessages.add("* " + error.getDescription());
                if (!error.getUnverifiedFixes().isEmpty()) {
                    errorMessages.add("  The following suggestions should help you fix the reported warnings:");
                    for (String unverifiedFix : error.getUnverifiedFixes()) {
                        errorMessages.add("  - " + unverifiedFix);
                    }
                }
                if (!error.getPossibleAddons().isEmpty()) {
                    StringBuilder errorBuilder = new StringBuilder();
                    errorBuilder.append("  Enabling one of the following add-ons may ");
                    if (!error.getUnverifiedFixes().isEmpty()) {
                        errorBuilder.append("also ");
                    }
                    errorBuilder.append("fix this issue:");
                    errorMessages.add(errorBuilder.toString());
                    for (AddOn addOn : error.getPossibleAddons()) {
                        String deployer = configResolver == null ? null : configResolver.getPossibleDeployer(addOn.getLayers());
                        errorMessages.add("  - " + addOn.getName() + (deployer == null ? "" : " (supported by "+deployer+" deployer)"));
                    }
                }
            }
        }

        if (!errorBuilders.isEmpty()) {
            writer.info("identified errors");
            for (String errorBuilder : errorBuilders) {
                writer.error(errorBuilder);
            }
        }

        if (!warnBuilders.isEmpty()) {
            writer.warn("some potential issues have been found");
            for (String warnBuilder : warnBuilders) {
                writer.warn(warnBuilder);
            }
        }
        if (!fixBuilders.isEmpty()) {
            writer.info("identified fixes");
            for (String fixBuilder : fixBuilders) {
                writer.info(fixBuilder);
            }
        }

        if (configResolver != null) {
            Set<String> deployers = new TreeSet<>();
            for (Layer l : scanResults.getDiscoveredLayers()) {
                String deployer = configResolver.getPossibleDeployer(l);
                if (deployer != null) {
                    deployers.add(deployer);
                }
            }
            for (Layer l : scanResults.getMetadataOnlyLayers()) {
                String deployer = configResolver.getPossibleDeployer(l);
                if (deployer != null) {
                    deployers.add(deployer);
                }
            }
            if (!deployers.isEmpty()) {
                writer.info("deployers that would get automatically enabled when deploying to openshift");
                for (String deployer : deployers) {
                    writer.info("- " + deployer);
                }
                writer.info("");
            }
        }

        if (!scanResults.getSuggestions().getStronglySuggestedConfigurations().isEmpty()) {
            writer.warn("strongly suggested configuration at runtime");
            for(Map.Entry<Layer, Set<Env>> entry : scanResults.getSuggestions().getStronglySuggestedConfigurations().entrySet()) {
                List<String> lst = buildSuggestions(entry.getKey(), entry.getValue());
                for(String s : lst) {
                    writer.warn(s);
                }
            }
            writer.warn("");
        }

        if (!scanResults.getSuggestions().getBuildTimeRequiredConfigurations().isEmpty()) {
            writer.warn("configuration that must be set at provisioning time");
            for(Map.Entry<Layer, Set<Env>> entry : scanResults.getSuggestions().getBuildTimeRequiredConfigurations().entrySet()) {
                List<String> lst = buildSuggestions(entry.getKey(), entry.getValue());
                for(String s : lst) {
                    writer.warn(s);
                }
            }
            writer.warn("");
        }
        if (arguments.getDefaultConfigStability() != null || arguments.getConfigStability() != null || arguments.getPackageStability() != null) {
            boolean needCR = false;
            if (!scanResults.getExcludedFeatures().isEmpty()) {
                String msg = arguments.getConfigStability() == null ? "" : " at the '" + arguments.getConfigStability() + "' stability level";
                writer.warn("The following features would be disabled if provisioning a server" + msg + ". Make sure to set the '--config-stability-level=<features expected lowest stability level>' option:");
                needCR = true;
                for (Layer l : scanResults.getExcludedFeatures().keySet()) {
                    writer.warn(l.getName() + " features:");
                    for (String f : scanResults.getExcludedFeatures().get(l)) {
                        writer.warn("- " + f);
                    }
                }
            }
            if (!scanResults.getExcludedPackages().isEmpty()) {
                writer.warn("The following packages would be disabled if provisioning a server at the '"
                        + arguments.getPackageStability() + "' stability level for packages:");
                needCR = true;
                writer.warn("packages:");
                for (String p : scanResults.getExcludedPackages()) {
                    writer.warn("- " + p);
                }
            }
            if (needCR) {
                writer.info("");
            }
        }
        List<String> suggestedConfigs = buildSuggestions(scanResults.getSuggestions().getSuggestedConfigurations());
        List<String> suggestedBuildTimeConfigs = buildSuggestions(scanResults.getSuggestions().getBuildTimeConfigurations());

        if (arguments.isSuggest()) {
            writer.info("suggestions");
            if (scanResults.getSuggestions().getPossibleAddOns().isEmpty() && scanResults.getSuggestions().getPossibleProfiles().isEmpty() && suggestedConfigs.isEmpty() && suggestedBuildTimeConfigs.isEmpty()) {
                writer.info("none");
            } else {
                if (!suggestedBuildTimeConfigs.isEmpty()) {
                    writer.info(" ");
                    writer.info("* you could set the following configuration at provisioning time");
                    for(String s : suggestedBuildTimeConfigs) {
                        writer.info(s);
                    }
                }
                if (!suggestedConfigs.isEmpty()) {
                    writer.info(" ");
                    writer.info("* you could set the following configuration at runtime");
                    for(String s : suggestedConfigs) {
                        writer.info(s);
                    }
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
                    for (String family : sortedAddOns.keySet()) {
                        writer.info("  - " + family + " add-ons:");
                        for (AddOn l : sortedAddOns.get(family)) {
                            writer.info("    - " + l.getName() + (l.getDescription() != null ? " : "
                                    + l.getDescription() : ""));
                        }
                    }
                }
                if (!scanResults.getSuggestions().getPossibleProfiles().isEmpty()) {
                    writer.info("* you could enable profiles:");
                    for (String l : scanResults.getSuggestions().getPossibleProfiles()) {
                        writer.info("  - " + l);
                    }
                }
            }
        } else {
            if (!scanResults.getSuggestions().getPossibleAddOns().isEmpty() || !scanResults.getSuggestions().getPossibleAddOns().isEmpty() || !suggestedConfigs.isEmpty() || !suggestedBuildTimeConfigs.isEmpty()) {
                writer.info("Some suggestions have been found. You could enable suggestions with the " + (arguments.isCli() ? "--suggest" : "<suggest>true</suggest>") + " option.");
            }
        }
    }

    private List<String> buildSuggestions(Map<Layer, Set<Env>> map) throws Exception {
        List<String> suggestedConfigsBuilder = new ArrayList<>();
        for (Layer l : map.keySet()) {
            suggestedConfigsBuilder.addAll(buildSuggestions(l, map.get(l)));
        }
        return suggestedConfigsBuilder;
    }

    private List<String> buildSuggestions(Layer layer, Set<Env> envs) throws Exception {
        List<String> suggestedConfigsBuilder = new ArrayList<>();
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
            suggestedConfigsBuilder.add(" ");
            suggestedConfigsBuilder.add(layer.getName() + " environment variables:");
            if (configResolver != null) {
                ResolvedEnvs resolvedEnvs = configResolver.getResolvedEnvs(layer, envVars);
                if (resolvedEnvs != null) {
                    envVars.removeAll(resolvedEnvs.getEnvs());
                    if (envVars.isEmpty()) {
                        suggestedConfigsBuilder.add(" - Resolver " + resolvedEnvs.getName() + " resolved all env variables.");
                    } else {
                        suggestedConfigsBuilder.add(" - Resolver " + resolvedEnvs.getName() + " resolved the following env variables:");
                        for (Env env : resolvedEnvs.getEnvs()) {
                            suggestedConfigsBuilder.add("  - " + env.getName());

                        }
                    }
                }
            }
            Iterator<Env> it2 = envVars.iterator();
            while (it2.hasNext()) {
                Env e = it2.next();
                suggestedConfigsBuilder.add(" - " + e.getName() + "=" + e.getDescription());
            }
        }
        if (!properties.isEmpty()) {
            suggestedConfigsBuilder.add(" ");
            suggestedConfigsBuilder.add(layer.getName() + " system properties:");
            Iterator<Env> it2 = properties.iterator();
            while (it2.hasNext()) {
                Env e = it2.next();
                suggestedConfigsBuilder.add(" -D" + e.getName() + "=" + e.getDescription());
            }
        }
        return suggestedConfigsBuilder;
    }}
