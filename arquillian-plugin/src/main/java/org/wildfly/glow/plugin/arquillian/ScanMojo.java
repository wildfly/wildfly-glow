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
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.HiddenPropertiesAccessor;
import org.wildfly.glow.Layer;
import org.wildfly.glow.OutputFormat;
import org.wildfly.glow.ScanResults;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugin.logging.Log;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.GalleonFeaturePack;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayers;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayersBuilder;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.Channel;
import org.wildfly.glow.ScanArguments;
import org.wildfly.glow.error.IdentifiedError;
import static org.wildfly.glow.plugin.arquillian.GlowArquillianDeploymentExporter.TEST_CLASSPATH;
import static org.wildfly.glow.plugin.arquillian.GlowArquillianDeploymentExporter.TEST_PATHS;
import static org.wildfly.glow.Arguments.PREVIEW;

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
     * List of feature-packs that are scanned and injected in the generated
     * provisioning.xml.
     * This option can't be used when  {@code server-version} or {@code preview} or {@code context} are used.
     */
    @Parameter(required = false, alias = "feature-packs")
    List<GalleonFeaturePack> featurePacks = Collections.emptyList();

    /**
     * GroupId:ArtifactId of dependencies that contain test classes to scan for
     * Arquillian deployments.
     */
    @Parameter(alias = "dependencies-to-scan", property = "org.wildfly.glow.dependencies-to-scan")
    private List<String> dependenciesToScan = Collections.emptyList();

    /**
     * Execution profile.
     */
    @Parameter(alias = "profile", required = false, property = "org.wildfly.glow.profile")
    private String profile;

    /**
     * Do not lookup deployments to scan.
     */
    @Parameter(alias = "skip-scanning", property = "org.wildfly.glow.skip-scanning")
    boolean skipScanning;

    @Parameter(alias = "add-layers-for-jndi", property = "org.wildfly.glow.layers-for-jndi")
    Set<String> layersForJndi = Collections.emptySet();

    /**
     * All executions sharing the same aggregate have their provisioning.xml
     * merged inside a single one. Make sure to have config-name parameter fo
     * each execution sharing the same aggregates.
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
     * A string identical to what this plugin output. It is used to compare what
     * the plugin has discovered vs what is expected.
     */
    @Parameter(alias = "expected-discovery", property = "org.wildfly.glow.expected-discovery")
    String expectedDiscovery;

    /**
     * System properties to set. For example, you can set
     * org.wildfly.glow.manual.layers system property to a list of layers to add
     * to the discovered set.
     */
    @Parameter
    private Map<String, String> systemPropertyVariables = Collections.emptyMap();

    /**
     * Expects error to be found by glow (eg: missing datasource, ...) that will
     * get fixed by the test. If an error is found and this parameter contains
     * expected errors, the errors are ignored. If no error is found and this
     * parameter contains errors, an exception is thrown. If this parameter is
     * null (the default) and an error is found, an exception is thrown.
     */
    @Parameter(alias = "expected-errors", property = "org.wildfly.glow.expected-errors")
    private List<String> expectedErrors = Collections.emptyList();

    /**
     * Enable verbose output (containing identified errors, suggestions, ...).
     */
    @Parameter(property = "org.wildfly.glow.verbose")
    private boolean verbose = false;

    /**
     * A list of channels used for resolving artifacts while provisioning.
     * <p>
     * Defining a channel:
     *
     * <pre>
     * <channels>
     *     <channel>
     *         <manifest>
     *             <groupId>org.wildfly.channels</groupId>
     *             <artifactId>wildfly-30.0</artifactId>
     *         </manifest>
     *     </channel>
     *     <channel>
     *         <manifest>
     *             <url>https://example.example.org/channel/30</url>
     *         </manifest>
     *     </channel>
     * </channels>
     * </pre>
     * </p>
     * <p>
     * The {@code wildfly.channels} property can be used pass a comma delimited string for the channels. The channel
     * can be a URL or a Maven GAV. If a Maven GAV is used, the groupId and artifactId are required.
     * <br>
     * Examples:
     *
     * <pre>
     *     -Dorg.wildfly.glow.channels=&quot;https://channels.example.org/30&quot;
     *     -Dorg.wildfly.glow.channels=&quot;https://channels.example.org/30,org.example.channel:updates-30&quot;
     *     -Dorg.wildfly.glow.channels=&quot;https://channels.example.org/30,org.example.channel:updates-30:1.0.2&quot;
     * </pre>
     * </p>
     */
    @Parameter(alias = "channels", property = "org.wildfly.glow.channels")
    List<ChannelConfiguration> channels;

    /**
     * A WildFly server version. The latest version is the default, only usable if no {@code feature-packs} have been set.
     */
    @Parameter(alias = "server-version", property = "org.wildfly.glow.server-version")
    private String serverVersion;

    /**
     * Use WildFly Preview server, only usable if no {@code feature-packs} have been set.
     */
    @Deprecated
    @Parameter(alias = "preview-server", property = "org.wildfly.glow.preview-server")
    private boolean previewServer;

    /**
     * Use a variant of the WildFly server, only usable if no {@code feature-packs} have been set.
     */
    @Parameter(alias = "wildfly-variant", property = "org.wildfly.glow.wildfly-variant")
    private String wildflyVariant;

    /**
     * Execution context, can be {@code cloud} or {@code bare-metal}, default value is {@code bare-metal},
     * only usable if no {@code feature-packs} have been set.
     */
    @Parameter(alias = "context", property = "org.wildfly.glow.context")
    private String context;

    /**
     * By default the execution is aborted when unknown errors are reported. You can disable the check done for errors by
     * setting this option to true.
     */
    @Parameter(alias = "check-errors", property = "org.wildfly.glow.check-errors")
    private boolean checkErrors = true;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        // Check configuration
        if (!featurePacks.isEmpty()) {
            if (serverVersion != null) {
                throw new MojoExecutionException("server-version can't be set when feature-packs have been set.");
            }
            if (context != null) {
                throw new MojoExecutionException("context can't be set when feature-packs have been set.");
            }
            if (previewServer) {
                throw new MojoExecutionException("preview-server can't be set when feature-packs have been set.");
            }
            if (wildflyVariant != null) {
                throw new MojoExecutionException("wildfly-variant can't be set when feature-packs have been set.");
            }
        }

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
            List<String> paths = new ArrayList<>();
            for (String s : project.getTestClasspathElements()) {
                paths.add(new File(s).getAbsolutePath());
            }
            MavenRepoManager artifactResolver;
             ConfiguredChannels cr = null;
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
            Set<String> profiles = new HashSet<>();
            if (profile != null) {
                profiles.add(profile);
            }
            if (previewServer) {
                if (wildflyVariant != null) {
                    throw new MojoExecutionException("wildfly-variant can't be set when preview-server has been set.");
                }
                getLog().warn("preview-server option has been deprecated, use the wildfly-variant option");
                wildflyVariant = PREVIEW;
            }
            ScanArguments.Builder argumentsBuilder = Arguments.scanBuilder().
                    setExecutionProfiles(profiles).
                    setBinaries(retrieveDeployments(paths, classesRootFolder, outputFolder)).
                    setUserEnabledAddOns(addOns).
                    setConfigName(configName).
                    setSuggest((verbose || getLog().isDebugEnabled())).
                    setJndiLayers(layersForJndi).
                    setVerbose(verbose || getLog().isDebugEnabled()).
                    setOutput(OutputFormat.PROVISIONING_XML).
                    setVariant(wildflyVariant).
                    setExecutionContext(context).setVersion(serverVersion);

            if (!featurePacks.isEmpty()) {
                argumentsBuilder.setProvisoningXML(buildInputConfig(outputFolder, artifactResolver));
            }

            Arguments arguments = argumentsBuilder.build();
            try (ScanResults results = GlowSession.scan(artifactResolver,
                    arguments, writer)) {
                boolean skipTests = Boolean.getBoolean("maven.test.skip") || Boolean.getBoolean("skipTests");
                if (skipTests) {
                    getLog().warn("Tests are disabled, not checking for expected discovered layers.");
                } else {
                    if (expectedDiscovery != null) {
                        String compact = results.getCompactInformation();
                        if (!expectedDiscovery.equals(compact)) {
                            throw new MojoExecutionException("Error in glow discovery.\n"
                                    + "-Expected: " + expectedDiscovery + "\n"
                                    + "-Found   : " + compact);
                        }
                    }
                    if (results.getErrorSession().hasErrors()) {
                        boolean errorsFound = false;
                        if (expectedErrors.isEmpty()) {
                            results.outputInformation(writer);
                            errorsFound = true;
                            String msg = "An error has been reported and expected-errors has not been set.";
                            if (checkErrors) {
                                throw new MojoExecutionException(msg);
                            } else {
                                getLog().warn(msg);
                                return;
                            }
                        }
                        List<IdentifiedError> errors = new ArrayList<>();
                        for (IdentifiedError err : results.getErrorSession().getErrors()) {
                            if (!err.isFixed()) {
                                errors.add(err);
                            }
                        }
                        if (expectedErrors.size() != errors.size()) {
                            List<String> descriptions = new ArrayList<>();
                            for (IdentifiedError err : errors) {
                                descriptions.add(err.getDescription());
                            }
                            String msg = "Number of expected errors mismatch. Expected "
                                    + expectedErrors.size() + " reported " + errors.size() + ".\n"
                                    + "Reported Errors " + descriptions + "\n"
                                    + "Expected Errors " + expectedErrors;
                            errorsFound = true;
                            if (checkErrors) {
                                throw new MojoExecutionException(msg);
                            } else {
                                getLog().warn(msg);
                            }
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
                            errorsFound = true;
                            String msg = "The following errors are unexpected:\n" + builder.toString();
                            if (checkErrors) {
                                throw new MojoExecutionException(msg);
                            } else {
                                getLog().warn(msg);
                            }
                        }
                        if(!errorsFound) {
                            getLog().info("Expected errors found in glow scanning results. "
                                + " The test execution should fix them (eg: add missing datasources)");
                        }
                    } else {
                        if (!expectedErrors.isEmpty()) {
                            throw new MojoExecutionException("expected-errors contains errors but no error reported.");
                        }
                    }
                }
                if (verbose || getLog().isDebugEnabled()) {
                    results.outputInformation(writer);
                } else {
                    results.outputCompactInformation(writer);
                }

                Path provisioningFile = outputFolder.resolve("provisioning.xml");
                if (aggregate != null && Files.exists(provisioningFile)) {
                    try (Provisioning provisioning = new GalleonBuilder().addArtifactResolver(artifactResolver).newProvisioningBuilder(provisioningFile).build()) {
                        GalleonProvisioningConfig parsed = provisioning.loadProvisioningConfig(provisioningFile);
                        GalleonProvisioningConfig.Builder builder = GalleonProvisioningConfig.builder(parsed);
                        GalleonConfigurationWithLayersBuilder config = GalleonConfigurationWithLayersBuilder.builder("standalone", configName);
                        config.includeLayer(results.getBaseLayer().getName());
                        for (Layer l : results.getDecorators()) {
                            config.includeLayer(l.getName());
                        }
                        for (Layer l : results.getExcludedLayers()) {
                            config.excludeLayer(l.getName());
                        }
                        builder.addConfig(config.build());
                        for (GalleonConfigurationWithLayers c : parsed.getDefinedConfigs()) {
                            builder.addConfig(c);
                        }
                        provisioning.storeProvisioningConfig(builder.build(), provisioningFile);
                    }
                } else {
                    results.outputConfig(outputFolder, null);
                }
            }
        } catch (Exception ex) {
            if (ex instanceof MojoExecutionException) {
                throw (MojoExecutionException) ex;
            }
            throw new MojoExecutionException(ex.getMessage(), ex);
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
            List<String> testArtifacts) throws IOException, MojoExecutionException {
        StringBuilder classesLst = new StringBuilder();
        for (String c : classes) {
            classesLst.append(c).append(",");
        }
        StringBuilder pathList = new StringBuilder();
        for (String path : testArtifacts) {
            pathList.append(path).append(",");
        }
        Path lst = outputFolder.resolve(TEST_PATHS);
        Files.write(lst, pathList.toString().getBytes());
        if (verbose) {
            getLog().info("SCANNER: Test elements: " + pathList);
            getLog().info("SCANNER: Classes: " + classesLst);
        }
        final StringBuilder cp = new StringBuilder();
        final StringBuilder reducedCp = new StringBuilder();
        collectCpPaths(System.getProperty("java.home"),
                Thread.currentThread().getContextClassLoader(),
                cp,
                verbose,
                reducedCp,
                getLog());
        if (verbose) {
            getLog().info("SCANNER: classpath: " + cp);
            getLog().info("SCANNER: bootstrap classpath: " + reducedCp);
        }
        Path cpFile = outputFolder.resolve(TEST_CLASSPATH);
        Files.write(cpFile, cp.toString().getBytes());

        List<String> cmd = new ArrayList<>();
        cmd.add(getJavaCommand());
        cmd.add("-Dorg.wildfly.glow.scan");
        for (String sysPropName : systemPropertyVariables.keySet()) {
            cmd.add("-D" + sysPropName + "=" + systemPropertyVariables.get(sysPropName));
        }
        cmd.add("-cp");
        cmd.add(reducedCp.toString());
        cmd.add("org.wildfly.glow.plugin.arquillian.ScannerMain");
        cmd.add(cpFile.toAbsolutePath().toString());
        cmd.add(lst.toAbsolutePath().toString());
        cmd.add(classesLst.toString());
        cmd.add(outputFolder.toAbsolutePath().toString());
        cmd.add(verbose || getLog().isDebugEnabled() ? "true" : "false");
        final ProcessBuilder builder = new ProcessBuilder(cmd)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT);
        return builder.start();
    }

    private static void collectCpPaths(String javaHome, ClassLoader cl, StringBuilder buf,
            boolean enableVerboseOutput,
            StringBuilder reducedCp, Log log) throws MojoExecutionException {
        final ClassLoader parentCl = cl.getParent();
        if (parentCl != null) {
            collectCpPaths(javaHome, cl.getParent(), buf, enableVerboseOutput, reducedCp, log);
        }
        if (cl instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader) cl).getURLs()) {
                final String filePath;
                File file;
                try {
                    file = new File(url.toURI());
                    filePath = file.getAbsolutePath();
                } catch (URISyntaxException ex) {
                    throw new MojoExecutionException(ex.getMessage(), ex);
                }
                if (filePath.startsWith(javaHome)) {
                    continue;
                }
                if (file.getName().contains("wildfly-glow-arquillian-plugin-scanner")) {
                    if (reducedCp.length() > 0) {
                        reducedCp.append(File.pathSeparator);
                    }
                    reducedCp.append(filePath);
                } else {
                    if (buf.length() > 0) {
                        buf.append(",");
                    }
                    buf.append(filePath);
                }
            }
        }
    }

    private Path buildInputConfig(Path outputFolder, MavenRepoManager artifactResolver) throws ProvisioningException, IOException, XMLStreamException {
        GalleonProvisioningConfig.Builder inBuilder = GalleonProvisioningConfig.builder();
        // Build config
        for (GalleonFeaturePack fp : featurePacks) {
            GalleonFeaturePackConfig.Builder builder = GalleonFeaturePackConfig.builder(FeaturePackLocation.
                    fromString(fp.getLocation() == null ? fp.getMavenCoords() : fp.getLocation()));
            builder.includeAllPackages(fp.getIncludedPackages());
            GalleonFeaturePackConfig cfg = builder.build();
            inBuilder.addFeaturePackDep(cfg);
        }
        GalleonProvisioningConfig conf = inBuilder.build();
        GalleonBuilder provider = new GalleonBuilder();
        provider.addArtifactResolver(artifactResolver);
        try (Provisioning provisioning = provider.newProvisioningBuilder(conf).build()) {
            Path p = outputFolder.resolve("glow-in-provisioning.xml");
            provisioning.storeProvisioningConfig(conf, p);
            return p;
        }
    }

    private List<Path> retrieveDeployments(List<String> testArtifacts, Path classesRootFolder, Path outputFolder) throws Exception {
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
