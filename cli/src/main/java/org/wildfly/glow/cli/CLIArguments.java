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
package org.wildfly.glow.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.FeaturePacks;
import static org.wildfly.glow.GlowSession.STANDALONE_PROFILE;
import org.wildfly.glow.Layer;
import org.wildfly.glow.LayerMapping;
import org.wildfly.glow.OutputFormat;
import org.wildfly.glow.error.JndiErrorIdentification;

/**
 *
 * @author jdenise
 */
public class CLIArguments extends Arguments {

    private static final String EXECUTION_CONTEXT_OPTION = "--context";
    private static final String DISPLAY_CONFIGURATION_OPTION = "--display-configuration";
    public static final String HELP_OPTION = "--help";
    public static final String VERSION_OPTION = "--version";
    private static final String SERVER_VERSION_OPTION = "--server-version";
    private static final String GO_OFFLINE_OPTION = "--go-offline";
    private static final String SUGGESTION_OPTION = "--suggest";
    private static final String PREVIEW_OPTION = "--preview";
    private static final String OUTPUT_OPTION = "--output";
    private static final String FEATURE_PACKS_FILE_OPTION = "--feature-packs-file";
    private static final String PROFILE_OPTION = "--profile";
    private static final String ADDONS_OPTION = "--add-ons";
    private static final String VERBOSE_OPTION = "--verbose";
    private static final String JNDI_LAYERS_ARGUMENT = JndiErrorIdentification.JNDI_LAYERS_ARGUMENT;
    private static final String JNDI_LAYERS_ARGUMENT_EXPLANATION = JndiErrorIdentification.JNDI_LAYERS_ARGUMENT_EXPLANATION;
    private final boolean goOffline;
    private final boolean help;
    private final boolean version;
    private final boolean displayConfigurationInfo;
    public CLIArguments(
            boolean version,
            boolean help,
            String executionContext,
            Set<String> executionProfiles,
            Set<String> userEnabledAddOns,
            List<Path> binaries,
            Path provisioningXML,
            OutputFormat output,
            boolean suggest,
            boolean goOffline,
            String serverVersion,
            Set<String> layersForJndi,
            boolean verbose,
            boolean techPreview,
            boolean displayConfigurationInfo) {
        super(executionContext,
                executionProfiles,
                userEnabledAddOns,
                binaries,
                provisioningXML,
                output,
                suggest,
                serverVersion,
                STANDALONE_XML,
                layersForJndi,
                verbose,
                techPreview);
        this.goOffline = goOffline;
        this.help = help;
        this.version = version;
        this.displayConfigurationInfo = displayConfigurationInfo;
    }

