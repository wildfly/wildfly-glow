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

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class GlowArquillianDeploymentExporter {

    private static final Class<Deployment> DEPLOYMENT_ANNOTATION = Deployment.class;
    public static final String ARCHIVE_LIST_FILENAME = "glow-archive-list.txt";
    public static final String TEST_PATHS = "glow-test-path-list.txt";
    public static final String TEST_CLASSPATH = "glow-test-cp-list.txt";
    private final List<String> testClasses;
    private final Path outputFolder;
    private final ClassLoader loader;
    private final List<String> fileNames = new ArrayList<>();
    private int counter = 0;
    private final boolean verbose;

    public GlowArquillianDeploymentExporter(List<String> testClasses, ClassLoader loader, Path outputFolder, boolean verbose) {
        this.testClasses = testClasses;
        this.outputFolder = outputFolder.normalize();
        this.loader = loader;
        this.verbose = verbose;
    }

    public List<String> scanAndExport() throws Exception {
        for (String className : testClasses) {
            inspectClassFile(className);
        }
        Path outputPath = outputFolder.resolve(ARCHIVE_LIST_FILENAME);
        //System.out.println("--> " + outputPath);
        Files.write(outputPath, fileNames);
        return fileNames;
    }

    private void inspectClassFile(String className) throws IOException {
        Class<?> clazz;
        try {
            if(verbose) {
                System.out.println("Inspect " + className);
            }
            clazz = Class.forName(className, false, loader);
        } catch (Throwable e) {
            if (verbose) {
                System.err.println("Exception scanning test class " + className + ": " + e);
                // In some classes static initialisers expect some system properties to be set
                // Just ignore this, in the hope that this does not affect any of the test classes (for now anyway)
                e.printStackTrace();
            }
            return;
        }

        List<Method> deploymentMethods = findDeploymentAnnotatedMethod(clazz);
        if (deploymentMethods.isEmpty()) {
            return;
        }
        //System.out.println(deploymentMethod);
        for(Method m : deploymentMethods) {
            invokeDeploymentMethodAndExportArchive(m);
        }
    }

    private List<Method> findDeploymentAnnotatedMethod(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        try {
            for (Method m : clazz.getDeclaredMethods()) {
                m.setAccessible(true);
                if (!Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                if (m.getParameterCount() > 0) {
                    continue;
                }
                if (m.getAnnotation(DEPLOYMENT_ANNOTATION) != null) {
                    methods.add(m);
                }
            }
            Class superClass = clazz.getSuperclass();
            if (superClass != null && superClass != Object.class) {
                methods.addAll(findDeploymentAnnotatedMethod(superClass));
            }
        } catch (Throwable ex) {
            if (verbose) {
                System.err.println("Exception scanning test class " + clazz + " methods: " + ex);
                ex.printStackTrace();
            }
        }
        return methods;
    }

    private void invokeDeploymentMethodAndExportArchive(Method m) {
        Archive<?> archive;
        try {
            ClassLoader current = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try {
                archive = (Archive<?>) m.invoke(null);
            } finally {
                Thread.currentThread().setContextClassLoader(current);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            if (verbose) {
                System.err.println("Exception invoking deployment method " + m + ": " + e);
                e.printStackTrace();
            }
            return;
        }

        if (archive.getContent().size() == 0) {
            //Some tests use a dummy archive with no content. We can't export those
            return;
        }

        // TODO Is the extension enough, or do we need to check the type?
        String archiveName = archive.getName();
        int index = archiveName.lastIndexOf('.');
        String archiveSuffix = archiveName.substring(index);

        String outputName = m.getDeclaringClass().getSimpleName() + (++counter);
        outputName += archiveSuffix;
        Path outputPath = outputFolder.resolve(outputName).toAbsolutePath();
        if (verbose) {
            System.out.println("---->" + outputName);
        }
        ZipExporter exporter = archive.as(ZipExporter.class);
        exporter.exportTo(outputPath.toFile(), true);
        this.fileNames.add(outputPath.toString());
    }
}
