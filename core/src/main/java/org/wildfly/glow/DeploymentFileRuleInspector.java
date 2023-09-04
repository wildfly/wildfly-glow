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

package org.wildfly.glow;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DeploymentFileRuleInspector {
    private final Path rootPath;
    private final boolean archive;
    private final ArchiveFileRegistry archiveFileRegistry;

    public DeploymentFileRuleInspector(
            Path rootPath,
            boolean archive) throws IOException {
        this.rootPath = rootPath;
        this.archive = archive;
        this.archiveFileRegistry = new ArchiveFileRegistry(rootPath);
    }

    public ParsedRule extractParsedRule(String prop) {
        return ParsedRule.extract(archiveFileRegistry, rootPath, prop);
    }

    private class ArchiveFileRegistry {
        private final Map<String, Path> allFilePaths;
        private final Path rootPath;

        public ArchiveFileRegistry(Path rootPath) throws IOException {
            Map<String, Path> allFilePaths = new HashMap<>();
            Files.walkFileTree(rootPath,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new NestedWarOrExplodedArchiveFileVisitor(rootPath, archive) {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    allFilePaths.put(dir.toString(), dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    allFilePaths.put(file.toString(), file);
                    return FileVisitResult.CONTINUE;
                }
            });

            this.allFilePaths = Collections.unmodifiableMap(allFilePaths);
            this.rootPath = rootPath;
        }

        List<Path> getArchivePaths(List<PatternOrValue> rulePaths) {
            List<Path> paths = new ArrayList<>();
            for (PatternOrValue path : rulePaths) {
                if (!path.isPattern()) {
                    if (allFilePaths.containsKey(path.value)) {
                        paths.add(allFilePaths.get(path.value));
                    }
                } else {
                    for (Map.Entry<String, Path> entry : allFilePaths.entrySet()) {
                        if (path.pattern.matcher(entry.getKey()).matches()) {
                            paths.add(entry.getValue());
                        }
                    }
                }
            }
            return paths;
        }

        private String pathRelativeToRoot(String path) {
            if (path.startsWith("/")) {
                return path.substring(1);
            }
            return path;
        }

        private String adjustPatternInputRelativeToRoot(Path rootPath, String pathPattern) {
            if (rootPath.toString().endsWith("/")) {
                return rootPath + pathRelativeToRoot(pathPattern);
            } else {
                return rootPath + pathPattern;
            }
        }
    }


    public static class PatternOrValue {
        private final String value;
        private final Pattern pattern;

        private PatternOrValue(String value, Pattern pattern) {
            this.value = value;
            this.pattern = pattern;
        }

        static PatternOrValue createForFile(Path rootPath, String path) {
            String escapedPath;
            Pattern pattern = null;
            if (Utils.isPattern(path)) {
                escapedPath = Utils.escapePattern(path);
                escapedPath = adjustPatternInputRelativeToRoot(rootPath, escapedPath);
                pattern = Pattern.compile(escapedPath);
            } else {
                escapedPath = rootPath.resolve(pathRelativeToRoot(path)).toString();
            }
            return new PatternOrValue(escapedPath, pattern);
        }

        static PatternOrValue createForValue(String value) {
            if (Utils.isPattern(value)) {
                return new PatternOrValue(value, Pattern.compile(Utils.escapePattern(value)));
            }
            return new PatternOrValue(value, null);
        }

        public boolean equalsOrMatches(String value) {
            if (pattern != null) {
                return pattern.matcher(value).matches();
            } else {
                return this.value.equals(value);
            }
        }

        public boolean isPattern() {
            return pattern != null;
        }

        private static String pathRelativeToRoot(String path) {
            if (path.startsWith("/")) {
                return path.substring(1);
            }
            return path;
        }

        private static String adjustPatternInputRelativeToRoot(Path rootPath, String pathPattern) {
            String path = rootPath.toString();
            if (!path.endsWith("/")) {
                path += "/";
            }
            path += pathRelativeToRoot(pathPattern);
            return path;
        }

        public String getValue() {
            return value;
        }

        public Pattern getPattern() {
            return pattern;
        }
    }

    public static class ParsedRule {
        private final List<PatternOrValue> fileParts;
        private final List<PatternOrValue> valueParts;
        private final List<Path> matchedPaths;

        public ParsedRule(List<PatternOrValue> fileParts, List<PatternOrValue> valueParts, List<Path> matchedPaths) {
            this.fileParts = fileParts;
            this.valueParts = valueParts;
            this.matchedPaths = matchedPaths;
        }

        private static ParsedRule extract(ArchiveFileRegistry registry, Path rootPath, String prop) {
            final List<PatternOrValue> fileParts;
            List<PatternOrValue> valueParts;
            if (prop.startsWith("[")) {
                int index = prop.indexOf("]");
                if (index == -1) {
                    throw new IllegalStateException("Expected a closing ']' in " + prop);
                }
                String filesPart = prop.substring(1, index);
                fileParts = patternOrValueListFromArray(filesPart.split(","), p -> PatternOrValue.createForFile(rootPath, p));
                index = prop.indexOf(',', index + 1);
                if (index == -1) {
                    valueParts = Collections.emptyList();
                } else {
                    String values = prop.substring(index + 1);
                    valueParts = patternOrValueListFromArray(values.split(","), v -> PatternOrValue.createForValue(v));
                }
            } else {
                String[] split = prop.split(",");
                fileParts = patternOrValueListFromArray(new String[]{split[0]}, p -> PatternOrValue.createForFile(rootPath, p));
                valueParts = patternOrValueListFromArray(Arrays.copyOfRange(split, 1, split.length), v -> PatternOrValue.createForValue(v));
            }
            List<Path> matchedPaths = registry.getArchivePaths(fileParts);
            return new ParsedRule(fileParts, valueParts, matchedPaths);
        }

        private static List<PatternOrValue> patternOrValueListFromArray(String[] arr, Function<String, PatternOrValue> factory) {
            return Arrays.stream(arr)
                    .map(v -> factory.apply(v))
                    .collect(Collectors.toList());
        }

        public List<Path> getMatchedPaths() {
            return matchedPaths;
        }

        public List<PatternOrValue> getValueParts() {
            return valueParts;
        }

        public void iterateMatchedPaths(MatchedPathConsumer consumer) throws Exception {
            for (Path path : matchedPaths) {
                if (Files.exists(path)) {
                    consumer.accept(path, valueParts);
                }
            }
        }
    }

    @FunctionalInterface
    public interface MatchedPathConsumer {
        void accept(Path path, List<PatternOrValue> values) throws Exception;
    }
}
