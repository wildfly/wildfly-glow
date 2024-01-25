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

import org.jboss.galleon.util.IoUtils;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.FeaturePacks;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.wildfly.glow.Arguments.CLOUD_EXECUTION_CONTEXT;
import static org.wildfly.glow.Arguments.COMPACT_PROPERTY;
import static org.wildfly.glow.OutputFormat.BOOTABLE_JAR;
import static org.wildfly.glow.OutputFormat.DOCKER_IMAGE;

@CommandLine.Command(
        name = Constants.SCAN_COMMAND,
        sortOptions = true
)
public class ScanCommand extends AbstractCommand {

    private static final String ADD_ADD_ONS_MSG="@|bold To enable add-ons, add the|@ @|fg(yellow) " +
            Constants.ADD_ONS_OPTION + "=<list of add-ons>|@ @|bold option to the|@ @|fg(yellow) " +
            Constants.SCAN_COMMAND + "|@ @|bold command|@";

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

    @CommandLine.Option(names = {Constants.PROVISION_OPTION_SHORT, Constants.PROVISION_OPTION}, paramLabel = Constants.PROVISION_OPTION_LABEL)
    Optional<OutputFormat> provision;

    @CommandLine.Option(names = {Constants.PROVISION_OUTPUT_DIR_OPTION_SHORT, Constants.PROVISION_OUTPUT_DIR_OPTION}, paramLabel = Constants.PROVISION_OUTPUT_DIR_LABEL)
    Optional<String> provisionOutputDir;

    @CommandLine.Option(names = {Constants.EXCLUDE_ARCHIVES_FROM_SCAN_OPTION_SHORT, Constants.EXCLUDE_ARCHIVES_FROM_SCAN_OPTION},
            split = ",", paramLabel = Constants.EXCLUDE_ARCHIVES_FROM_SCAN_OPTION_LABEL)
    Set<String> excludeArchivesFromScan = new HashSet<>();

    @Override
    public Integer call() throws Exception {
        HiddenPropertiesAccessor hiddenPropertiesAccessor = new HiddenPropertiesAccessor();
        boolean compact = Boolean.parseBoolean(hiddenPropertiesAccessor.getProperty(COMPACT_PROPERTY));
        if (!compact) {
            print("Wildfly Glow is scanning...");
        }
        Builder builder = Arguments.scanBuilder();
        if (cloud.orElse(false)) {
            builder.setExecutionContext(CLOUD_EXECUTION_CONTEXT);
        }
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
            builder.setTechPreview(true);
        }
        if (wildflyServerVersion.isPresent()) {
            builder.setVersion(wildflyServerVersion.get());
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
        if (provision.isPresent()) {
            if (BOOTABLE_JAR.equals(provision.get()) && cloud.orElse(false)) {
                throw new Exception("Can't produce a Bootable JAR for cloud. Use the " + Constants.PROVISION_OPTION + "=SERVER option for cloud.");
            }
            if (DOCKER_IMAGE.equals(provision.get()) && !cloud.orElse(false)) {
                throw new Exception("Can't produce a Docker image if cloud is not enabled. Use the " + Constants.CLOUD_OPTION + " option.");
            }
            builder.setOutput(provision.get());
        }
        builder.setExcludeArchivesFromScan(excludeArchivesFromScan);

        if (dockerImageName.isPresent()) {
            if (provision.isPresent() && !DOCKER_IMAGE.equals(provision.get())) {
                throw new Exception("Can only set a docker image name when provisioning a docker image. Remove the " + Constants.DOCKER_IMAGE_NAME_OPTION + " option");
            }
        }
        ScanResults scanResults = GlowSession.scan(MavenResolver.newMavenResolver(), builder.build(), GlowMessageWriter.DEFAULT);
        scanResults.outputInformation();
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
                       for(IdentifiedError err : scanResults.getErrorSession().getErrors()) {
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
                    print("@|bold If you had included a|@ @|fg(yellow) " + Constants.PROVISION_OPTION + "|@ @|bold option to the|@ @|fg(yellow) " + Constants.SCAN_COMMAND + "|@ @|bold command, after outputting this report, WildFly Glow will continue on to provisioning your WildFly server, bootable jar or Docker image.|@");
                }
            }
        } else {
            print();
            String vers = wildflyServerVersion.orElse(null) == null ? FeaturePacks.getLatestVersion() : wildflyServerVersion.get();
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
            }
            OutputContent content = scanResults.outputConfig(target, dockerImageName.orElse(null));
            Path base = Paths.get("").toAbsolutePath();
            for (OutputContent.OutputFile f : content.getFiles().keySet()) {
                Path rel = base.relativize(content.getFiles().get(f));
                switch (f) {
                    case BOOTABLE_JAR_FILE: {
                        print("@|bold Bootable JAR build DONE.|@");
                        print("@|bold To run the jar call: 'java -jar " + rel + "'|@");
                        break;
                    }
                    case DOCKER_FILE: {
                        print("@|bold Image generation DONE.|@.");
                        print("@|bold Docker file generated in %s|@.", rel);
                        break;
                    }
                    case ENV_FILE: {
                        print("@|bold The file " + rel + " contains the list of environment variables that you must set prior to start the server.|@");
                        switch (provision.get()) {
                            case SERVER: {
                                print("@|bold Export the suggested env variables for the server to take them into account.|@");
                                break;
                            }
                            case BOOTABLE_JAR: {
                                print("@|bold Export the suggested env variables for the bootable JAR to take them into account.|@");
                                break;
                            }
                            case DOCKER_IMAGE: {
                                print("@|bold For each env variable add `-e <env name>=<env value>` to the `docker run` command.|@");
                                break;
                            }
                        }
                        break;
                    }
                    case PROVISIONING_XML_FILE: {
                        print("@|bold Generation DONE.|@");
                        print("@|bold Galleon Provisioning configuration is located in " + rel + " file|@");
                        break;
                    }
                    case SERVER_DIR: {
                        print("@|bold Provisioning DONE.|@");
                        if (cloud.orElse(false)) {
                            print("@|bold To run the server call: 'JBOSS_HOME=" + rel + " sh " + rel + "/bin/openshift-launch.sh'|@");
                        } else {
                            print("@|bold To run the server call: 'sh " + rel + "/bin/standalone.sh'|@");
                        }
                        break;
                    }
                }
            }
            if (content.getDockerImageName() != null) {
                print("@|bold To run the image call: 'docker run " + content.getDockerImageName() + "'|@");
            }
        }
        return 0;
    }
}
