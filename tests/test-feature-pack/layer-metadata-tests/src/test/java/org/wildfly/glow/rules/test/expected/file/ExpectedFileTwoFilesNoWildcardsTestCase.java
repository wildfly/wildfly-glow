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

public class ExpectedFileTwoFilesNoWildcardsTestCase extends AbstractLayerMetaDataTestCase {
    @Test
    public void testMatchesSingleFileMetaInf() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("two-no-wc.txt", "")
                .build();
        checkLayersForArchive(p, "expected-file-two-files-no-wildcards");
    }

    @Test
    public void testNoMatchSingleFileMetaInf() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("two-no-wcX.txt", "")
                .build();
        checkLayersForArchive(p);
    }


    @Test
    public void testMatchesSingleFileWebInf() {
        Path p = createArchiveBuilder(ArchiveType.WAR)
                .addFile("two-no-wc/two-no-wc.txt", "")
                .build();
        checkLayersForArchive(p, "expected-file-two-files-no-wildcards");
    }

    @Test
    public void testNoMatchSingleFileWebInf() {
        Path p = createArchiveBuilder(ArchiveType.WAR)
                .addFile("two-no-wc/two-no-wc", "")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testMatchesSeveralFiles() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("two-no-wc.txt", "")
                .addFile("two-no-wc.xml", "")
                .build();
        checkLayersForArchive(p, "expected-file-two-files-no-wildcards");
    }
}
