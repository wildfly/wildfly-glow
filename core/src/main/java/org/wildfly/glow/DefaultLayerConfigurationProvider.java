/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.glow;

import java.net.URI;
import java.util.Set;

/**
 *
 * @author jdenise
 */
public class DefaultLayerConfigurationProvider implements LayerConfigurationProvider {

    @Override
    public URI getConfigurationURI(String layerName, String version, Set<String> spaces, String context, String variant, URI uri) {
        return uri;
    }
}
