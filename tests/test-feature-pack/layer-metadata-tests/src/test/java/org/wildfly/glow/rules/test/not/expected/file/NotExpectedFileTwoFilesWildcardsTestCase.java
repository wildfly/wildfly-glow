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

public class NotExpectedFileTwoFilesWildcardsTestCase extends AbstractLayerMetaDataTestCase {
    public NotExpectedFileTwoFilesWildcardsTestCase() {
        super("not-expected-file-two-files-wildcards");
    }

    @Test
    public void testMatchesSingleFileA() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("ne-two-wildstar.txt", "")
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testMatchesSingleFileB() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("ne-two-wildtime/match.txt", "")
                .build();
        checkLayersForArchive(p);
    }

    //not-expected-file-two-files-wildcards
    //<prop name="org.wildfly.rule.not-expected-file" value="[/META-INF/ne-two-wild*.txt,/META-INF/ne-two-wild*/*.txt]"/>

    @Test
    public void testNoMatch() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("ne-two-wilX.txt", "")
                .addFile("ne-two-wildstar.xml", "")
                .addFile("ne-two-wildtime/bad-extension.xml", "")
                .addFile("ne-two-wilX/match.txt", "")
                .build();
        checkLayersForArchive(p, "not-expected-file-two-files-wildcards");
    }
}
