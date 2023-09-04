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

package org.wildfly.glow.rules.test.expected.file;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

import java.nio.file.Path;

public class ExpectedFileTwoFilesWildcardsTestCase extends AbstractLayerMetaDataTestCase {
    @Test
    public void testMatchesSingleFileMetaInfSuffixA() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("two-wc.txt", "")
                .build();
        checkLayersForArchive(p, "expected-file-two-files-wildcards");
    }

    @Test
    public void testMatchesSingleFileMetaInfSuffixB() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("two-wc.xml", "")
                .build();
        checkLayersForArchive(p, "expected-file-two-files-wildcards");
    }

    @Test
    public void testMatchesSingleFileMetaInfSuffixC() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("two-wc.thing", "")
                .build();
        checkLayersForArchive(p, "expected-file-two-files-wildcards");
    }

    @Test
    public void testMatchesSeveralFilesMetaInfSuffix() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("two-wc.txt", "")
                .addFile("two-wc.xml", "")
                .addFile("two-wc.thing", "")
                // Throw in a 'bad' one
                .addFile("two-wcX.thing", "")
                .build();
        checkLayersForArchive(p, "expected-file-two-files-wildcards");
    }

    @Test
    public void testNoMatchSingleFileMetaInf() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("two-wcX.txt", "")
                .build();
        checkLayersForArchive(p);
    }


    @Test
    public void testMatchesSingleFileMetaInfSubDirA() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("two-wc/hello", "")
                .build();
        checkLayersForArchive(p, "expected-file-two-files-wildcards");
    }

    @Test
    public void testMatchesSingleFileMetaInfSubDirB() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("two-wc/test.txt", "")
                .build();
        checkLayersForArchive(p, "expected-file-two-files-wildcards");
    }

    @Test
    public void testNoMatchMetaInfSubDir() {
        Path p = createArchiveBuilder(ArchiveType.WAR)
                .addFile("two-no-wcX/hello", "")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testMatchesSeveralFilesMetaInfSubDir() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("two-no-wc.txt", "")
                .addFile("two-no-wc.xml", "")
                .build();
        checkLayersForArchive(p, "expected-file-two-files-no-wildcards");
    }
}
