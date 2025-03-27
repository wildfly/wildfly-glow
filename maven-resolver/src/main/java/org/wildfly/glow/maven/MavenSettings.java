/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.glow.maven;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.RepositoryPolicy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

/**
 *
 * @author jdenise
 */
public class MavenSettings {

    private static final String EXTERNAL = "external:";
    private static final String ALL = "*";
    private static final String NOT = "!";
    private final List<RemoteRepository> repositories;
    private final RepositorySystemSession session;
    private final org.eclipse.aether.repository.Proxy proxy;
    private final MavenProxySelector proxySelector;

    MavenSettings(RepositorySystem repoSystem, Path settingsPath) throws Exception {
        Settings settings = buildMavenSettings(settingsPath);
        Proxy proxy = settings.getActiveProxy();
        if (proxy != null) {
            MavenProxySelector.Builder builder = new MavenProxySelector.Builder(proxy.getHost(), proxy.getPort(), proxy.getProtocol());
            builder.setPassword(proxy.getPassword());
            builder.setUserName(proxy.getUsername());
            if (proxy.getNonProxyHosts() != null) {
                String[] hosts = proxy.getNonProxyHosts().split("\\|");
                builder.addNonProxyHosts(Arrays.asList(hosts));
            }
            proxySelector = builder.build();
            Authentication auth = null;
            if (proxy.getPassword() != null && proxy.getUsername() != null) {
                auth = new AuthenticationBuilder().addUsername(proxy.getUsername()).addPassword(proxy.getPassword()).build();
            }
            this.proxy = new org.eclipse.aether.repository.Proxy(proxy.getProtocol(), proxy.getHost(), proxy.getPort(), auth);
        } else {
            this.proxy = null;
            proxySelector = null;
        }
        repositories = Collections.unmodifiableList(buildRemoteRepositories(settings));
        session = MavenResolver.newMavenSession(repoSystem,
                settings.getLocalRepository() == null ? null : Paths.get(settings.getLocalRepository()),
                proxySelector, settings.isOffline());
    }

    static Path getMavenSettingsFile() throws Exception {
        String customFile = System.getProperty("org.wildfly.glow.maven.settings.xml.url");
        if (customFile != null) {
            Path customSettingsPath = Paths.get(new URL(customFile).toURI());
            if (Files.exists(customSettingsPath)) {
                return customSettingsPath;
            }
        }

        Path m2 = Paths.get(System.getProperty("user.home"), ".m2");
        Path userSettingsPath = m2.resolve("settings.xml");
        if (Files.exists(userSettingsPath)) {
            return userSettingsPath;
        }

        String mavenHome = System.getenv("M2_HOME");
        if (mavenHome != null) {
            Path globalSettingsPath = Paths.get(mavenHome, "conf", "settings.xml");
            if (Files.exists(globalSettingsPath)) {
                return globalSettingsPath;
            }
        }
        return null;
    }

    public List<RemoteRepository> getRepositories() {
        return repositories;
    }

    public RepositorySystemSession getSession() {
        return session;
    }

    private static Settings buildMavenSettings(Path settingsPath) throws Exception {
        SettingsBuildingRequest settingsBuildingRequest = new DefaultSettingsBuildingRequest();
        settingsBuildingRequest.setSystemProperties(System.getProperties());
        settingsBuildingRequest.setUserSettingsFile(settingsPath.toFile());
        SettingsBuildingResult settingsBuildingResult;
        DefaultSettingsBuilderFactory mvnSettingBuilderFactory = new DefaultSettingsBuilderFactory();
        DefaultSettingsBuilder settingsBuilder = mvnSettingBuilderFactory.newInstance();
        settingsBuildingResult = settingsBuilder.build(settingsBuildingRequest);

        return settingsBuildingResult.getEffectiveSettings();
    }

    private List<RemoteRepository> buildRemoteRepositories(Settings settings) throws Exception {
        Map<String, RemoteRepository> repos = new LinkedHashMap<>();
        List<RemoteRepository> repositories = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        for (Profile profile : settings.getProfiles()) {
            if ((profile.getActivation() != null && profile.getActivation().isActiveByDefault()) || settings.getActiveProfiles().contains(profile.getId())) {
                List<Repository> mavenRepositories = profile.getRepositories();
                for (Repository repo : mavenRepositories) {
                    repos.put(repo.getId(), buildRepository(repo.getId(), repo.getLayout(),
                            repo.getUrl(), settings, repo.getReleases(), repo.getSnapshots(), null));
                    urls.add(repo.getUrl());
                }
            }
        }
        boolean ignoreDefaultRepos = Boolean.getBoolean("org.wildfly.glow.maven.ignore.default.repos");
        if (!ignoreDefaultRepos) {
            List<RemoteRepository> defaultRepositories = MavenResolver.getMissingDefaultRepositories(urls, proxySelector, proxy);
            for (RemoteRepository r : defaultRepositories) {
                repos.put(r.getId(), r);
            }
        }
        repositories.addAll(handleMirroring(settings, repos));
        // Then the remaining repositories
        for (Map.Entry<String, RemoteRepository> entry : repos.entrySet()) {
            repositories.add(entry.getValue());
        }
        return repositories;
    }

