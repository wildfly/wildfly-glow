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
package org.wildfly.glow.plugin.arquillian;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
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
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.jboss.galleon.xml.ProvisioningXmlWriter;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.HiddenPropertiesAccessor;
import org.wildfly.glow.Layer;
import org.wildfly.glow.OutputFormat;
import org.wildfly.glow.ScanResults;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.wildfly.glow.error.IdentifiedError;

/**
 *
 * @author jdenise
 */
@Mojo(name = "scan", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.TEST)
public class ScanMojo extends AbstractMojo {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

    private class MavenMessageWriter implements GlowMessageWriter {

        @Override
        public void info(Object s) {
            getLog().info(s.toString());
        }

        @Override
        public void warn(Object s) {
            getLog().warn(s.toString());
        }

        @Override
        public void error(Object s) {
            getLog().error(s.toString());
        }

        @Override
        public void trace(Object s) {
            getLog().debug(s.toString());
        }

    }
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

    /**
     * Execution add-ons.
     */
    @Parameter(alias = "add-ons", required = false, property = "org.wildfly.glow.add-ons")
    Set<String> addOns = Collections.emptySet();

    /**
     * Server configuration name in the generated provisioning.xml.
     */
    @Parameter(alias = "config-name", required = false, property = "org.wildfly.glow.config-name", defaultValue = "standalone.xml")
    String configName;

    /**
     * List of feature-packs that are scanned and injected in the generated provisioning.xml.
     */
    @Parameter(required = false, alias = "feature-packs")
    List<FeaturePack> featurePacks = Collections.emptyList();

    /**
     * Enable verbose output (containing identified errors, suggestions, ...).
     */
    @Parameter(alias = "enable-verbose-output", property = "org.wildfly.glow.enable-verbose-output")
    boolean enableVerboseOutput = false;

    /**
     * GroupId:ArtifactId of dependencies that contain test classes to scan for Arquillian deployments.
     */
    @Parameter(alias = "dependencies-to-scan", property = "org.wildfly.glow.dependencies-to-scan")
    private List<String> dependenciesToScan = Collections.emptyList();

    /**
     * Execution profiles.
     */
    @Parameter(alias = "profiles", required = false, property = "org.wildfly.glow.profiles")
    Set<String> profiles = Collections.emptySet();

    /**
     * Do not lookup deployments to scan.
     */
    @Parameter(alias = "skip-scanning", property = "org.wildfly.glow.skip-scanning")
    boolean skipScanning;

    @Parameter(alias = "add-layers-for-jndi", property = "org.wildfly.glow.layers-for-jndi")
    Set<String> layersForJndi = Collections.emptySet();

    /**
     * All executions sharing the same aggregate have their provisioning.xml merged inside a
     * single one. Make sure to have config-name parameter fo each execution sharing the same aggregates.
     */
    @Parameter(alias = "aggregate", property = "org.wildfly.glow.aggregate")
    String aggregate;

    /**
     * If not specified, it will search all test classes for @Deployment
     * annotated methods. If a surefire execution name is specified, it will
     * only look at test classes picked out by its import/export filters. If
     * set, it will also output the provisioning.xml file in a child folder of
     * that name.
     */
    @Parameter(alias = "surefire-execution-for-included-classes")
    String surefireExecutionForIncludedClasses;

    /**
     * A string identical to what this plugin output. It is used to compare what the plugin has discovered vs what is expected.
     */
    @Parameter(alias = "expected-discovery", property = "org.wildfly.glow.expected-discovery")
    String expectedDiscovery;

    /**
     * System properties to set. For example, you can set org.wildfly.glow.manual.layers system property to a list of
     * layers to add to the discovered set.
     */
    @Parameter
    private Map<String, String> systemPropertyVariables = Collections.emptyMap();

    /**
     * Expects error to be found by glow (eg: missing datasource, ...) that will get fixed by the test.
     * If an error is found and this parameter contains expected errors, the errors are ignored. If no error is found
     * and this parameter contains errors, an exception is thrown.
     * If this parameter is null (the default) and an error is found, an exception is thrown.
     */
    @Parameter(alias = "expected-errors", property = "org.wildfly.glow.expected-errors")
    private List<String> expectedErrors = Collections.emptyList();

