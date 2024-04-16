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
package org.wildfly.glow.cli.commands;

import org.wildfly.glow.deployment.openshift.api.OpenShiftSupport;
import org.wildfly.glow.deployment.openshift.api.OpenShiftConfiguration;
import java.nio.file.Files;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.HiddenPropertiesAccessor;
import org.wildfly.glow.OutputContent;
import org.wildfly.glow.OutputFormat;
import org.wildfly.glow.ScanArguments.Builder;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.error.IdentifiedError;
import org.wildfly.glow.maven.MavenResolver;
import picocli.CommandLine;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jboss.galleon.universe.FeaturePackLocation.ProducerSpec;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.ChannelSession;

import static org.wildfly.glow.Arguments.CLOUD_EXECUTION_CONTEXT;
import static org.wildfly.glow.Arguments.COMPACT_PROPERTY;
import org.wildfly.glow.ConfigurationResolver;
import org.wildfly.glow.Env;
import org.wildfly.glow.Layer;
import static org.wildfly.glow.OutputFormat.BOOTABLE_JAR;
import static org.wildfly.glow.OutputFormat.DOCKER_IMAGE;
import static org.wildfly.glow.OutputFormat.OPENSHIFT;
import org.wildfly.glow.StabilitySupport;
import org.wildfly.glow.maven.ChannelMavenArtifactRepositoryManager;

@CommandLine.Command(
        name = Constants.SCAN_COMMAND,
        sortOptions = true
)
public class ScanCommand extends AbstractCommand {

    private static class ProvisionConverter implements CommandLine.ITypeConverter<OutputFormat> {

        @Override
        public OutputFormat convert(String value) throws Exception {
            return OutputFormat.valueOf(value.toUpperCase());
        }
    }

    private static final String ADD_ADD_ONS_MSG = "@|bold To enable add-ons, add the|@ @|fg(yellow) "
            + Constants.ADD_ONS_OPTION + "=<list of add-ons>|@ @|bold option to the|@ @|fg(yellow) "
            + Constants.SCAN_COMMAND + "|@ @|bold command|@";

    @CommandLine.Option(names = {Constants.CLOUD_OPTION_SHORT, Constants.CLOUD_OPTION})
    Optional<Boolean> cloud;

    @CommandLine.Option(names = {Constants.WILDFLY_PREVIEW_OPTION_SHORT, Constants.WILDFLY_PREVIEW_OPTION})
    Optional<Boolean> wildflyPreview;

    @CommandLine.Option(names = {Constants.SUGGEST_OPTION_SHORT, Constants.SUGGEST_OPTION})
    Optional<Boolean> suggest;

    @CommandLine.Option(names = Constants.HA_OPTION)
    Optional<Boolean> haProfile;

    @CommandLine.Option(names = {Constants.SERVER_VERSION_OPTION_SHORT, Constants.SERVER_VERSION_OPTION}, paramLabel = Constants.SERVER_VERSION_OPTION_LABEL)
    Optional<String> wildflyServerVersion;

    @CommandLine.Option(names = {Constants.DOCKER_IMAGE_NAME_OPTION_SHORT, Constants.DOCKER_IMAGE_NAME_OPTION}, paramLabel = Constants.DOCKER_IMAGE_NAME_OPTION_LABEL)
    Optional<String> dockerImageName;

    @CommandLine.Option(names = Constants.ADD_LAYERS_FOR_JNDI_OPTION, split = ",", paramLabel = Constants.ADD_LAYERS_FOR_JNDI_OPTION_LABEL)
    Set<String> layersForJndi = new LinkedHashSet<>();

    @CommandLine.Option(names = {Constants.ADD_ONS_OPTION_SHORT, Constants.ADD_ONS_OPTION}, split = ",", paramLabel = Constants.ADD_ONS_OPTION_LABEL)
    Set<String> addOns = new LinkedHashSet<>();

    @Parameters(descriptionKey = "deployments")
    List<Path> deployments;

