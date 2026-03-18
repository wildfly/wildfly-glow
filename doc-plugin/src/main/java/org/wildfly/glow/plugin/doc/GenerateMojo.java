/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.glow.plugin.doc;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.maven.project.MavenProjectHelper;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.wildfly.channel.Channel;
import org.wildfly.glow.DefaultLayerConfigurationProvider;
import org.wildfly.glow.Layer;
import org.wildfly.glow.MetadataProvider;
import org.wildfly.glow.Space;
import org.wildfly.glow.Utils;
import org.wildfly.glow.WildFlyMavenMetadataProvider;
import org.wildfly.glow.WildFlyMetadataProvider;

/**
 *
 * @author jdenise
 */
@Mojo(name = "generate-maven-metadata", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class GenerateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;
    @Component
    RepositorySystem repoSystem;

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repositories;

    @Parameter(defaultValue = "glow-maven-metadata.zip")
    String generatedFile;

    @Parameter(defaultValue = "${project.build.directory}")
    String targetDir;

    @Parameter(required = true)
    String repoPath;

    @Parameter(required = false, defaultValue = "true")
    boolean preview;

    @Parameter(required = false)
    List<ChannelConfiguration> channels;

    @Parameter(required = false)
    String minVersionWithPackagedConfig;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Path rootDir = Paths.get(repoPath);
            MetadataProvider provider = new WildFlyMetadataProvider(rootDir.toFile().getAbsoluteFile().toURI());
            Set<String> versions = provider.getAllVersions();
            Path dir = Paths.get(targetDir);
            Path generatedFilePath = dir.resolve(generatedFile);
            Path versionsFile = rootDir.resolve("versions.yaml");
            Path metadataDir = dir.resolve("metadata");
            Files.createDirectories(metadataDir);
            Files.copy(versionsFile, metadataDir.resolve("versions.yaml"));
            for (String version : versions) {
                Path versionDir = rootDir.resolve(version);
                Path versionTargetDir = metadataDir.resolve(version);
                IoUtils.copy(versionDir, versionTargetDir);
            }
            Path spacesDir = rootDir.resolve("spaces");
            Path spacesTargetDir = metadataDir.resolve("spaces");
            IoUtils.copy(spacesDir, spacesTargetDir);
            addLayersConfiguration(metadataDir);
            ZipUtils.zip(metadataDir, generatedFilePath);
            getLog().debug("Attaching maven metadata " + generatedFilePath + " as a project artifact");
            projectHelper.attachArtifact(project, "zip", generatedFilePath.toFile());
        } catch (Exception ex) {
            throw new MojoExecutionException(ex);
        }
    }
    private void addLayersConfiguration(Path metadataDir) throws Exception {
        Path tmpDirectory = null;
        try {
            MetadataProvider metadataProvider;
            MavenRepoManager artifactResolver;
            if (channels != null && !channels.isEmpty()) {
                getLog().debug("WildFly channel enabled.");
                List<Channel> lst = new ArrayList<>();
                for (ChannelConfiguration conf : channels) {
                    lst.add(conf.toChannel(repositories));
                }
                artifactResolver = new ChannelMavenArtifactRepositoryManager(lst, repoSystem, repoSession, repositories);
            } else {
                artifactResolver = new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories);
            }
            if (repoPath != null) {
                Path p = Paths.get(repoPath);
                String repoUrl = "file://" + p.toAbsolutePath();
                System.out.println("Using repo url " + repoUrl);
                metadataProvider = new WildFlyMetadataProvider(new URI(repoUrl));
            } else {
                tmpDirectory = Files.createTempDirectory("wildfly-glow-metadata");
                metadataProvider = new WildFlyMavenMetadataProvider(artifactResolver, tmpDirectory);
            }
            UniverseResolver universeResolver = UniverseResolver.builder().addArtifactResolver(artifactResolver).build();
            GalleonBuilder provider = new GalleonBuilder();
            provider.addArtifactResolver(artifactResolver);
            Set<String> versions = new TreeSet<>();
            if (minVersionWithPackagedConfig == null) {
                versions.add(metadataProvider.getLatestVersion());
            } else {
                Comparator<String> versionComparator = new Comparator<>() {
                    @Override
                    public int compare(String o1, String o2) {
                        String[] items1 = o1.split("\\.");
                        String[] items2 = o2.split("\\.");
                        int major1 = Integer.parseInt(items1[0]);
                        int major2 = Integer.parseInt(items2[0]);
                        if (major1 > major2) {
                            return 1;
                        } else {
                            if (major1 < major2) {
                                return -1;
                            }
                        }
                        int minor1 = Integer.parseInt(items1[1]);
                        int minor2 = Integer.parseInt(items2[1]);
                        if (minor1 > minor2) {
                            return 1;
                        } else {
                            if (minor1 < minor2) {
                                return -1;
                            }
                        }
                        int micro1 = Integer.parseInt(items1[2]);
                        int micro2 = Integer.parseInt(items2[2]);
                        if (micro1 > micro2) {
                            return 1;
                        } else {
                            if (micro1 < micro2) {
                                return -1;
                            }
                        }
                        return items1[3].compareTo(items2[3]);
                    }
                };
                for(String s : metadataProvider.getAllVersions()) {
                    if(!s.contains("-SNAPSHOT") && versionComparator.compare(s, minVersionWithPackagedConfig) >= 0) {
                        versions.add(s);
                    }
                }
            }
            for (String version : versions) {
                System.out.println("Adding Layers configuration for version " + version);
                //First default
                storeLayersConfig(version, metadataProvider, provider, universeResolver, metadataDir, Space.DEFAULT, "bare-metal", false);
                storeLayersConfig(version, metadataProvider, provider, universeResolver, metadataDir, Space.DEFAULT, "cloud", false);
                if (preview) {
                    // Then preview
                    storeLayersConfig(version, metadataProvider, provider, universeResolver, metadataDir, Space.DEFAULT, "bare-metal", true);
                    storeLayersConfig(version, metadataProvider, provider, universeResolver, metadataDir, Space.DEFAULT, "cloud", true);
                }
                for (Space space : metadataProvider.getAllSpaces()) {
                    //First default
                    storeLayersConfig(version, metadataProvider, provider, universeResolver, metadataDir, space, "bare-metal", false);
                    storeLayersConfig(version, metadataProvider, provider, universeResolver, metadataDir, space, "cloud", false);
                    if (preview) {
                        // Then preview
                        storeLayersConfig(version, metadataProvider, provider, universeResolver, metadataDir, space, "bare-metal", true);
                        storeLayersConfig(version, metadataProvider, provider, universeResolver, metadataDir, space, "cloud", true);
                    }
                }
            }
        } finally {
            if (tmpDirectory != null) {
                IoUtils.recursiveDelete(tmpDirectory);
            }
        }
    }
    private void storeLayersConfig(String version, MetadataProvider metadataProvider, GalleonBuilder provider, UniverseResolver universeResolver,
            Path layersConfigRootDir, Space space, String context, boolean preview) throws Exception {
        Path provisioningXML = metadataProvider.getFeaturePacks(space, version, context, preview);
        Map<String, Layer> all;
        try (Provisioning p = provider.newProvisioningBuilder(provisioningXML).build()) {
            GalleonProvisioningConfig config = p.loadProvisioningConfig(provisioningXML);
            Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies = new HashMap<>();
            all = Utils.getAllLayers(config, universeResolver, p, fpDependencies);
            // Filter-out the layers that are not in the additional space
            if (!Space.DEFAULT.equals(space)) {
                Set<String> toRemove = new HashSet<>();
                for(Map.Entry<String, Layer> entry : all.entrySet()) {
                    Layer l = entry.getValue();
                    boolean toInclude = false;
                    for(FeaturePackLocation.FPID fpid : l.getFeaturePacks()) {
                        if (config.getFeaturePackDep(fpid.getProducer()) != null) {
                            toInclude = true;
                            break;
                        }
                    }
                    if (!toInclude) {
                        toRemove.add(entry.getKey());
                    }
                }
                for(String k : toRemove) {
                    all.remove(k);
                }
            }
            Set<String> set = new HashSet<>();
            set.add(space.getName());
            Utils.buildMapping(new DefaultLayerConfigurationProvider(), version, set, context, preview, all, new HashSet<>());
            for (Layer l : all.values()) {
                if (!l.getConfiguration().isEmpty()) {
                    Path layerDir = layersConfigRootDir.resolve(WildFlyMavenMetadataProvider.toPath(version, space.getName(), context, preview));
                    layerDir = layerDir.resolve(l.getName());
                    Files.createDirectories(layerDir);
                    for (String c : l.getConfiguration()) {
                        URI uri = new URI(c);
                        Path filePath = layerDir.resolve(Paths.get(WildFlyMavenMetadataProvider.URItoPath(uri)));
                        try (InputStream in = uri.toURL().openStream()) {
                            Files.copy(in, filePath);
                        }
                    }
                }
            }
        }
    }
}