    @Parameter(property = "org.wildfly.glow.verbose")
    private boolean verbose = false;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Make sure that the 'hidden' properties used by the Arguments class come from the Maven configuration
        HiddenPropertiesAccessor.setOverrides(systemPropertyVariables);
        try {
            MavenMessageWriter writer = new MavenMessageWriter();
            //Typically target/test-classes
            Path classesRootFolder = Paths.get(project.getBuild().getTestOutputDirectory());
            if (!Files.exists(classesRootFolder)) {
                throw new IllegalStateException(classesRootFolder + " does not exist");
            }

            //Typically under target
            Path outputFolder = Paths.get(project.getBuild().getDirectory()).resolve("glow-scan");
            if (aggregate != null) {
                outputFolder = outputFolder.resolve(aggregate);
            } else {
                if (surefireExecutionForIncludedClasses != null) {
                    //If we selected a surefire execution as the input, we need to output the jars into a sub-folder
                    outputFolder = outputFolder.resolve(surefireExecutionForIncludedClasses);
                }
            }
            if (!Files.exists(outputFolder)) {
                Files.createDirectories(outputFolder);
            }
            List<URL> urls = new ArrayList<>();
            for (String s : project.getTestClasspathElements()) {
                urls.add(new File(s).toURI().toURL());
            }
            Arguments arguments = Arguments.scanBuilder().
                    setExecutionProfiles(profiles).
                    setBinaries(retrieveDeployments(urls, classesRootFolder, outputFolder)).
                    setProvisoningXML(buildInputConfig(outputFolder)).
                    setUserEnabledAddOns(addOns).
                    setConfigName(configName).
                    setSuggest((enableVerboseOutput || getLog().isDebugEnabled())).
                    setJndiLayers(layersForJndi).
                    setVerbose(verbose || getLog().isDebugEnabled()).
                    setOutput(OutputFormat.PROVISIONING_XML).build();
            MavenArtifactRepositoryManager artifactResolver
                    = new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories);
            ScanResults results = GlowSession.scan(artifactResolver,
                    arguments, writer);
            if (expectedDiscovery != null) {
                String compact = results.getCompactInformation();
                if (!expectedDiscovery.equals(compact)) {
                    throw new MojoExecutionException("Error in glow discovery.\n"
                            + "-Expected: " + expectedDiscovery + "\n"
                            + "-Found   : " + compact);
                }
            }
            if (results.getErrorSession().hasErrors()) {
                if (expectedErrors.isEmpty()) {
                    results.outputInformation(writer);
                    throw new MojoExecutionException("An error has been reported and expected-errors has not been set.");
                }
                List<IdentifiedError> errors = new ArrayList<>();
                for (IdentifiedError err : results.getErrorSession().getErrors()) {
                    if(!err.isFixed()) {
                        errors.add(err);
                    }
                }
                if (expectedErrors.size() != errors.size()) {
                    List<String> descriptions = new ArrayList<>();
                    for(IdentifiedError err : errors) {
                        descriptions.add(err.getDescription());
                    }
                    throw new MojoExecutionException("Number of expected errors mismatch. Expected "
                            + expectedErrors.size() + " reported " + errors.size() + ".\n" +
                                    "Reported Errors " + descriptions + "\n" +
                                    "Expected Errors " + expectedErrors);
                }
                Iterator<IdentifiedError> it = errors.iterator();
                while (it.hasNext()) {
                    IdentifiedError err = it.next();
                    if (expectedErrors.contains(err.getDescription())) {
                        it.remove();
                    }
                }
                it = errors.iterator();
                if (it.hasNext()) {
                    StringBuilder builder = new StringBuilder();
                    while (it.hasNext()) {
                        IdentifiedError err = it.next();
                        builder.append(err.getDescription()).append("\n");
                    }
                    throw new MojoExecutionException("The following errors are unexpected:\n" + builder.toString());
                }
                System.out.println("Expected errors found in glow scanning results. "
                        + " The test execiution should fix them (eg: add missing datasources)");
            } else {
                if (!expectedErrors.isEmpty()) {
                    throw new MojoExecutionException("expected-errors contains errors but no error reported.");
                }
            }
            if (enableVerboseOutput || getLog().isDebugEnabled()) {
                results.outputInformation(writer);
            } else {
                results.outputCompactInformation(writer);
            }

