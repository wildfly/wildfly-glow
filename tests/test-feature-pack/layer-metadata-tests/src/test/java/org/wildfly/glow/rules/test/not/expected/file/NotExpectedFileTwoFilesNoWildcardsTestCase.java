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

package org.wildfly.glow.rules.test.not.expected.file;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

import java.nio.file.Path;

public class NotExpectedFileTwoFilesNoWildcardsTestCase extends AbstractLayerMetaDataTestCase {

    public NotExpectedFileTwoFilesNoWildcardsTestCase() {
        super("not-expected-file-two-files-no-wildcards");
    }

    @Test
    public void testMatchesSingleFileA() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("ne-two-no-wildcardA.txt", "")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testMatchesSingleFileB() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("ne-two-no-wildcardB.txt", "")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testNoMatch() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("ne-two-no-wildcardX.txt", "")
                .build();
        checkLayersForArchive(p, "not-expected-file-two-files-no-wildcards");
    }
}
