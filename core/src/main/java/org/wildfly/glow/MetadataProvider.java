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

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 *
 * @author jdenise
 */
public interface MetadataProvider {

    Path getFeaturePacks(Space space, String version, String context, String kind) throws Exception;
    default Path getFeaturePacks(String version, String context, String kind) throws Exception {
        return getFeaturePacks(Space.DEFAULT, version, context, kind);
    }
    default Set<String> getAllVersions() throws Exception {
        return getAllVersions(Space.DEFAULT.getName());
    }
    Set<String> getAllVersions(String spaceName) throws Exception;
    Space getSpace(String spaceName) throws Exception;
    String getLatestVersion() throws Exception;
    List<Space> getAllSpaces() throws Exception;
    List<Variant> getAllVariants(String wildflyVersion) throws Exception;
}
