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

package org.wildfly.glow.test.core.deployment.inspector;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.glow.DeploymentFileRuleInspector;
import org.wildfly.glow.DeploymentFileRuleInspector.ParsedRule;
import org.wildfly.glow.DeploymentFileRuleInspector.PatternOrValue;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public class DeploymentInspectorTestCase {

    private static final String NON_MATCHING_FILE = "org/wildfly/test/NotMatchingAnythingBlahBlah.txt";

    @Test
    public void testSingleFile() throws Exception {
        final String file = "META-INF/microprofile-config.properties";
        DeploymentCreator creator =
                new DeploymentCreator("test.war")
                        .addFile(file);
        DeploymentFileRuleInspector inspector = new DeploymentFileRuleInspector(creator.rootPath, false);
        ParsedRule parsedRule = inspector.extractParsedRule(file + ",prop,123");
        List<Path> paths = parsedRule.getMatchedPaths();
        Assert.assertEquals(1, paths.size());
        Assert.assertEquals(creator.rootPath.resolve(file), paths.get(0));
        List<PatternOrValue> values = parsedRule.getValueParts();
        Assert.assertEquals(2, values.size());
        Assert.assertEquals("prop", values.get(0).getValue());
        Assert.assertNull(values.get(0).getPattern());
        Assert.assertEquals("123", values.get(1).getValue());
        Assert.assertNull(values.get(1).getPattern());
    }

    @Test
    public void testSingleFileNoProps() throws Exception {
        final String file = "META-INF/microprofile-config.properties";
        DeploymentCreator creator =
                new DeploymentCreator("test.war")
                        .addFile(file);
        DeploymentFileRuleInspector inspector = new DeploymentFileRuleInspector(creator.rootPath, false);
        ParsedRule parsedRule = inspector.extractParsedRule(file);
        List<Path> paths = parsedRule.getMatchedPaths();
        Assert.assertEquals(1, paths.size());
        Assert.assertEquals(creator.rootPath.resolve(file), paths.get(0));
        List<PatternOrValue> values = parsedRule.getValueParts();
        Assert.assertEquals(0, values.size());
    }

    @Test
    public void testSingleFileWildcardProp() throws Exception {
        final String file = "META-INF/microprofile-config.properties";
        DeploymentCreator creator =
                new DeploymentCreator("test.war")
                        .addFile(file);
        DeploymentFileRuleInspector inspector = new DeploymentFileRuleInspector(creator.rootPath, false);
        ParsedRule parsedRule = inspector.extractParsedRule(file + ",empty-*");
        List<Path> paths = parsedRule.getMatchedPaths();
        Assert.assertEquals(1, paths.size());
        Assert.assertEquals(creator.rootPath.resolve(file), paths.get(0));
        List<PatternOrValue> values = parsedRule.getValueParts();
        Assert.assertEquals(1, values.size());
        Assert.assertEquals("empty-*", values.get(0).getValue());
        Assert.assertNotNull(values.get(0).getPattern());
    }

    @Test
    public void testSingleFileTwoWildcardProps() throws Exception {
        final String file = "META-INF/microprofile-config.properties";
        DeploymentCreator creator =
                new DeploymentCreator("test.war")
                        .addFile(file);
        DeploymentFileRuleInspector inspector = new DeploymentFileRuleInspector(creator.rootPath, false);
        ParsedRule parsedRule = inspector.extractParsedRule(file + ",empty-*,value-*");
        List<Path> paths = parsedRule.getMatchedPaths();
        Assert.assertEquals(1, paths.size());
        Assert.assertEquals(creator.rootPath.resolve(file), paths.get(0));
        List<PatternOrValue> values = parsedRule.getValueParts();
        Assert.assertEquals(2, values.size());
        Assert.assertEquals("empty-*", values.get(0).getValue());
        Assert.assertNotNull(values.get(0).getPattern());
        Assert.assertEquals("value-*", values.get(1).getValue());
        Assert.assertNotNull(values.get(1).getPattern());
    }

    @Test
    public void testTwoFilesBothMatching() throws Exception {
        final String file = "META-INF/microprofile-config.properties";
        final String file2 = "WEB-INF/classes/META-INF/microprofile-config.properties";
        DeploymentCreator creator =
                new DeploymentCreator("test.war")
                        .addFile(file)
                        .addFile(file2);
        DeploymentFileRuleInspector inspector = new DeploymentFileRuleInspector(creator.rootPath, false);
        ParsedRule parsedRule = inspector.extractParsedRule(String.format("[%s,%s],empty-prop", file, file2));
        List<Path> paths = parsedRule.getMatchedPaths();
        Assert.assertEquals(2, paths.size());
        Assert.assertEquals(creator.rootPath.resolve(file), paths.get(0));
        Assert.assertEquals(creator.rootPath.resolve(file2), paths.get(1));
        List<PatternOrValue> values = parsedRule.getValueParts();
        Assert.assertEquals(1, values.size());
        Assert.assertEquals("empty-prop", values.get(0).getValue());
        Assert.assertNull(values.get(0).getPattern());
    }


    @Test
    public void testTwoFilesOneMatching() throws Exception {
        final String file = "META-INF/microprofile-config.properties";
        final String file2 = "WEB-INF/classes/META-INF/microprofile-config.properties";
        DeploymentCreator creator =
                new DeploymentCreator("test.war")
                        .addFile(file2);
        DeploymentFileRuleInspector inspector = new DeploymentFileRuleInspector(creator.rootPath, false);
        ParsedRule parsedRule = inspector.extractParsedRule(String.format("[%s,%s],prop,123", file + "non-match", file2));
        List<Path> paths = parsedRule.getMatchedPaths();
        Assert.assertEquals(1, paths.size());
        Assert.assertEquals(creator.rootPath.resolve(file2), paths.get(0));
        List<PatternOrValue> values = parsedRule.getValueParts();
        Assert.assertEquals(2, values.size());
        Assert.assertEquals("prop", values.get(0).getValue());
        Assert.assertNull(values.get(0).getPattern());
        Assert.assertEquals("123", values.get(1).getValue());
        Assert.assertNull(values.get(1).getPattern());
    }

    @Test
    public void testSingleFileWildcard() throws Exception {
        final String file = "META-INF/microprofile-config.properties";
        final String file2 = "META-INF/microprofile-config2.properties";
        DeploymentCreator creator =
                new DeploymentCreator("test.war")
                        .addFile(file)
                        .addFile(file2);
        DeploymentFileRuleInspector inspector = new DeploymentFileRuleInspector(creator.rootPath, false);
        ParsedRule parsedRule = inspector.extractParsedRule("META-INF/*.properties" + ",prop,123");
        List<Path> paths = parsedRule.getMatchedPaths();
        Assert.assertEquals(2, paths.size());
        Assert.assertEquals(creator.rootPath.resolve(file), paths.get(0));
        Assert.assertEquals(creator.rootPath.resolve(file2), paths.get(1));
        List<PatternOrValue> values = parsedRule.getValueParts();
        Assert.assertEquals(2, values.size());
        Assert.assertEquals("prop", values.get(0).getValue());
        Assert.assertNull(values.get(0).getPattern());
        Assert.assertEquals("123", values.get(1).getValue());
        Assert.assertNull(values.get(1).getPattern());
    }

    @Test
    public void testTwoFileWildcards() throws Exception {
        final String file = "META-INF/microprofile-config.properties";
        final String file2 = "META-INF/microprofile-config2.properties";
        final String file3 = "WEB-INF/xyz";
        DeploymentCreator creator =
                new DeploymentCreator("test.war")
                        .addFile(file)
                        .addFile(file2)
                        .addFile(file3);
        DeploymentFileRuleInspector inspector = new DeploymentFileRuleInspector(creator.rootPath, false);
        ParsedRule parsedRule = inspector.extractParsedRule("[META-INF/*.properties,WEB-INF/*]" + ",empty-prop");
        List<Path> paths = parsedRule.getMatchedPaths();
        Assert.assertEquals(3, paths.size());
        Assert.assertEquals(creator.rootPath.resolve(file), paths.get(0));
        Assert.assertEquals(creator.rootPath.resolve(file2), paths.get(1));
        Assert.assertEquals(creator.rootPath.resolve(file3), paths.get(2));
        List<PatternOrValue> values = parsedRule.getValueParts();
        Assert.assertEquals(1, values.size());
        Assert.assertEquals("empty-prop", values.get(0).getValue());
        Assert.assertNull(values.get(0).getPattern());
    }

    @Test
    public void testMixFileWildcardsAndNon() throws Exception {
        final String file = "META-INF/microprofile-config.properties";
        final String file2 = "META-INF/microprofile-config2.properties";
        final String file3 = "WEB-INF/test.txt";
        final String file4 = "WEB-INF/classes/META-INF/testing.xml";
        DeploymentCreator creator =
                new DeploymentCreator("test.war")
                        .addFile(file)
                        .addFile(file2)
                        .addFile(file3)
                        .addFile(file4);
        DeploymentFileRuleInspector inspector = new DeploymentFileRuleInspector(creator.rootPath, false);
        ParsedRule parsedRule = inspector.extractParsedRule("[META-INF/*.properties,WEB-INF/test.txt,WEB-INF/classes/META-INF/*]" + ",empty-prop");
        List<Path> paths = parsedRule.getMatchedPaths();
        Assert.assertEquals(4, paths.size());
        Assert.assertEquals(creator.rootPath.resolve(file), paths.get(0));
        Assert.assertEquals(creator.rootPath.resolve(file2), paths.get(1));
        Assert.assertEquals(creator.rootPath.resolve(file3), paths.get(2));
        Assert.assertEquals(creator.rootPath.resolve(file4), paths.get(3));
        List<PatternOrValue> values = parsedRule.getValueParts();
        Assert.assertEquals(1, values.size());
        Assert.assertEquals("empty-prop", values.get(0).getValue());
        Assert.assertNull(values.get(0).getPattern());
    }

    private static class DeploymentCreator{
        private final String deploymentName;
        private final Path rootPath;

        public DeploymentCreator(String deploymentName) throws IOException {
            this.deploymentName = deploymentName;
            rootPath = Path.of("target/deployment").resolve(deploymentName);
            if (Files.exists(rootPath)) {
                try {
                    Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            Files.createDirectories(rootPath);
            addFile(NON_MATCHING_FILE);
        }

        DeploymentCreator addFile(String pathRelativeToRoot) throws IOException {
            Assert.assertFalse("Paths should be relative, not absolute", pathRelativeToRoot.startsWith("/"));
            Path path = rootPath.resolve(pathRelativeToRoot);
            Path dir = path.getParent();
            Files.createDirectories(dir);
            Files.createFile(path);
            return this;
        }
    }
}
