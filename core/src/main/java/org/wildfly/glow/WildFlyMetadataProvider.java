/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
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
package org.wildfly.glow;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 *
 * @author jdenise
 */
public class WildFlyMetadataProvider implements MetadataProvider {

    private final URI uri;
    private FeaturePacks featurePacks;

    public WildFlyMetadataProvider(URI uri) {
        this.uri = uri;
    }

    private FeaturePacks getResolver() throws Exception {
        if (featurePacks == null) {
            featurePacks = new FeaturePacks(uri);
        }
        return featurePacks;
    }

    @Override
    public Path getFeaturePacks(Space space, String version, String context, String kind) throws Exception {
        return getResolver().getFeaturePacks(space, version, context, kind);
    }

    @Override
    public Set<String> getAllVersions(String spaceName) throws Exception {
        return getResolver().getAllVersions(spaceName);
    }

    @Override
    public Space getSpace(String spaceName) throws Exception {
        return getResolver().getSpace(spaceName);
    }

    @Override
    public String getLatestVersion() throws Exception {
        return getResolver().getLatestVersion();
    }

    @Override
    public List<Space> getAllSpaces() throws Exception {
        return getResolver().getAllSpaces();
    }

    @Override
    public List<Variant> getAllVariants(String wildflyVersion) throws Exception {
        return getResolver().getAllVariants(wildflyVersion);
    }

}