    public static CLIArguments fromMainArguments(String[] args) throws Exception {
        String executionContext = null;
        List<Path> binaries = new ArrayList<>();
        Set<String> executionProfiles = new TreeSet<>();
        Set<String> userEnabledAddOns = new TreeSet<>();
        Set<String> layersForJndi = new TreeSet<>();
        Path provisioningXML = null;
        OutputFormat output = null;
        boolean goOffline = false;
        boolean suggest = false;
        String version = null;
        boolean help = false;
        boolean isVersion = false;
        boolean verbose = false;
        boolean preview = false;
        boolean displayConfigurationInfo = false;
        if (args.length == 0) {
            return new CLIArguments(isVersion, help, executionContext,
                    executionProfiles, userEnabledAddOns, binaries,
                    provisioningXML, output, suggest, goOffline, version, layersForJndi,
                    verbose, preview, displayConfigurationInfo);
        }
        for (String a : args) {
            if (a.startsWith("--")) {
                if (a.equals(DISPLAY_CONFIGURATION_OPTION)) {
                    displayConfigurationInfo = true;
                    continue;
                }
                if (a.equals(PREVIEW_OPTION)) {
                    preview = true;
                    continue;
                }
                if (a.equals(HELP_OPTION)) {
                    help = true;
                    continue;
                }
                if (a.equals(VERSION_OPTION)) {
                    isVersion = true;
                    continue;
                }
                if (a.equals(SUGGESTION_OPTION)) {
                    suggest = true;
                    continue;
                }
                if (a.equals(GO_OFFLINE_OPTION)) {
                    goOffline = true;
                    continue;
                }
                if (a.startsWith(SERVER_VERSION_OPTION)) {
                    version = parseArgStringValue(a);
                    continue;
                }
                if (a.startsWith(OUTPUT_OPTION)) {
                    int i = a.indexOf("=");
                    String type = parseArgStringValue(a);
                    for (OutputFormat of : EnumSet.allOf(OutputFormat.class)) {
                        if (of.name.equals(type)) {
                            output = of;
                            break;
                        }
                    }
                    if (output == null) {
                        throw new Exception("Invalid output format " + type);
                    }
                    continue;
                }
                if (a.startsWith(EXECUTION_CONTEXT_OPTION)) {
                    executionContext = parseArgStringValue(a);
                    if (!CLOUD_EXECUTION_CONTEXT.equals(executionContext) && !BARE_METAL_EXECUTION_CONTEXT.equals(executionContext)) {
                        throw new Exception("Unknown execution context " + executionContext);
                    }
                    continue;
                }
                if (a.startsWith(FEATURE_PACKS_FILE_OPTION)) {
                    provisioningXML = Paths.get(parseArgStringValue(a));
                    if (!Files.exists(provisioningXML)) {
                        throw new Exception("Provisioning file " + provisioningXML + "doesn't exist");
                    }
                    continue;
                }
                if (a.startsWith(PROFILE_OPTION)) {
                    List<String> split = parseArgStringArrayValue(a);
                    for (String s : split) {
                        s = s.trim();
                        if (!s.isEmpty()) {
                            if (!STANDALONE_PROFILE.equals(s)) {
                                executionProfiles.add(s);
                            }
                        }
                    }
                    continue;
                }
                if (a.startsWith(ADDONS_OPTION)) {
                    userEnabledAddOns.addAll(parseArgStringArrayValue(a));
                    continue;
                }
                if (a.startsWith(JNDI_LAYERS_ARGUMENT)) {
                    layersForJndi.addAll(parseArgStringArrayValue(a));
                    continue;
                }
                if (a.equals(VERBOSE_OPTION)) {
                    verbose = true;
                    continue;
                }
                throw new Exception("Unknown Option " + a);
            } else {
                List<String> split = Arrays.asList(a.split(","));
                for(String s : split) {
                    binaries.add(Paths.get(s));
                }
            }
        }
        if (!isVersion && !help && !goOffline && binaries.isEmpty() && userEnabledAddOns.isEmpty() && !displayConfigurationInfo) {
            throw new Exception("No deployment to scan nor add-ons have been set");
        }
        return new CLIArguments(isVersion, help, executionContext, executionProfiles, userEnabledAddOns, binaries,
                provisioningXML, output, suggest, goOffline, version, layersForJndi, verbose, preview, displayConfigurationInfo);
    }

    private static String parseArgStringValue(String arg) {
        int i = arg.indexOf("=");
        return arg.substring(i + 1);
    }

    private static List<String> parseArgStringArrayValue(String arg) {
        String value =  parseArgStringValue(arg);
        return Arrays.asList(value.split(","));
    }


