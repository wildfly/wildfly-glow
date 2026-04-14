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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.ZipUtils;

/**
 *
 * @author jdenise
 */
public class WildFlyMavenMetadataProvider implements MetadataProvider, LayerConfigurationProvider {
    public static final String ROOT_CONFIG_DIRECTORY = "layers-configuration";
    private static final String DEFAULT_RANGE = "[0.0,)";
    private static final String METADATA_EXTENSION = "zip";
    private static final String METADATA_GROUP_ID = "org.wildfly.galleon.feature-packs";
    private static final String METADATA_ARTIFACT_ID = "wildfly-galleon-feature-packs-metadata";
    private final MavenRepoManager repo;
    private final Path tmpDirectory;
    private FeaturePacks featurePacks;
    private final Path rootDirectory ;
    public WildFlyMavenMetadataProvider(MavenRepoManager repo, Path tmpDirectory) {
        this.repo = repo;
        this.tmpDirectory = tmpDirectory;
        rootDirectory = tmpDirectory.resolve("glow-metadata");
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
            ZipUtils.unzip(zip, rootDirectory);
            featurePacks = new FeaturePacks(rootDirectory.toUri());
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
    public static String URItoPath(URI uri) {
        return uri.getPath().replace("/", "_");
    }
    public static Path toPath(String version, String space, String context, String variant) {
        return Paths.get(ROOT_CONFIG_DIRECTORY).resolve(version).resolve(space).resolve(variant == null ? "nominal" : variant).
            resolve(context);
    }
    private static Path toPath(String layerName, String version, String space, String context, String variant, URI uri) {
        return toPath(version, space, context, variant).resolve(layerName).resolve(URItoPath(uri));
    }
    @Override
    public URI getConfigurationURI(String layerName, String version, Set<String> spaces, String context, String variant, URI uri) {
        Objects.requireNonNull(version);
        Objects.requireNonNull(spaces);
        Objects.requireNonNull(context);
        Objects.requireNonNull(uri);
        if(spaces.isEmpty()) {
            throw new RuntimeException("At least one space is required.");
        }
        for (String s : spaces) {
            Path local = rootDirectory.resolve(toPath(layerName, version, s, context, variant, uri));
            if (Files.exists(local)) {
                return local.toUri();
            }
        }
        return uri;
    }

    @Override
    public List<Variant> getAllVariants(String wildflyVersion) throws Exception {
        return getResolver().getAllVariants(wildflyVersion);
    }

}
