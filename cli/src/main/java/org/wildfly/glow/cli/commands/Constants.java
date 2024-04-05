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

/**
 *
 * @author jdenise
 */
public interface Constants {

    String ADD_ONS_OPTION = "--add-ons";
    String ADD_ONS_OPTION_LABEL = "<add-on>";
    String ADD_ONS_OPTION_SHORT = "-ao";
    String ADD_LAYERS_FOR_JNDI_OPTION = "--add-layers-for-jndi";
    String ADD_LAYERS_FOR_JNDI_OPTION_LABEL = "<layer>";
    String BATCH_OPTION = "--batch";
    String BATCH_OPTION_SHORT = "-B";
    String BUILD_ENV_FILE_OPTION = "--build-env-file";
    String BUILD_ENV_FILE_OPTION_SHORT = "-bef";
    String BUILD_ENV_FILE_OPTION_LABEL = "<build env file path>";
    String CHANNELS_OPTION = "--channels";
    String CHANNELS_OPTION_SHORT = "-cc";
    String CHANNELS_OPTION_LABEL = "<Channels yaml file path>";
    String CLI_SCRIPT_OPTION = "--cli-script";
    String CLI_SCRIPT_OPTION_SHORT = "-cs";
    String CLI_SCRIPT_OPTION_LABEL = "<CLI script file path>";
    String CLOUD_OPTION = "--cloud";
    String CLOUD_OPTION_SHORT = "-c";
    String COMPLETION_COMMAND = "completion";
    String CONFIG_STABILITY_OPTION = "--config-stability-level";
    String CONFIG_STABILITY_OPTION_SHORT = "-csl";
    String DISABLE_DEPLOYERS = "--disable-deployers";
    String DISABLE_DEPLOYERS_LABEL = "<ALL|deployer name>";
    String DOCKER_IMAGE_NAME_OPTION = "--docker-image-name";
    String DOCKER_IMAGE_NAME_OPTION_LABEL = "<docker image name>";
    String DOCKER_IMAGE_NAME_OPTION_SHORT = "-di";
    String ENV_FILE_OPTION = "--env-file";
    String ENV_FILE_OPTION_SHORT = "-ef";
    String ENV_FILE_OPTION_LABEL = "<env file path>";
    String EXCLUDE_ARCHIVES_FROM_SCAN_OPTION = "--exclude-archives-from-scan";
    String EXCLUDE_ARCHIVES_FROM_SCAN_OPTION_LABEL = "<list of nested archive names>";
    String EXCLUDE_ARCHIVES_FROM_SCAN_OPTION_SHORT = "-ea";
    String FAILS_ON_ERROR_OPTION = "--fails-on-error";
    String FAILS_ON_ERROR_OPTION_SHORT = "-foe";
    String HA_OPTION = "--ha";
    String GO_OFFLINE_COMMAND = "go-offline";
    String HA = "ha";
    String HELP_OPTION = "--help";
    String HELP_OPTION_SHORT = "-h";
    String INIT_SCRIPT_OPTION = "--init-script";
    String INIT_SCRIPT_OPTION_SHORT = "-is";
    String INIT_SCRIPT_OPTION_LABEL = "<init script file path>";
    String INPUT_FEATURE_PACKS_FILE_OPTION = "--input-feature-packs-file";
    String INPUT_FEATURE_PACKS_FILE_OPTION_LABEL = "<provisioning file path>";
    String NO_DOCKER_IMAGE_OPTION = "--no-docker-image";
    String NO_DOCKER_IMAGE_OPTION_SHORT = "-nd";
    String PACKAGE_STABILITY_OPTION = "--package-stability-level";
    String PACKAGE_STABILITY_OPTION_SHORT = "-psl";
    String PROVISION_OPTION = "--provision";
    String PROVISION_OPTION_LABEL = "<SERVER|BOOTABLE_JAR|OPENSHIFT|DOCKER_IMAGE|PROVISIONING_XML>";
    String PROVISION_OPTION_SHORT = "-p";
    String PROVISION_OUTPUT_DIR_OPTION = "--output-dir";
    String PROVISION_OUTPUT_DIR_OPTION_SHORT = "-d";
    String PROVISION_OUTPUT_DIR_LABEL = "<output directory>";
    String SCAN_COMMAND = "scan";
    String SERVER_VERSION_OPTION = "--server-version";
    String SERVER_VERSION_OPTION_SHORT = "-sv";
    String SERVER_VERSION_OPTION_LABEL = "<server version>";
    String SHOW_ADD_ONS_COMMAND = "show-add-ons";
    String SHOW_CONFIGURATION_COMMAND = "show-configuration";
    String SHOW_SERVER_VERSIONS_COMMAND = "show-server-versions";
    String STABILITY_LABEL = "<default|community|preview|experimental>";
    String STABILITY_OPTION = "--stability-level";
    String STABILITY_OPTION_SHORT = "-sl";
    String SYSTEM_PROPERTIES_LABEL = "\"<-DpropName[=value] [-DpropName[=value]]>\"";
    String SYSTEM_PROPERTIES_OPTION = "--properties";
    String SYSTEM_PROPERTIES_OPTION_SHORT = "-pp";
    String SUGGEST_OPTION = "--suggest";
    String SUGGEST_OPTION_SHORT = "-s";
    String VERBOSE_OPTION = "--verbose";
    String VERBOSE_OPTION_SHORT = "-vv";
    String VERSION_OPTION = "--version";
    String VERSION_OPTION_SHORT = "-v";
    String WILDFLY_GLOW = "wildfly-glow";
    String WILDFLY_PREVIEW_OPTION = "--wildfly-preview";
    String WILDFLY_PREVIEW_OPTION_SHORT = "-wp";
}