    public static void dumpInfos(Set<String> profiles) throws Exception {

        StringBuilder builder = new StringBuilder();
        builder.append("\nDescription\n");
        builder.append("\nglow helps you create a WildFly server based on the content of your WAR/JAR/EAR deployment(s).\n");
        builder.append("glow will identify the required Galleon feature-packs and layers.\n"
                + "glow will identify possible configuration errors and will help you fix them.\n"
                + "glow will identify and suggest WildFly add-ons to extend the server capabilities.\n"
                + "glow will provision a server or a bootable jar with your application deployed in it.\n"
                + "You can choose to generate an hollow server or bootable jar (with no deployment) by only selecting a set of add-ons.\n");

        builder.append("\nUsage:\n");
        builder.append("\njava -jar glow.jar [<comma separated list of path to war|jar|ear>] [options]\n");

        builder.append("\nOptions:\n");

        builder.append("\n" + JNDI_LAYERS_ARGUMENT + "=<list of layers>\n");
        builder.append("\n "+ JNDI_LAYERS_ARGUMENT_EXPLANATION +"\n");

        builder.append("\n" + ADDONS_OPTION + "=<comma separated list of add-ons>\n");
        builder.append("\n List of add-ons to enable. To get the list of possible add-ons, use the " + DISPLAY_CONFIGURATION_OPTION + " option.\n");

        builder.append("\n" + DISPLAY_CONFIGURATION_OPTION + "\n");
        builder.append("\n Display the configuration information (known feature-packs, add-ons and layers) for a given execution context (bare-metal by default, set "
                + EXECUTION_CONTEXT_OPTION + "=<context> option to change it), server version (latest by default, set " +
                SERVER_VERSION_OPTION + "=<server version> option to change it) and preview (false by default, set " + PREVIEW_OPTION +
                " option to change it)\n");

        builder.append("\n" + EXECUTION_CONTEXT_OPTION + "=<execution context>\n");
        builder.append("\n Possible execution context:\n");
        builder.append("  - " + BARE_METAL_EXECUTION_CONTEXT + " (default)\n");
        builder.append("  - " + CLOUD_EXECUTION_CONTEXT + "\n");

        builder.append("\n" + FEATURE_PACKS_FILE_OPTION + "=<path to provisioning.xml file>\n");
        builder.append("\n This file contains the feature-packs used by glow during scanning. By default the URL ").append(FeaturePacks.getFeaturePacksURL()).append(" contains the list of known feature-packs for each server version.\n");

        builder.append("\n" + GO_OFFLINE_OPTION + "\n");
        builder.append("\n Glow will generate a zip file containing all that is required to run the tool in offline mode. Put this zip in the "
                + "working directory of glow to work offline.\n");

        builder.append("\n "+ HELP_OPTION +"\n");
        builder.append("\n Print this help content.\n");

        builder.append("\n" + OUTPUT_OPTION + "=<type of output>\n");
        builder.append("\n Possible output formats (by default, glow scanning results are printed in the console):\n");
        Set<OutputFormat> set = EnumSet.allOf(OutputFormat.class);
        Set<String> sorted = new TreeSet<>();
        for (OutputFormat o : set) {
            sorted.add(o.name + ": " + o.description);
        }
        for (String s : sorted) {
            builder.append("  - ").append(s).append("\n");
        }

        builder.append("\n"+ PREVIEW_OPTION +"\n");
        builder.append("\n Use only preview feature-packs as input.\n");

        builder.append("\n" + PROFILE_OPTION + "=<profile>\n");
        builder.append("\n Possible execution profile (default non HA):\n");
        for (String p : profiles) {
            builder.append("  - ").append(p).append("\n");
        }

        builder.append("\n" + SERVER_VERSION_OPTION + "=<server version>\n");
        builder.append("\n Glow works with the latest version of the server by default. Supported versions: ").append(FeaturePacks.getAllVersions()).append(".\n");

        builder.append("\n" + SUGGESTION_OPTION + "\n");
        builder.append("\n Glow will suggest add-ons and usable env variables.\n");

        builder.append("\n" + VERBOSE_OPTION + "\n");
        builder.append("\n Enable more verbose output of errors\n");

        builder.append("\n "+ VERSION_OPTION +"\n");
        builder.append("\n Print the tool version.\n");
        System.out.println(builder);
    }

    public static String dumpConfiguration(Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies, String context, String serverVersion, Map<String, Layer> allLayers,
            LayerMapping mapping, ProvisioningConfig fps, boolean isLatest, boolean techPreview) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append("Execution context: ").append(context).append("\n");
        builder.append("Server version: ").append(serverVersion).append(isLatest ? " (latest)" : "").append("\n");
        builder.append("Tech Preview: ").append(techPreview).append("\n");
        Set<FeaturePackLocation.ProducerSpec> topLevel = new LinkedHashSet<>();
        for(FeaturePackConfig fp : fps.getFeaturePackDeps()) {
            topLevel.add(fp.getLocation().getProducer());
        }
        for(FeaturePackConfig fp : fps.getFeaturePackDeps()) {
            builder.append("\nFeature-pack: ").append("@|bold ").append(fp.getLocation().getFPID()).append("|@\n");
            builder.append("Contained layers: ");
            Set<String> layers = new TreeSet<>();
            Set<FeaturePackLocation.ProducerSpec> deps = fpDependencies.get(fp.getLocation().getFPID());
            for(Layer l : allLayers.values()) {
                if(l.getFeaturePacks().contains(fp.getLocation().getFPID())) {
                    layers.add(l.getName());
                }
                if(deps != null) {
                    for (FeaturePackLocation.ProducerSpec dep : deps) {
                        if (!topLevel.contains(dep)) {
                            for (FeaturePackLocation.FPID fpid : l.getFeaturePacks()) {
                                if (fpid.getProducer().equals(dep)) {
                                    layers.add(l.getName());
                                }
                            }
                        }
                    }
                }
            }
            topLevel.addAll(deps);
            builder.append(layers).append("\n");
        }
        return builder.toString();
    }

    public boolean isGoOffline() {
        return goOffline;
    }

    public boolean isHelp() {
        return help;
    }

    public boolean isVersion() {
        return version;
    }

    public boolean isDisplayConfigurationInfo() {
        return displayConfigurationInfo;
    }
}