    @CommandLine.Option(names = Constants.INPUT_FEATURE_PACKS_FILE_OPTION, paramLabel = Constants.INPUT_FEATURE_PACKS_FILE_OPTION_LABEL)
    Optional<Path> provisioningXml;

    @CommandLine.Option(converter = ProvisionConverter.class, names = {Constants.PROVISION_OPTION_SHORT, Constants.PROVISION_OPTION}, paramLabel = Constants.PROVISION_OPTION_LABEL)
    Optional<OutputFormat> provision;

    @CommandLine.Option(names = {Constants.PROVISION_OUTPUT_DIR_OPTION_SHORT, Constants.PROVISION_OUTPUT_DIR_OPTION}, paramLabel = Constants.PROVISION_OUTPUT_DIR_LABEL)
    Optional<String> provisionOutputDir;

    @CommandLine.Option(names = {Constants.EXCLUDE_ARCHIVES_FROM_SCAN_OPTION_SHORT, Constants.EXCLUDE_ARCHIVES_FROM_SCAN_OPTION},
            split = ",", paramLabel = Constants.EXCLUDE_ARCHIVES_FROM_SCAN_OPTION_LABEL)
    Set<String> excludeArchivesFromScan = new HashSet<>();

    @CommandLine.Option(names = {Constants.STABILITY_OPTION, Constants.STABILITY_OPTION_SHORT}, paramLabel = Constants.STABILITY_LABEL)
    Optional<String> stability;

    @CommandLine.Option(names = {Constants.PACKAGE_STABILITY_OPTION, Constants.PACKAGE_STABILITY_OPTION_SHORT}, paramLabel = Constants.STABILITY_LABEL)
    Optional<String> packageStability;

    @CommandLine.Option(names = {Constants.CONFIG_STABILITY_OPTION, Constants.CONFIG_STABILITY_OPTION_SHORT}, paramLabel = Constants.STABILITY_LABEL)
    Optional<String> configStability;

    @CommandLine.Option(names = {Constants.ENV_FILE_OPTION_SHORT, Constants.ENV_FILE_OPTION}, paramLabel = Constants.ENV_FILE_OPTION_LABEL)
    Optional<Path> envFile;

    @CommandLine.Option(names = {Constants.BUILD_ENV_FILE_OPTION_SHORT, Constants.BUILD_ENV_FILE_OPTION}, paramLabel = Constants.BUILD_ENV_FILE_OPTION_LABEL)
    Optional<Path> buildEnvFile;

    @CommandLine.Option(names = {Constants.INIT_SCRIPT_OPTION_SHORT, Constants.INIT_SCRIPT_OPTION}, paramLabel = Constants.INIT_SCRIPT_OPTION_LABEL)
    Optional<Path> initScriptFile;

    @CommandLine.Option(names = {Constants.CLI_SCRIPT_OPTION_SHORT, Constants.CLI_SCRIPT_OPTION}, paramLabel = Constants.CLI_SCRIPT_OPTION_LABEL)
    Optional<Path> cliScriptFile;

    @CommandLine.Option(names = Constants.DISABLE_DEPLOYERS, split = ",", paramLabel = Constants.ADD_ONS_OPTION_LABEL)
    Set<String> disableDeployers = new LinkedHashSet<>();

    @CommandLine.Option(names = {Constants.SYSTEM_PROPERTIES_OPTION_SHORT, Constants.SYSTEM_PROPERTIES_OPTION},
            split = " ", paramLabel = Constants.SYSTEM_PROPERTIES_LABEL)
    Set<String> systemProperties = new HashSet<>();

    @CommandLine.Option(names = {Constants.FAILS_ON_ERROR_OPTION_SHORT, Constants.FAILS_ON_ERROR_OPTION}, defaultValue = "true")
    Optional<Boolean> failsOnError;

    @CommandLine.Option(names = {Constants.CHANNELS_OPTION_SHORT, Constants.CHANNELS_OPTION}, paramLabel = Constants.CHANNELS_OPTION_LABEL)
    Optional<Path> channelsFile;

