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

package org.wildfly.glow.rules.test.properties.file.match;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

import java.nio.file.Path;

public class PropertiesFileMatchSeveralFilesWithWildcardsTestCase extends AbstractLayerMetaDataTestCase {
    @Test
    public void testMatchKeyOnlyFile1Key1() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testB.properties", "testB-key-only")
                .build();
        checkLayersForArchive(p, "properties-file-match-file-several-wildcards");
    }

    @Test
    public void testMatchKeyOnlyFile2Key2() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testb.properties", "test-key-only")
                .build();
        checkLayersForArchive(p, "properties-file-match-file-several-wildcards");
    }

    @Test
    public void testMatchKeyValueFolder1File1Key1Value1() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test1/property-file-match-testB1.properties", "testB-key1=value1B")
                .build();
        checkLayersForArchive(p, "properties-file-match-file-several-wildcards");
    }

    @Test
    public void testMatchKeyValueFolder2File2Key2Value2() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test2/property-file-match-testB2.properties", "testB-key2=value2B")
                .build();
        checkLayersForArchive(p, "properties-file-match-file-several-wildcards");
    }

    @Test
    public void testKeyOnlyNoMatchBadFile1() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testB1.properties", "testB-key-only")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testKeyOnlyNoMatchBadFile2() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testb1.properties", "testB-key-only")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testKeyOnlyNoMatchBadKey() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testB.properties", "testB-Xkey-only")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testKeyValueNoMatchBadFileDir() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("tes1/property-file-match-testB.properties", "testB-key1=value1B")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testKeyValueNoMatchBadFileName() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test1/property-file-match-testX1.properties", "testB-key1=value1B")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testKeyValueNoMatchBecauseNoValue() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test1/property-file-match-testB1.properties", "testB-key1")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testNoMatchKeyValueBecauseNoKeyMatch() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test1/property-file-match-testB1.properties", "testBX-key1=value1B")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testNoMatchKeyValueBecauseNoValueMatch() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test1/property-file-match-testB1.properties", "testB-key1=value1BX")
                .build();
        checkLayersForArchive(p);
    }
}
