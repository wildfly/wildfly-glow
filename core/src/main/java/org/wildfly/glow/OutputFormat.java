/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.glow;

/**
 *
 * @author jdenise
 */
public enum OutputFormat {
    PROVISIONING_XML("provisioning.xml", "Galleon provisioning file usable with Galleon tooling."),
    SERVER("server", "Provision a WildFly server."),
    BOOTABLE_JAR("bootable-jar", "Provision a WildFly bootable jar."),
    DOCKER_IMAGE("docker-image", "Produce a docker image."),
    OPENSHIFT("openshift", "Build and deploy on OpenShift.");

    public final String name;
    public final String description;

    OutputFormat(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
