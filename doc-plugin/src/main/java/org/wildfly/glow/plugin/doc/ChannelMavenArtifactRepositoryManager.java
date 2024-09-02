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
package org.wildfly.glow.plugin.doc;

import static org.wildfly.channel.maven.VersionResolverFactory.DEFAULT_REPOSITORY_MAPPER;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.jboss.galleon.api.MavenStreamResolver;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.ArtifactTransferException;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.NoStreamFoundException;
import org.wildfly.channel.Repository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.VersionResult;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.ChannelResolvable;

public class ChannelMavenArtifactRepositoryManager implements MavenRepoManager, ChannelResolvable, MavenStreamResolver {

    private final ChannelSession channelSession;
    private final RepositorySystem system;
    private final DefaultRepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    public ChannelMavenArtifactRepositoryManager(List<Channel> channels,
            RepositorySystem system,
            RepositorySystemSession contextSession,
            List<RemoteRepository> repositories)
            throws Exception {
        session = MavenRepositorySystemUtils.newSession();
        this.repositories = repositories;
        session.setLocalRepositoryManager(contextSession.getLocalRepositoryManager());
        Map<String, RemoteRepository> mapping = new HashMap<>();
        for (RemoteRepository r : repositories) {
            mapping.put(r.getId(), r);
        }
        Function<Repository, RemoteRepository> mapper = r -> {
            RemoteRepository rep = mapping.get(r.getId());
            if (rep == null) {
                rep = DEFAULT_REPOSITORY_MAPPER.apply(r);
            }
            return rep;
        };
        VersionResolverFactory factory = new VersionResolverFactory(system, session, mapper);
        channelSession = new ChannelSession(channels, factory);
        this.system = system;
    }

    public ChannelSession getChannelSession() {
        return channelSession;
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        try {
            resolveFromChannels(artifact);
        } catch (ArtifactTransferException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        } catch (NoStreamFoundException ex) {
                // unable to resolve the artifact through the channel.
                // if the version is defined, let's resolve it directly
                if (artifact.getVersion() == null || artifact.getVersion().isEmpty()) {
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
        resolveLatestVersion(artifact, null, false);
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, boolean locallyAvailable)
            throws MavenUniverseException {
        artifact.setVersion(getLatestVersion(artifact));
        resolve(artifact);
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact) throws MavenUniverseException {
        return getLatestVersion(artifact, null, null, null);
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        return getLatestVersion(artifact, lowestQualifier, null, null);
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion,
            Pattern excludeVersion) throws MavenUniverseException {
        try {
            return channelSession.resolveMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                    artifact.getClassifier(), null).getVersion();
        } catch (UnresolvedMavenArtifactException e) {
            VersionRangeResult res = getVersionRange(new DefaultArtifact(artifact.getGroupId(),
                    artifact.getArtifactId(), artifact.getExtension(), artifact.getVersionRange()));
            return res.getHighestVersion().toString();
        }
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
        VersionResult res = channelSession.findLatestMavenArtifactVersion(groupId, artifactId, extension, classifier,
                baseVersion);
        return res.getVersion();
    }

    private VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(artifact);
        rangeRequest.setRepositories(repositories);
        VersionRangeResult rangeResult;
        try {
            rangeResult = system.resolveVersionRange(session, rangeRequest);
        } catch (VersionRangeResolutionException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        }
        return rangeResult;
    }

}