    private List<RemoteRepository> handleMirroring(Settings settings, Map<String, RemoteRepository> repos) throws MalformedURLException {
        List<RemoteRepository> repositories = new ArrayList<>();
        // Mirrors are hidding actual repo.
        for (Mirror mirror : settings.getMirrors()) {
            String[] patterns = mirror.getMirrorOf().split(",");
            List<RemoteRepository> mirrored = new ArrayList<>();
            boolean all = false;
            List<String> excluded = new ArrayList<>();
            for (String p : patterns) {
                p = p.trim();
                if (ALL.equals(p)) {
                    all = true;
                } else if (p.startsWith(NOT)) {
                    excluded.add(p.substring(NOT.length()));
                }
            }
            if (all) {
                // Add all except the excluded ones.
                List<String> safeKeys = new ArrayList<>(repos.keySet());
                for (String k : safeKeys) {
                    if (!excluded.contains(k)) {
                        mirrored.add(repos.remove(k));
                    }
                }
            } else {
                for (String p : patterns) {
                    p = p.trim();
                    if (p.startsWith(EXTERNAL)) {
                        System.err.println("external:* mirroring is not supported, "
                                + "skipping configuration item");
                        continue;
                    }
                    RemoteRepository m = repos.get(p);
                    if (m != null) {
                        // Remove from the initial map, it is hidden by mirror
                        mirrored.add(repos.remove(p));
                    }
                }
            }
            if (!mirrored.isEmpty()) { // We have an active mirror
                repositories.add(buildRepository(mirror.getId(),
                        mirror.getLayout(), mirror.getUrl(), settings, null, null, mirrored));
            }
        }
        return repositories;
    }

    private static org.eclipse.aether.repository.RepositoryPolicy fromMavenRepositoryPolicy(RepositoryPolicy repositoryPolicy) {
        return new org.eclipse.aether.repository.RepositoryPolicy(
                repositoryPolicy.isEnabled(),
                repositoryPolicy.getUpdatePolicy(),
                repositoryPolicy.getChecksumPolicy()
        );
    }

    private static Consumer<RepositoryPolicy> forPolicy(Consumer<org.eclipse.aether.repository.RepositoryPolicy> mappedPolicyConsumer) {
        return repositoryPolicy ->
                Optional.ofNullable(repositoryPolicy)
                        .map(MavenSettings::fromMavenRepositoryPolicy)
                        .ifPresent(mappedPolicyConsumer);
    }

    private static Optional<Authentication> authenticationForServer(Server server) {
        if (server.getUsername() != null) {
            AuthenticationBuilder authBuilder = new AuthenticationBuilder();
            authBuilder.addPassword(server.getPassword());
            authBuilder.addUsername(server.getUsername());
            return Optional.of(authBuilder.build());
        } else if (server.getPrivateKey() != null) {
            AuthenticationBuilder authBuilder = new AuthenticationBuilder();
            authBuilder.addPrivateKey(server.getPrivateKey(), server.getPassphrase());
            return Optional.of(authBuilder.build());
        } else {
            return Optional.empty();
        }
    }

    private RemoteRepository buildRepository(
            String id,
            String type,
            String url,
            Settings settings,
            RepositoryPolicy releasePolicy,
            RepositoryPolicy snapshotPolicy,
            List<RemoteRepository> mirrored) throws MalformedURLException {
        RemoteRepository.Builder builder = new RemoteRepository.Builder(id,
                type == null ? MavenResolver.DEFAULT_REPOSITORY_TYPE : type,
                url);

        forPolicy(builder::setReleasePolicy).accept(releasePolicy);
        forPolicy(builder::setSnapshotPolicy).accept(snapshotPolicy);

        for (var server : settings.getServers()) {
            if (server.getId().equals(id)) {
                authenticationForServer(server).ifPresent(builder::setAuthentication);
            }
        }

        if (mirrored != null) {
            builder.setMirroredRepositories(mirrored);
        }
        if (proxySelector != null && proxySelector.proxyFor(new URL(url).getHost())) {
            builder.setProxy(proxy);
        }
        return builder.build();
    }

}
