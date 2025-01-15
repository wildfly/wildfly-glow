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
import java.util.List;
import java.util.Set;
import org.apache.maven.project.MavenProjectHelper;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.wildfly.glow.MetadataProvider;
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
            ZipUtils.zip(metadataDir, generatedFilePath);
            getLog().debug("Attaching maven metadata " + generatedFilePath + " as a project artifact");
            projectHelper.attachArtifact(project, "zip", generatedFilePath.toFile());
        } catch (Exception ex) {
            throw new MojoExecutionException(ex);
        }
    }
}
