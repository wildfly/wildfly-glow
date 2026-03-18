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
public interface LayerConfigurationProvider {
    URI getConfigurationURI(String layerName, String version, Set<String> spaces, String context, boolean preview, URI uri);
}
