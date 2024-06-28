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
package org.wildfly.glow.cli.support;

import java.util.Collections;
import java.util.Set;
import org.wildfly.glow.ConfigurationResolver;
import org.wildfly.glow.Env;
import org.wildfly.glow.Layer;
import org.wildfly.glow.deployment.openshift.api.OpenShiftSupport;

/**
 *
 * @author jdenise
 */
public class CLIConfigurationResolver implements ConfigurationResolver {

    private final Set<String> disabledDeployers;
    private final Set<String> enabledDeployers;
    private final boolean isOpenShift;
    public CLIConfigurationResolver() {
        this(true, null, null);
    }
    public CLIConfigurationResolver(boolean isOpenShift,
            Set<String> disabledDeployers,
            Set<String> enabledDeployers) {
        this.disabledDeployers = disabledDeployers == null ? Collections.emptySet() : disabledDeployers;
        this.enabledDeployers = enabledDeployers == null ? Collections.emptySet() : enabledDeployers;
        this.isOpenShift = isOpenShift;
    }

    @Override
    public ResolvedEnvs getResolvedEnvs(Layer layer, Set<Env> input) throws Exception {
        if (isOpenShift) {
            return OpenShiftSupport.getResolvedEnvs(layer, input, disabledDeployers, enabledDeployers);
        }
        return null;
    }

    @Override
    public String getPossibleDeployer(Set<Layer> layers) throws Exception {
        return OpenShiftSupport.getPossibleDeployer(layers, disabledDeployers, enabledDeployers);
    }
}
