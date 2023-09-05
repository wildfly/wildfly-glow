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

public class PropertiesFileMatchSingleFileNoWildcardsTestCase extends AbstractLayerMetaDataTestCase {

    @Test
    public void testMatchKeyOnly() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testA.properties", "testA-key-only")
                .build();
        checkLayersForArchive(p, "properties-file-match-file-single-no-wildcards");
    }


    @Test
    public void testMatchKeyValue() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testA.properties", "testA-key=valueA")
                .build();
        checkLayersForArchive(p, "properties-file-match-file-single-no-wildcards");
    }


    @Test
    public void testMatchKeyOnlyAndKeyValue() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testA.properties", "testA-key-only")
                .addFile("property-file-match-testA.properties", "testA-key=valueA")
                .build();
        checkLayersForArchive(p, "properties-file-match-file-single-no-wildcards");

    }

    @Test
    public void testNoMatchBadFileA() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testXYZ.properties", "testA-key-only")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testNoMatchBadFileB() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testXYZ.properties", "testA-key=valueA")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testNoMatchKeyValueBecauseNoValue() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testA.properties", "testA-key")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testNoMatchKeyOnlyBecauseBadKey() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testA.properties", "testA-key-only-XXXX")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testNoMatchKeyValueBecauseNoKeyMatch() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testA.properties", "testA-key-only-XXXX")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testNoMatchKeyValueBecauseNoValueMatch() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("property-file-match-testA.properties", "testA-key=valueX")
                .build();
        checkLayersForArchive(p);
    }
}
