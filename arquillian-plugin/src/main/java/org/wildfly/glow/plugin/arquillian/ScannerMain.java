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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.artifact.DependencyResolutionRequiredException;

/**
 *
 * @author jdenise
 */
public class ScannerMain {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Scanner in forked process");
        String[] cpUrls = args[0].split(",");
        String[] cls = args[1].split(",");
        List<String> classes = new ArrayList<>();
        classes.addAll(Arrays.asList(cls));
        Path outputFolder = Paths.get(args[2]);
        boolean verbose = Boolean.parseBoolean(args[3]);
        GlowArquillianDeploymentExporter exporter = new GlowArquillianDeploymentExporter(classes, buildClassLoader(cpUrls), outputFolder, verbose);
        exporter.scanAndExport();
        System.exit(0);
    }

    private static URLClassLoader buildClassLoader(String[] cpUrls) throws DependencyResolutionRequiredException, MalformedURLException, URISyntaxException {
        List<URL> urls = new ArrayList<>();
        for (String s : cpUrls) {
            urls.add(new URI(s).toURL());
        }
        URL[] cp = urls.toArray(new URL[0]);
        ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
        return new URLClassLoader(cp, originalCl);
    }
}
