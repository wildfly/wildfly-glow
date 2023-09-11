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

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author jdenise
 */
public class ScannerMain {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Scanner in forked process");
        Path cpFile = Paths.get(args[0]);
        String cp = Files.readString(cpFile);
        String[] cpArray = cp.split(",");
        Path urlsFile = Paths.get(args[1] );
        String urls = Files.readString(urlsFile);
        String[] urlsArray = urls.split(",");

        String[] cls = args[2].split(",");
        List<String> classes = new ArrayList<>();

        classes.addAll(Arrays.asList(cls));
        Path outputFolder = Paths.get(args[3]);

        boolean verbose = Boolean.parseBoolean(args[4]);
        // ClassLoader to load the Scanner from the classpath (equivalent to application cp).
        // Delegates to the application classpath to resolve Java API.
        URLClassLoader cpLoader = buildClassLoader(cpArray, Thread.currentThread().getContextClassLoader(), verbose);
        // ClassLoader to load the test classes, delegate to cpLoader
        URLClassLoader testLoader = buildClassLoader(urlsArray, cpLoader, verbose);
        Class<?> exporterClass = Class.forName("org.wildfly.glow.plugin.arquillian.GlowArquillianDeploymentExporter", true, cpLoader);
        Constructor ctr = exporterClass.getConstructor(List.class, ClassLoader.class, Path.class, Boolean.TYPE);
        Object obj = ctr.newInstance(classes, testLoader, outputFolder, verbose);
        Method scan = exporterClass.getMethod("scanAndExport");
        scan.invoke(obj);
        System.exit(0);
    }

    private static URLClassLoader buildClassLoader(String[] cpUrls, ClassLoader parent, boolean verbose) throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String s : cpUrls) {
            if (verbose) {
                System.out.println("URL " + s);
            }
            urls.add(new File(s).toURI().toURL());
        }
        URL[] cp = urls.toArray(new URL[0]);
        return new URLClassLoader(cp, parent);
    }
}
