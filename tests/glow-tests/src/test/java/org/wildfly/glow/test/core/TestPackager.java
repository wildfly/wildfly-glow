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

package org.wildfly.glow.test.core;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.maven.MavenResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class TestPackager {
    private Path archivePath;
    private ScanResults scanResults;

    public ScanResults packageTestAsArchiveAndScan(Object instance) throws Exception {
        return packageTestAsArchiveAndScan(instance.getClass());
    }

    public ScanResults packageTestAsArchiveAndScan(Class<?> testClass) throws Exception {
        String archiveName = testClass.getName().toLowerCase() + ".jar";
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, archiveName);
        archive.addClass(testClass);
        ZipExporter exporter = archive.as(ZipExporter.class);

        Path path = Paths.get("target/resource-archives");
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        path = path.resolve(archiveName);
        if (Files.exists(path)) {
            Files.delete(path);
        }

        exporter.exportTo(path.toFile());
        this.archivePath = path;

        Arguments arguments = Arguments.scanBuilder().setBinaries(Collections.singletonList(archivePath)).build();
        scanResults = GlowSession.scan(MavenResolver.newMavenResolver(), arguments, GlowMessageWriter.DEFAULT);
        return scanResults;
    }

    public void reset() {
        this.scanResults = null;
        this.archivePath = null;
    }

    public ScanResults getScanResults() {
        return scanResults;
    }
}
