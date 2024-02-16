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

    String HA = "ha";

    String COMPLETION_COMMAND = "completion";
    String GO_OFFLINE_COMMAND = "go-offline";
    String SCAN_COMMAND = "scan";
    String SHOW_ADD_ONS_COMMAND = "show-add-ons";
    String SHOW_CONFIGURATION_COMMAND = "show-configuration";
    String SHOW_SERVER_VERSIONS_COMMAND = "show-server-versions";
    String ADD_LAYERS_FOR_JNDI_OPTION = "--add-layers-for-jndi";
    String ADD_LAYERS_FOR_JNDI_OPTION_LABEL = "<layer>";
    String ADD_ONS_OPTION = "--add-ons";
    String ADD_ONS_OPTION_LABEL = "<add-on>";
    String ADD_ONS_OPTION_SHORT = "-ao";

    String BATCH_OPTION = "--batch";
    String BATCH_OPTION_SHORT = "-B";

    String CLOUD_OPTION = "--cloud";
    String CLOUD_OPTION_SHORT = "-c";
    String DOCKER_IMAGE_NAME_OPTION = "--docker-image-name";
    String DOCKER_IMAGE_NAME_OPTION_LABEL = "<docker image name>";
    String DOCKER_IMAGE_NAME_OPTION_SHORT = "-di";

    String EXCLUDE_ARCHIVES_FROM_SCAN_OPTION = "--exclude-archives-from-scan";
    String EXCLUDE_ARCHIVES_FROM_SCAN_OPTION_LABEL = "<list of nested archive names>";
    String EXCLUDE_ARCHIVES_FROM_SCAN_OPTION_SHORT = "-ea";
    String HA_OPTION = "--ha";

    String HELP_OPTION = "--help";
    String HELP_OPTION_SHORT = "-h";
    String INPUT_FEATURE_PACKS_FILE_OPTION = "--input-feature-packs-file";
    String INPUT_FEATURE_PACKS_FILE_OPTION_LABEL = "<provisioning file path>";
    String NO_DOCKER_IMAGE_OPTION = "--no-docker-image";
    String NO_DOCKER_IMAGE_OPTION_SHORT = "-nd";
    String PROVISION_OPTION = "--provision";
    String PROVISION_OPTION_LABEL = "<SERVER|BOOTABLE_JAR|DOCKER_IMAGE|PROVISIONING_XML>";
    String PROVISION_OPTION_SHORT = "-p";
    String SERVER_VERSION_OPTION = "--server-version";
    String SERVER_VERSION_OPTION_SHORT = "-sv";
    String SERVER_VERSION_OPTION_LABEL = "<server version>";
    String SUGGEST_OPTION = "--suggest";
    String SUGGEST_OPTION_SHORT = "-s";

    String VERBOSE_OPTION = "--verbose";
    String VERBOSE_OPTION_SHORT = "-vv";

    String VERSION_OPTION = "--version";
    String VERSION_OPTION_SHORT = "-v";

    String WILDFLY_PREVIEW_OPTION = "--wildfly-preview";
    String WILDFLY_PREVIEW_OPTION_SHORT = "-wp";

    String WILDFLY_GLOW = "wildfly-glow";

    String PROVISION_OUTPUT_DIR_OPTION = "--output-dir";

    String PROVISION_OUTPUT_DIR_OPTION_SHORT = "-d";

    String PROVISION_OUTPUT_DIR_LABEL = "<output directory>";

    String STABILITY_OPTION = "--stability-level";
    String STABILITY_OPTION_SHORT = "-sl";

    String STABILITY_LABEL = "<default|community|preview|experimental>";
}