    @Override
    public Integer call() throws Exception {
        if (!systemProperties.isEmpty()) {
            for (String p : systemProperties) {
                if (p.startsWith("-D")) {
                    int i = p.indexOf("=");
                    String propName;
                    String value = "";
                    if (i > 0) {
                        propName = p.substring(2, i);
                        value = p.substring(i + 1);
                    } else {
                        propName = p.substring(2);
                    }
                    System.setProperty(propName, value);
                } else {
                    throw new Exception("Invalid system property " + p + ". A property must start with -D");
                }
            }
        }
        HiddenPropertiesAccessor hiddenPropertiesAccessor = new HiddenPropertiesAccessor();
        boolean compact = Boolean.parseBoolean(hiddenPropertiesAccessor.getProperty(COMPACT_PROPERTY));
        if (!compact) {
            print("Wildfly Glow is scanning...");
        }
        Builder builder = Arguments.scanBuilder();
        if (haProfile.orElse(false)) {
            Set<String> profiles = new HashSet<>();
            profiles.add(Constants.HA);
            builder.setExecutionProfiles(profiles);
        }
        if (!layersForJndi.isEmpty()) {
            builder.setJndiLayers(layersForJndi);
        }
        if (suggest.orElse(false)) {
            builder.setSuggest(true);
        }
        if (wildflyPreview.orElse(false)) {
            if (channelsFile.isPresent()) {
                throw new Exception(Constants.WILDFLY_PREVIEW_OPTION + "can't be set when " + Constants.CHANNELS_OPTION + " is set.");
            }
            builder.setTechPreview(true);
        }
        if (wildflyServerVersion.isPresent()) {
            if (channelsFile.isPresent()) {
                throw new Exception(Constants.SERVER_VERSION_OPTION + "can't be set when " + Constants.CHANNELS_OPTION + " is set.");
            }
            builder.setVersion(wildflyServerVersion.get());
        }
        Map<String, String> extraEnv = new HashMap<>();
        Map<String, String> buildExtraEnv = new HashMap<>();
        if (envFile.isPresent()) {
            if (provision.isPresent()) {
                if (!OPENSHIFT.equals(provision.get())) {
                    throw new Exception("Env file is only usable when --provision=" + OPENSHIFT + " option is set.");
                }
            } else {
                throw new Exception("Env file is only usable when --provision=" + OPENSHIFT + " option is set.");
            }
            extraEnv.putAll(handleOpenShiftEnvFile(envFile.get()));
        }
        if (buildEnvFile.isPresent()) {
            if (provision.isPresent()) {
                if (!OPENSHIFT.equals(provision.get())) {
                    throw new Exception("Build env file is only usable when --provision=" + OPENSHIFT + " option is set.");
                }
            } else {
                throw new Exception("Build env file is only usable when --provision=" + OPENSHIFT + " option is set.");
            }
            buildExtraEnv.putAll(handleOpenShiftEnvFile(buildEnvFile.get()));
        }
        if (cliScriptFile.isPresent()) {
            if (provision.isPresent()) {
                if (!OPENSHIFT.equals(provision.get())) {
                    throw new Exception("CLI script file is only usable when --provision=" + OPENSHIFT + " option is set.");
                }
            } else {
                throw new Exception("CLI script file file is only usable when --provision=" + OPENSHIFT + " option is set.");
            }
            Path p = cliScriptFile.get();
            if (!Files.exists(p)) {
                throw new Exception(p + " file doesn't exist");
            }
        }
        if (initScriptFile.isPresent()) {
            if (provision.isPresent()) {
                if (!OPENSHIFT.equals(provision.get())) {
                    throw new Exception("Init script file is only usable when --provision=" + OPENSHIFT + " option is set.");
                }
            } else {
                throw new Exception("Init script file file is only usable when --provision=" + OPENSHIFT + " option is set.");
            }
            Path p = initScriptFile.get();
            if (!Files.exists(p)) {
                throw new Exception(p + " file doesn't exist");
            }
        }
        builder.setVerbose(verbose);
        if (!addOns.isEmpty()) {
            builder.setUserEnabledAddOns(addOns);
        }
        if (deployments != null && !deployments.isEmpty()) {
            builder.setBinaries(deployments);
        }
        if (provisioningXml.isPresent()) {
            builder.setProvisoningXML(provisioningXml.get());
        }
        MavenRepoManager repoManager = null;
        if (channelsFile.isPresent()) {
            Path channelsFilePath = this.channelsFile.get();
            if (!Files.exists(channelsFilePath)) {
                throw new Exception(channelsFilePath + " file doesn't exist");
            }
            ChannelSession session = MavenResolver.buildChannelSession(channelsFilePath);
            builder.setChannelSession(session);
            repoManager = new ChannelMavenArtifactRepositoryManager(session);
        }
        if (provision.isPresent()) {
            if (BOOTABLE_JAR.equals(provision.get()) && cloud.orElse(false)) {
                throw new Exception("Can't produce a Bootable JAR for cloud. Use the " + Constants.PROVISION_OPTION + "=SERVER option for cloud.");
            }
            if (DOCKER_IMAGE.equals(provision.get()) && !cloud.orElse(false)) {
                cloud = Optional.of(Boolean.TRUE);
            }
            if (OPENSHIFT.equals(provision.get()) && !cloud.orElse(false)) {
               cloud = Optional.of(Boolean.TRUE);
            }
            builder.setOutput(provision.get());
        }
        if (cloud.orElse(false)) {
            builder.setExecutionContext(CLOUD_EXECUTION_CONTEXT);
        }
        builder.setExcludeArchivesFromScan(excludeArchivesFromScan);

        // Enforce community stability level. Doing so, any discovered features at a lower level are advertised
        String userSetConfigStability = null;
        builder.setConfigStability(org.jboss.galleon.Constants.STABILITY_COMMUNITY);
        if (stability.isPresent()) {
            if (configStability.isPresent()) {
                throw new Exception(Constants.CONFIG_STABILITY_OPTION + " can't be set when " + Constants.STABILITY_OPTION + " is set");
            }
            if (packageStability.isPresent()) {
                throw new Exception(Constants.PACKAGE_STABILITY_OPTION + " can't be set when " + Constants.STABILITY_OPTION + " is set");
            }
            StabilitySupport.checkStability(stability.get());
            userSetConfigStability = stability.get();
            builder.setConfigStability(stability.get());
            builder.setPackageStability(stability.get());
        }
        if (configStability.isPresent()) {
            StabilitySupport.checkStability(configStability.get());
            userSetConfigStability = configStability.get();
            builder.setConfigStability(configStability.get());
        }
        if (packageStability.isPresent()) {
            StabilitySupport.checkStability(packageStability.get());
            builder.setPackageStability(packageStability.get());
        }
        if (dockerImageName.isPresent()) {
            if (provision.isPresent() && !DOCKER_IMAGE.equals(provision.get())) {
                throw new Exception("Can only set a docker image name when provisioning a docker image. Remove the " + Constants.DOCKER_IMAGE_NAME_OPTION + " option");
            }
        }
        builder.setIsCli(true);
        MavenRepoManager directMavenResolver = MavenResolver.newMavenResolver();
        ScanResults scanResults = GlowSession.scan(repoManager == null ? directMavenResolver : repoManager, builder.build(), GlowMessageWriter.DEFAULT);
        ConfigurationResolver configurationResolver = new ConfigurationResolver() {
            @Override
            public ResolvedEnvs getResolvedEnvs(Layer layer, Set<Env> input) throws Exception {
                if(provision.isPresent() && provision.get().equals(OPENSHIFT)) {
                    return OpenShiftSupport.getResolvedEnvs(layer, input, disableDeployers);
                }
                return null;
            }
        };
        scanResults.outputInformation(configurationResolver);
        if (provision.isEmpty()) {
            if (!compact) {
                if (suggest.orElse(false)) {
                    if (!scanResults.getSuggestions().getPossibleAddOns().isEmpty() && addOns.isEmpty()) {
                        print(ADD_ADD_ONS_MSG);
                    }
                    if (!scanResults.getSuggestions().getPossibleProfiles().isEmpty()) {
                        print("@|bold To enable the HA profile, add the|@ @|fg(yellow) %s|@ @|bold option to the|@ @|fg(yellow) %s|@ @|bold command|@", Constants.HA_OPTION, Constants.SCAN_COMMAND);
                    }
                }
                if (scanResults.getErrorSession().hasErrors()) {
                    if (!suggest.orElse(false)) {
                        boolean hasAddOn = false;
                        // Do we have errors and add-ons to set?
                        for (IdentifiedError err : scanResults.getErrorSession().getErrors()) {
                            if (!err.getPossibleAddons().isEmpty()) {
                                hasAddOn = true;
                                break;
                            }
                        }
                        if (hasAddOn) {
                            System.out.println(CommandLine.Help.Ansi.AUTO.string(ADD_ADD_ONS_MSG));
                        }
                    }
                    print("@|bold Some errors have been reported. You should fix them prior provisioning a server with the|@ @|fg(yellow) " + Constants.PROVISION_OPTION + "|@ @|bold option of the|@ @|fg(yellow) " + Constants.SCAN_COMMAND + "|@ @|bold command|@");
                } else {
                    print("@|bold If you had included a|@ @|fg(yellow) " + Constants.PROVISION_OPTION+"="+Constants.PROVISION_OPTION_LABEL + "|@ @|bold option to the|@ @|fg(yellow) " + Constants.SCAN_COMMAND + "|@ @|bold command, after outputting this report, WildFly Glow will continue on to provisioning your WildFly server, bootable jar, a Docker image or deploy your application to OpenShift.|@");
                }
            }
        } else {
            print();
            if (failsOnError.orElse(false) && scanResults.getErrorSession().hasErrors()) {
                throw new Exception("Your are provisioning although errors have been reported. If you want to enforce provisioning, set --fails-on-error=false to ignore errors.");
            }
            ProducerSpec baseProducer = scanResults.getProvisioningConfig().getFeaturePackDeps().iterator().next().getLocation().getProducer();
            String vers = scanResults.getFeaturePackVersions().get(baseProducer).getBuild();
            Path target = Paths.get(provisionOutputDir.orElse("server-" + vers));
            IoUtils.recursiveDelete(target);
            switch (provision.get()) {
                case BOOTABLE_JAR: {
                    if (provisionOutputDir.isEmpty()) {
                        target = Paths.get("");
                    }
                    print("@|bold Building WildFly Bootable JAR file...|@");
                    break;
                }
                case PROVISIONING_XML: {
                    print("@|bold Generating Galleon provisioning configuration file...|@");
                    break;
                }
                case SERVER: {
                    print("@|bold Provisioning server...|@", target);
                    break;
                }
                case DOCKER_IMAGE: {
                    print("@|bold Generating docker image...|@");
                    break;
                }
                case OPENSHIFT: {
                    print("@|bold Openshift build and deploy...|@");
                    break;
                }
            }
            OutputContent content = scanResults.outputConfig(target, dockerImageName.orElse(null));
            Path base = Paths.get("").toAbsolutePath();
            String envMessage = null;
            String completedMessage = null;
            for (OutputContent.OutputFile f : content.getFiles().keySet()) {
                Path rel = base.relativize(content.getFiles().get(f));
                switch (f) {
                    case BOOTABLE_JAR_FILE: {
                        completedMessage = "@|bold To run the jar call: 'java -jar " + rel + "'|@";
                        break;
                    }
                    case DOCKER_FILE: {
                        print("@|bold Docker file generated in %s|@.", rel);
                        break;
                    }
                    case ENV_FILE: {
                        // Exposing this file seems to create confusion
//                        if (!OutputFormat.OPENSHIFT.equals(provision.get())) {
//                            print("@|bold The file " + rel + " contains the list of environment variables that you must set prior to start the server.|@");
//                        }
                        switch (provision.get()) {
                            case SERVER: {
                                envMessage = "WARNING: You have to export the suggested env variables prior to start the server.";
                                break;
                            }
                            case BOOTABLE_JAR: {
                                envMessage="WARNING: You have to export the suggested env variables prior to start the bootable JAR.";
                                break;
                            }
                            case DOCKER_IMAGE: {
                                envMessage = "WARNING: For each suggested env variable add `-e <env name>=<env value>` to the `[docker | podman] run` command.";
                                break;
                            }
                        }
                        break;
                    }
                    case PROVISIONING_XML_FILE: {
                        switch (provision.get()) {
                            case PROVISIONING_XML: {
                                print("@|bold Galleon Provisioning configuration is located in " + rel + " file|@");
                            }
                        }
                        break;

                    }
                    case SERVER_DIR: {
                        if (cloud.orElse(false)) {
                            completedMessage = "@|bold To run the server call: 'JBOSS_HOME=" + rel + " sh " + rel + "/bin/openshift-launch.sh'|@";
                        } else {
                            completedMessage = "@|bold To run the server call: 'sh " + rel + "/bin/standalone.sh" + (userSetConfigStability == null ? "" : " --stability=" + userSetConfigStability) + "'|@";
                        }
                        break;
                    }
                }
            }
            if (OutputFormat.OPENSHIFT.equals(provision.get())) {
                String name = "";
                Path deploymentsDir = target.resolve("deployments");
                Files.createDirectories(deploymentsDir);
                for (Path p : deployments) {
                    Files.copy(p, deploymentsDir.resolve(p.getFileName()));
                    int ext = p.getFileName().toString().indexOf(".");
                    name += p.getFileName().toString().substring(0, ext);
                }
                Map<String, String> envMap = new HashMap<>();
                for (Set<Env> envs : scanResults.getSuggestions().getStronglySuggestedConfigurations().values()) {
                    for (Env env : envs) {
                        envMap.put(env.getName(), env.getDescription());
                    }
                }
                OpenShiftSupport.deploy(GlowMessageWriter.DEFAULT,
                        target, name.isEmpty() ? "app-from-glow" : name.toLowerCase(),
                        envMap,
                        scanResults.getDiscoveredLayers(),
                        scanResults.getMetadataOnlyLayers(),
                        haProfile.orElse(false),
                        extraEnv,
                        buildExtraEnv,
                        scanResults.getSuggestions().getBuildTimeRequiredConfigurations(),
                        disableDeployers,
                        initScriptFile.orElse(null),
                        cliScriptFile.orElse(null),
                        new OpenShiftConfiguration.Builder().build(),
                        directMavenResolver,
                        userSetConfigStability,
                        Collections.emptyMap());
                print("@|bold \nOpenshift build and deploy DONE.|@");
            } else {
                if (content.getDockerImageName() != null) {
                    print("@|bold To run the image call: '[docker | podman] run -p 8080:8080 -p 9990:9990 " + content.getDockerImageName() + "'|@");
                    if (envMessage != null) {
                        GlowMessageWriter.DEFAULT.warn(envMessage);
                    }
                } else {
                    if (completedMessage != null) {
                        print(completedMessage);
                        if (envMessage != null) {
                            GlowMessageWriter.DEFAULT.warn(envMessage);
                        }
                    }
                }
            }
        }
        return 0;
    }

    private Map<String, String> handleOpenShiftEnvFile(Path envFile) throws Exception {
        Map<String, String> extraEnv = new HashMap<>();
        if (!Files.exists(envFile)) {
            throw new Exception(envFile + " file doesn't exist");
        }
        for (String l : Files.readAllLines(envFile)) {
            l = l.trim();
            if (!l.isEmpty() && !l.startsWith("#")) {
                int i = l.indexOf("=");
                if (i < 0 || i == l.length() - 1) {
                    throw new Exception("Invalid environment variable " + l + " in " + envFile);
                }
                extraEnv.put(l.substring(0, i), l.substring(i + 1));
            }
        }
        return extraEnv;
    }
}
