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

package org.wildfly.glow.rules.test.xmlpath;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;
import org.wildfly.glow.rules.test.XMLUtil;

import java.nio.file.Path;

public class XmlPathSeveralFilesWithWildcardsTestCase extends AbstractLayerMetaDataTestCase {
    @Test
    public void testMatchKeyOnlyFile1Key1() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("xml-path-testB.xml", XMLUtil.createXmlElementWithContent(null, "root", "wildcard-match", "key-only"))
                .build();
        checkLayersForArchive(p, "xml-path-file-several-wildcards");
    }

    @Test
    public void testMatchKeyOnlyFile2Key2() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("xml-path-testb.xml", XMLUtil.createXmlElementWithContent(null, "root", "match-wildcard", "key-only"))
                .build();
        checkLayersForArchive(p, "xml-path-file-several-wildcards");
    }

    @Test
    public void testMatchKeyValueFolder1File1Key1Value1() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test1/xml-path-testB1.xml", XMLUtil.createXmlElementWithContent("test-B", "root", "key-value", "one"))
                .build();
        checkLayersForArchive(p, "xml-path-file-several-wildcards");
    }

    @Test
    public void testMatchKeyValueFolder2File2Key2Value2() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test2/xml-path-testB2.xml", XMLUtil.createXmlElementWithContent("valueB", "root", "key-value", "two"))
                .build();
        checkLayersForArchive(p, "xml-path-file-several-wildcards");
    }

    @Test
    public void testKeyOnlyNoMatchBadFile1() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("xml-path-testB1.xml", XMLUtil.createXmlElementWithContent(null, "root", "wildcard-match", "key-only"))
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testKeyOnlyNoMatchBadFile2() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("xml-path-testb1.xml", XMLUtil.createXmlElementWithContent(null, "root", "wildcard-match", "key-only"))
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testKeyOnlyNoMatchBadKey() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("xml-path-testB.xml", XMLUtil.createXmlElementWithContent(null, "root", "wildcard-match", "key-onlyX"))
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testKeyValueNoMatchBadFileDir() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("tes1/xml-path-testB1.xml", XMLUtil.createXmlElementWithContent("test-B", "root", "key-value", "one"))
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testKeyValueNoMatchBadFileName() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test1/xml-path-testX1.xml", XMLUtil.createXmlElementWithContent("test-B", "root", "key-value", "one"))
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testKeyValueNoMatchBecauseNoValue() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test1/xml-path-testB1.xml", XMLUtil.createXmlElementWithContent(null, "root", "key-value", "one"))
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testNoMatchKeyValueBecauseNoKeyMatch() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test1/xml-path-testB1.properties", XMLUtil.createXmlElementWithContent("test-B", "root", "key-valueX", "one"))
                .build();
        checkLayersForArchive(p);
    }

    @Test
    public void testNoMatchKeyValueBecauseNoValueMatch() {
        Path p = createArchiveBuilder(ArchiveType.JAR)
                .addFile("test1/xml-path-testB1.xml", XMLUtil.createXmlElementWithContent("test-BX", "root", "key-value", "one"))
                .build();
        checkLayersForArchive(p);
    }
}