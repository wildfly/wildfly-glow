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
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.ZipUtils;

/**
 *
 * @author jdenise
 */
public class WildFlyMavenMetadataProvider implements MetadataProvider {

    private static final String DEFAULT_RANGE = "[0.0,)";
    private static final String METADATA_EXTENSION = "zip";
    private static final String METADATA_GROUP_ID = "org.wildfly.galleon.feature-packs";
    private static final String METADATA_ARTIFACT_ID = "wildfly-galleon-feature-packs-metadata";
    private final MavenRepoManager repo;
    private final Path tmpDirectory;
    private FeaturePacks featurePacks;
    public WildFlyMavenMetadataProvider(MavenRepoManager repo, Path tmpDirectory) {
        this.repo = repo;
        this.tmpDirectory = tmpDirectory;
    }

    private FeaturePacks getResolver() throws Exception {
        if (featurePacks == null) {
            MavenArtifact artifact = new MavenArtifact();
            artifact.setExtension(METADATA_EXTENSION);
            artifact.setArtifactId(METADATA_ARTIFACT_ID);
            artifact.setGroupId(METADATA_GROUP_ID);
            artifact.setVersionRange(DEFAULT_RANGE);
            repo.resolveLatestVersion(artifact, null, false);
            Path zip = artifact.getPath();
            Path rootDirectory = tmpDirectory.resolve("glow-metadata");
            ZipUtils.unzip(zip, rootDirectory);
            featurePacks = new FeaturePacks(rootDirectory.toUri());
        }
        return featurePacks;
    }

    @Override
    public Path getFeaturePacks(Space space, String version, String context, boolean techPreview) throws Exception {
        return getResolver().getFeaturePacks(space, version, context, techPreview);
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

}
