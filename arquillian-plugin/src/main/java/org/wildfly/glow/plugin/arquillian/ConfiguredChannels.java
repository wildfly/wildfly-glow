/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.glow.plugin.arquillian;

import static org.wildfly.channel.maven.VersionResolverFactory.DEFAULT_REPOSITORY_MAPPER;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.Repository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.maven.VersionResolverFactory;

public class ConfiguredChannels {

    private final ChannelSession channelSession;
    public ConfiguredChannels(List<ChannelConfiguration> channels,
            RepositorySystem system,
            RepositorySystemSession contextSession,
            List<RemoteRepository> repositories, Log log, boolean offline)
            throws MalformedURLException, UnresolvedMavenArtifactException, MojoExecutionException {
        if (channels.isEmpty()) {
            throw new MojoExecutionException("No channel specified.");
        }
        List<Channel> channelDefinitions = new ArrayList<>();
        for (ChannelConfiguration channelConfiguration : channels) {
            channelDefinitions.add(channelConfiguration.toChannel(repositories));
        }
        channelSession = buildChannelSession(system, contextSession, repositories, channelDefinitions);
    }

    ChannelSession getChannelSession() {
        return channelSession;
    }

    public static ChannelSession buildChannelSession(RepositorySystem system,
            RepositorySystemSession contextSession,
            List<RemoteRepository> repositories,
            List<org.wildfly.channel.Channel> channels) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
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
        return new ChannelSession(channels, factory);
    }
}
