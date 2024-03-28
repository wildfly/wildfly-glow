/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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
package org.wildfly.glow.deployment.openshift.api;

import io.fabric8.openshift.client.OpenShiftClient;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.wildfly.glow.GlowMessageWriter;

/**
 *
 * @author jdenise
 */
public interface Deployer {

    static final String LABEL = "deployment";

    String getName();

    Map<String, String> deploy(GlowMessageWriter writer, Path target, OpenShiftClient osClient, Map<String, String> env, String appHost, String appName, String matching, Map<String, String> extraEnv) throws Exception;

    default Map<String, String> disabledDeploy(String appHost, String appName, String matching, Map<String, String> env) {
        return Collections.emptyMap();
    }

    default Set<String> getSupportedLayers() {
        return Collections.emptySet();
    }

    default String getSupportedAddOnFamily() {
        return null;
    }

    default Set<String> getSupportedAddOns() {
        return Collections.emptySet();
    }
}
