/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
package org.wildfly.glow.plugin.arquillian;

import java.io.File;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.wildfly.glow.plugin.arquillian.apache.surefire.testset.TestListResolver;
import org.wildfly.glow.plugin.arquillian.apache.surefire.util.DefaultScanResult;
import org.wildfly.glow.plugin.arquillian.apache.surefire.util.DirectoryScanner;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import static java.util.Collections.singletonList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.wildfly.glow.plugin.arquillian.apache.surefire.util.DependencyScanner;

class CopiedFromSureFirePlugin {

    private final PluginExecution execution;

    private static final String[] DEFAULT_INCLUDES = new String[]{"**/Test*.java", "**/*Test.java", "**/*Tests.java", "**/*TestCase.java"};
    private final Path testClassesDirectory;
    private final List<String> dependenciesToScan;
    private final List<Artifact> projectTestArtifacts;
    public CopiedFromSureFirePlugin(Path testClassesDirectory, List<String> dependenciesToScan, List<Artifact> projectTestArtifacts, PluginExecution execution) {
        this.testClassesDirectory = testClassesDirectory;
        this.dependenciesToScan = dependenciesToScan;
        this.projectTestArtifacts = projectTestArtifacts;
        this.execution = execution;
    }

    DefaultScanResult scanForTestClasses()
            throws MojoFailureException {
        DefaultScanResult scan = scanDirectories();
        DefaultScanResult scanDeps = scanDependencies();
        return scan.append(scanDeps);
    }

    private DefaultScanResult scanDirectories()
            throws MojoFailureException {
        DirectoryScanner scanner = new DirectoryScanner(testClassesDirectory.toFile(), getIncludedAndExcludedTests());
        return scanner.scan();
    }

    private TestListResolver getIncludedAndExcludedTests() {
        List<String> includes = Collections.emptyList();
        List<String> excludes = Collections.emptyList();
        if (execution != null) {
            Xpp3Dom config = (Xpp3Dom) execution.getConfiguration();

            if (config != null) {
                Xpp3Dom includesDom = config.getChild("includes");
                if (includesDom != null) {
                    includes = Arrays.stream(includesDom.getChildren("include")).map(c -> c.getValue()).collect(Collectors.toList());
                }

                Xpp3Dom excludesDom = config.getChild("excludes");
                if (excludesDom != null) {
                    excludes = Arrays.stream(excludesDom.getChildren("exclude")).map(c -> c.getValue()).collect(Collectors.toList());
                }
            }
        }
        if (includes.isEmpty()) {
            includes = Arrays.asList(DEFAULT_INCLUDES);
        }
        // We handle the default excludes (trimming anonymous classes) in the caller

        return new TestListResolver(includes, excludes);
    }

    DefaultScanResult scanDependencies() throws MojoFailureException {
        if (getDependenciesToScan() == null) {
            return null;
        } else {
            try {
                DefaultScanResult result = null;

                List<Artifact> dependenciesToScan
                        = DependencyScanner.filter(projectTestArtifacts, getDependenciesToScan());

                for (Artifact artifact : dependenciesToScan) {
                    String type = artifact.getType();
                    File out = artifact.getFile();
                    if (out == null || !out.exists()
                            || !("jar".equals(type) || out.isDirectory() || out.getName().endsWith(".jar"))) {
                        continue;
                    }

                    if (out.isFile()) {
                        DependencyScanner scanner
                                = new DependencyScanner(singletonList(out), getIncludedAndExcludedTests());
                        result = result == null ? scanner.scan() : result.append(scanner.scan());
                    } else if (out.isDirectory()) {
                        DirectoryScanner scanner
                                = new DirectoryScanner(out, getIncludedAndExcludedTests());
                        result = result == null ? scanner.scan() : result.append(scanner.scan());
                    }
                }

                return result;
            } catch (Exception e) {
                throw new MojoFailureException(e.getLocalizedMessage(), e);
            }
        }
    }

    public List<String> getDependenciesToScan() {
        return dependenciesToScan;
    }
}
