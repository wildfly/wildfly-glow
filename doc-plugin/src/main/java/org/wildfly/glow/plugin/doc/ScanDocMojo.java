/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

import java.io.FileInputStream;
import java.nio.file.Files;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
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
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.Layer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.wildfly.glow.FeaturePacks;
import org.wildfly.glow.LayerMetadata;
import org.wildfly.glow.Utils;

/**
 *
 * @author jdenise
 */
@Mojo(name = "scan-doc", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class ScanDocMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;
    @Component
    RepositorySystem repoSystem;

    @Component
    BuildPluginManager pluginManager;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repositories;

    @Parameter(defaultValue = "rules.adoc")
    String generatedFile;

    @Parameter(defaultValue = "${project.build.directory}")
    String targetDir;

    @Parameter(required = false)
    String rulesPropertiesFile;

    @Parameter(required = false, defaultValue = "true")
    boolean generateRuleDescriptions;

    @Parameter(required = false, defaultValue = "true")
    boolean generateKnownFeaturePacks;

    @Parameter(required = false)
    String repoPath;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            StringBuilder rulesBuilder = new StringBuilder();

            if (generateRuleDescriptions) {
                Properties properties = new Properties();
                try (FileInputStream in = new FileInputStream(Paths.get(rulesPropertiesFile).toFile())) {
                    properties.load(in);
                }

                rulesBuilder.append("== [[glow.table.rules]]Rules descriptions\n");
                rulesBuilder.append("[cols=\"1,2,1\"]\n");
                rulesBuilder.append("|===\n");
                rulesBuilder.append("|Rule |Description |Value\n");
                for (String k : LayerMetadata.getAllRules()) {
                    rulesBuilder.append("|[[glow." + k + "]]" + k + "\n");
                    String desc = properties.getProperty(k);
                    String val = properties.getProperty(k + ".value");
                    if (desc == null) {
                        throw new Exception("Missing rule description for " + k + " in " + rulesPropertiesFile);
                    }
                    if (val == null) {
                        throw new Exception("Missing rule example value for " + k + " in " + rulesPropertiesFile);
                    }
                    rulesBuilder.append("|" + desc + "\n");
                    rulesBuilder.append("|" + val + "\n");
                }
                rulesBuilder.append("|===\n");
            }
            if (generateKnownFeaturePacks) {
                if (repoPath != null) {
                    Path p = Paths.get(repoPath);
                    String repoUrl = "file://" + p.toAbsolutePath();
                    System.out.println("Using repo url " + repoUrl);
                    System.setProperty(FeaturePacks.URL_PROPERTY, repoUrl);
                }
                try {
                    //Typically under target
                    Path outputFolder = Paths.get(project.getBuild().getDirectory());
                    MavenRepoManager artifactResolver = new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories);
                    UniverseResolver universeResolver = UniverseResolver.builder().addArtifactResolver(artifactResolver).build();
                    Map<Layer, Map<String, String>> rules = new TreeMap<>();

                    getRules("bare-metal", universeResolver, rules);
                    Map<Layer, Map<String, String>> cloudRules = new TreeMap<>();
                    getRules("cloud", universeResolver, cloudRules);
                    rulesBuilder.append("## Support for WildFly " + FeaturePacks.getLatestVersion() + "\n\n");

                    rulesBuilder.append(buildTable("bare-metal", rules, false));
                    rulesBuilder.append(buildTable("cloud", cloudRules, false));

                    rulesBuilder.append("## Support for WildFly Preview " + FeaturePacks.getLatestVersion() + "\n\n");

                    rulesBuilder.append(buildTable("bare-metal", rules, true));
                    rulesBuilder.append(buildTable("cloud", cloudRules, true));
                } finally {
                    System.clearProperty(FeaturePacks.URL_PROPERTY);
                }
            }
            Path dir = Paths.get(targetDir);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(generatedFile), rulesBuilder.toString());
        } catch (Exception ex) {
            throw new MojoExecutionException(ex);
        }
    }

    private String buildTable(String context, Map<Layer, Map<String, String>> rules, boolean preview) throws Exception {

        StringBuilder rulesBuilder = new StringBuilder();
        rulesBuilder.append("\n### " + context + "\n");
        rulesBuilder.append("\n#### Supported Galleon feature-packs \n");
        Path provisioningXML = FeaturePacks.getFeaturePacks(null, context, preview);
        ProvisioningConfig pConfig = ProvisioningXmlParser.parse(provisioningXML);
        for (FeaturePackConfig c : pConfig.getFeaturePackDeps()) {
            rulesBuilder.append("* " + c.getLocation() + " \n");
        }
        rulesBuilder.append("\n#### [[glow.table." + context + "]]Galleon layers and associated discovery rules\n");
        rulesBuilder.append("[cols=\"25%,50%,25%\"]\n");
        rulesBuilder.append("|===\n");
        rulesBuilder.append("|Layer |Rule(s) |Feature-pack(s)\n");
        for (Layer l : rules.keySet()) {
            rulesBuilder.append("|" + l + "\n");
            rulesBuilder.append("|\n");
            Map<String, String> local = rules.get(l);
            for (String k : local.keySet()) {
                String ruleClass = LayerMetadata.getRuleClass(k);
                if (ruleClass == null) {
                    throw new Exception("Unknown rule " + k);
                }
                rulesBuilder.append("link:#glow." + ruleClass + "[" + k + "]" + "=" + local.get(k)).append(" +\n");
            }
            rulesBuilder.append("l|\n");
            for (FeaturePackLocation.FPID id : l.getFeaturePacks()) {
                rulesBuilder.append(id + "\n");
            }
        }
        rulesBuilder.append("|===\n");
        return rulesBuilder.toString();
    }

    private void getRules(String context, UniverseResolver universeResolver,
            Map<Layer, Map<String, String>> rules) throws Exception {
        try (ProvisioningLayout<FeaturePackLayout> layout = Utils.buildLayout(context,
                null, null, GlowMessageWriter.DEFAULT, false)) {
            Map<String, Layer> all;
            Map<FeaturePackLocation.FPID, Set<FeaturePackLocation.ProducerSpec>> fpDependencies = new HashMap<>();
            try {
                all = Utils.getAllLayers(universeResolver, layout, fpDependencies);
            } finally {
                layout.close();
            }

            for (Layer l : all.values()) {
                if (!l.getProperties().isEmpty()) {
                    Map<String, String> props = rules.computeIfAbsent(l, (value) -> new TreeMap<>());
                    props.putAll(l.getProperties());
                }
            }
        }
    }
}