            Path provisioningFile = outputFolder.resolve("provisioning.xml");
            if (aggregate != null && Files.exists(provisioningFile)) {
                ProvisioningConfig pConfig = ProvisioningXmlParser.parse(provisioningFile);
                ProvisioningConfig.Builder builder = ProvisioningConfig.builder(pConfig);
                ConfigModel.Builder modelBuilder = ConfigModel.builder("standalone", configName);
                modelBuilder.includeLayer(results.getBaseLayer().getName());
                for (Layer l : results.getDecorators()) {
                    modelBuilder.includeLayer(l.getName());
                }
                for (Layer l : results.getExcludedLayers()) {
                    modelBuilder.excludeLayer(l.getName());
                }
                builder.addConfig(modelBuilder.build());
                try (FileWriter fileWriter = new FileWriter(provisioningFile.toFile())) {
                    ProvisioningXmlWriter.getInstance().write(builder.build(), fileWriter);
                }
            } else {
                results.outputConfig(outputFolder, false);
            }
        } catch (Exception ex) {
            throw new MojoExecutionException(ex);
        } finally {
            HiddenPropertiesAccessor.clearOverrides();
        }
    }

    private String getJavaCommand() {
        final Path javaHome = Paths.get(System.getProperty("java.home"));
        final Path java;
        if (IS_WINDOWS) {
            java = javaHome.resolve("bin").resolve("java.exe");
        } else {
            java = javaHome.resolve("bin").resolve("java");
        }
        if (Files.exists(java)) {
            return java.toString();
        }
        return "java";
    }

    private Process startScanner(Path outputFolder,
            List<String> classes,
            List<URL> testArtifacts) throws IOException, MojoExecutionException {
        StringBuilder classesLst = new StringBuilder();
        for (String c : classes) {
            classesLst.append(c).append(",");
        }
        StringBuilder urlList = new StringBuilder();
        for (URL url : testArtifacts) {
            urlList.append(url).append(",");
        }
        //System.out.println("URLS " + urlList);
        //System.out.println("CLASSES " + classesLst);
        final StringBuilder cp = new StringBuilder();
        collectCpUrls(System.getProperty("java.home"), Thread.currentThread().getContextClassLoader(), cp);
        //System.out.println("CP " + cp);


        List<String> cmd = new ArrayList<>();
        cmd.add(getJavaCommand());
        cmd.add("-Dorg.wildfly.glow.scan");
        for (String sysPropName : systemPropertyVariables.keySet()) {
            cmd.add("-D" + sysPropName + "=" + systemPropertyVariables.get(sysPropName));
        }
        cmd.add("-cp");
        cmd.add(cp.toString());
        cmd.add("org.wildfly.glow.plugin.arquillian.ScannerMain");
        cmd.add(urlList.toString());
        cmd.add(classesLst.toString());
        cmd.add(outputFolder.toAbsolutePath().toString());
        cmd.add(enableVerboseOutput || getLog().isDebugEnabled() ? "true" : "false");
        final ProcessBuilder builder = new ProcessBuilder(cmd)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT);
        return builder.start();
    }


    private static void collectCpUrls(String javaHome, ClassLoader cl, StringBuilder buf) throws MojoExecutionException {
        final ClassLoader parentCl = cl.getParent();
        if (parentCl != null) {
            collectCpUrls(javaHome, cl.getParent(), buf);
        }
        if (cl instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader) cl).getURLs()) {
                final String file;
                try {
                    file = new File(url.toURI()).getAbsolutePath();
                } catch (URISyntaxException ex) {
                    throw new MojoExecutionException(ex);
                }
                if (file.startsWith(javaHome)) {
                    continue;
                }
                if (buf.length() > 0) {
                    buf.append(File.pathSeparatorChar);
                }
                buf.append(file);
            }
        }
    }

    private Path buildInputConfig(Path outputFolder) throws ProvisioningDescriptionException, IOException, XMLStreamException {
        ProvisioningConfig.Builder inBuilder = ProvisioningConfig.builder();
        // Build config
        for (FeaturePack fp : featurePacks) {
            FeaturePackConfig.Builder builder = FeaturePackConfig.builder(FeaturePackLocation.
                    fromString(fp.getMavenCoords()));
            builder.includeAllPackages(fp.getIncludedPackages());
            FeaturePackConfig cfg = builder.build();
            inBuilder.addFeaturePackDep(cfg);
        }
        ProvisioningConfig in = inBuilder.build();
        Path p = outputFolder.resolve("glow-in-provisioning.xml");
        try (FileWriter fileWriter = new FileWriter(p.toFile())) {
            ProvisioningXmlWriter.getInstance().write(in, fileWriter);
        }
        return p;
    }

    private List<Path> retrieveDeployments(List<URL> testArtifacts, Path classesRootFolder, Path outputFolder) throws Exception {
        if (skipScanning) {
            return Collections.emptyList();
        }
        CopiedFromSureFirePlugin copiedFromSureFire = new CopiedFromSureFirePlugin(classesRootFolder, dependenciesToScan,
                project.getTestArtifacts(), selectPluginExecutionForDependencies());
        List<String> actualClasses = new ArrayList<>();
        List<String> scannedClasses = copiedFromSureFire.scanForTestClasses().getClasses();
        for (String name : scannedClasses) {
            if (!name.contains("$")) {
                actualClasses.add(name);
            }
        }
        Process p = startScanner(outputFolder, actualClasses, testArtifacts);
        p.waitFor();
        List<Path> deployments = new ArrayList<>();
        List<String> lst = Files.readAllLines(outputFolder.resolve(GlowArquillianDeploymentExporter.ARCHIVE_LIST_FILENAME));
        for (String l : lst) {
            deployments.add(Paths.get(l));
        }
        return deployments;
    }

    private PluginExecution selectPluginExecutionForDependencies() {
        if (surefireExecutionForIncludedClasses == null) {
            return null;
        }
        Plugin plugin = project.getPlugin("org.apache.maven.plugins:maven-surefire-plugin");
        Map<String, PluginExecution> surefireExecutions = plugin.getExecutionsAsMap();
        PluginExecution execution = surefireExecutions.get(surefireExecutionForIncludedClasses);
        if (execution == null) {
            throw new IllegalStateException("No maven-surefire-plugin execution called: " + surefireExecutionForIncludedClasses);
        }
        return execution;

    }

}
