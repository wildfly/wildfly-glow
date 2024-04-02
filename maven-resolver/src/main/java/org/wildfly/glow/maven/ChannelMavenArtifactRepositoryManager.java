/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.glow.maven;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.jboss.galleon.api.MavenStreamResolver;

import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.ArtifactTransferException;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.NoStreamFoundException;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.ChannelResolvable;

public class ChannelMavenArtifactRepositoryManager implements MavenRepoManager, ChannelResolvable, MavenStreamResolver {

    private static final String REQUIRE_CHANNEL_FOR_ALL_ARTIFACT = "org.wildfly.plugins.galleon.all.artifact.requires.channel.resolution";

    private final ChannelSession channelSession;

    public ChannelMavenArtifactRepositoryManager(ChannelSession channelSession) {
        this.channelSession = channelSession;
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        try {
            resolveFromChannels(artifact);
        } catch (ArtifactTransferException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        } catch (NoStreamFoundException ex) {
            boolean requireChannel = Boolean.parseBoolean(artifact.getMetadata().get(REQUIRE_CHANNEL_FOR_ALL_ARTIFACT));

            // unable to resolve the artifact through the channel.
            // if the version is defined, let's resolve it directly
            if (artifact.getVersion() == null) {
                throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
            }
            try {

                org.wildfly.channel.MavenArtifact mavenArtifact = channelSession.resolveDirectMavenArtifact(
                        artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(),
                        artifact.getVersion());
                artifact.setPath(mavenArtifact.getFile().toPath());
            } catch (UnresolvedMavenArtifactException e) {
                // if the artifact can not be resolved directly either, we abort
                throw new MavenUniverseException(e.getLocalizedMessage(), e);
            }
        }
    }

    private void resolveFromChannels(MavenArtifact artifact) throws UnresolvedMavenArtifactException {
        org.wildfly.channel.MavenArtifact result = channelSession.resolveMavenArtifact(artifact.getGroupId(),
                artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), artifact.getVersion());
        artifact.setVersion(result.getVersion());
        artifact.setPath(result.getFile().toPath());
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public boolean isResolved(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public boolean isLatestVersionResolved(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion,
            Pattern excludeVersion) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, boolean locallyAvailable)
            throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion,
            Pattern excludeVersion) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact, Pattern includeVersion, Pattern excludeVersion)
            throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public void install(MavenArtifact artifact, Path path) throws MavenUniverseException {
        throw new MavenUniverseException("Channel resolution can't be applied to Galleon universe");
    }

    @Override
    public String getLatestVersion(String groupId, String artifactId, String extension, String classifier, String baseVersion) {
        org.wildfly.channel.MavenArtifact result = channelSession.resolveMavenArtifact(groupId,
                artifactId, "jar", classifier, baseVersion);
        return result.getVersion();
    }

}
