/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2025 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.glow.jbang;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.wildfly.glow.GlowSession;

public class JBangIntegration {

    private static final Pattern MAVEN_JAR_PATTERN = Pattern.compile(
            "([a-zA-Z0-9_.-]+):([a-zA-Z0-9_.-]+):jar:([a-zA-Z0-9_.-]+)");

    public static void extractJar(Path jar, Path destDir) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(jar, null)) {
            Path root = fs.getPath("/");

            // Walk the tree inside the JAR
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path destFile = destDir.resolve(root.relativize(file).toString());
                    Files.createDirectories(destFile.getParent());
                    Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path destDirPath = destDir.resolve(root.relativize(dir).toString());
                    Files.createDirectories(destDirPath);
                    return FileVisitResult.CONTINUE;
                }
            });

            System.out.println("JAR extracted successfully to " + destDir);
        }
    }

    public static Path toWar(Path appClasses, Set<Path> libs) throws IOException {
        System.out.println("Adding libs to WAR: " + libs);
        Path parent = appClasses.getParent();
        Path webInf = parent.resolve("WEB-INF");
        Path webClassesDir = webInf.resolve("classes");
        Path webLibDir = webInf.resolve("lib");

        Files.createDirectories(webClassesDir);

        Files.walkFileTree(appClasses, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetPath = webClassesDir.resolve(appClasses.relativize(file));
                Files.createDirectories(targetPath.getParent());
                Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(webClassesDir.resolve(appClasses.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
        });

        if (libs.size() > 0) {
            Files.createDirectories(webLibDir);
        }
        for (Path lib : libs) {
            Path targetPath = webLibDir.resolve(lib.getFileName());
            Files.copy(lib, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        String warName = parent.getFileName().toString().split("\\.")[0] + ".war";
        Path warFile = parent.resolve(warName);

        URI warUri = URI.create("jar:" + warFile.toUri());
        try (FileSystem warFs = FileSystems.newFileSystem(warUri, Collections.singletonMap("create", "true"))) {
            Path warRoot = warFs.getPath("/");

            Files.walkFileTree(webInf, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path targetPath = warRoot.resolve(parent.relativize(file).toString().replace("\\", "/"));
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(warRoot.resolve(webInf.relativize(dir).toString().replace("\\", "/")));
                    return FileVisitResult.CONTINUE;
                }
            });

        }

        return warFile;
    }

    public static Path runGlowScan(Path glowJar, Path war, Optional<String> glowArgs) throws Exception {
        System.out.println("Scanning " + war + " with " + glowJar);

        Path temp = Files.createTempDirectory("glow");
        List<String> args = new ArrayList<>(List.of(
                "java", "-jar", glowJar.toString(),
                "scan",
                "--provision=BOOTABLE_JAR",
                "--output-dir=" + temp));
        glowArgs.map(s -> Arrays.asList(s.split("\\s+"))).ifPresent(args::addAll);
        args.add(war.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(args)
                .redirectErrorStream(true);

        System.out.println("Running command: " + String.join(" ", processBuilder.command()));

        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.lines().forEach(System.out::println);
        }

        // Wait for the process to complete
        if (process.waitFor() != 0) {
            throw new IOException("Glow scan failed with exit code " + process.exitValue());
        }

        // Find the bootable JAR in the temp directory
        try (Stream<Path> files = Files.list(temp)) {
            List<Path> fileList = files.filter(Files::isRegularFile)
                    .collect(Collectors.toList());

            return (fileList.size() == 1) ? fileList.get(0) : null;
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                        System.err.println("Failed to delete: " + path);
                    }
                });
            }
        }
    }

    // Filter the dependencies to add the versioned dependencies as libs in the WAR
    // web-inf/lib directory.
    //
    // The other dependencies without versions are ignored as they are meant to be
    // provided by WildFly
    // The originalDeps keys are of the form:
    //      {groupId}:{artifactId}:jar:{version}
    // while in the comments they are specified with either of the forms:
    //      //DEPS {groupId}:{artifactId}:{version}
    //      //DEPS {groupId}:{artifactId}
    public static Set<Path> findLibrariestoInclude(List<Map.Entry<String, Path>> originalDeps, List<String> comments) {
        return originalDeps.stream()
                .filter(entry -> {
                    Matcher matcher = MAVEN_JAR_PATTERN.matcher(entry.getKey());

                    if (!matcher.matches()) {
                        return false;
                    }

                    String groupId = matcher.group(1);
                    String artifactId = matcher.group(2);
                    String version = matcher.group(3);

                    // Ignore org.wildfly.glow:wildfly-glow artifact
                    if ("org.wildfly.glow".equals(groupId) && "wildfly-glow".equals(artifactId)) {
                        return false;
                    }

                    // Keep versioned dependencies specified in comments
                    String dep = String.format("//DEPS %s:%s:%s", groupId, artifactId, version);
                    return comments.contains(dep);
                })
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());
    }

    public static Map<String, Object> postBuild(Path appClasses, Path pomFile,
            List<Map.Entry<String, String>> repositories,
            List<Map.Entry<String, Path>> originalDeps,
            List<String> comments, boolean nativeImage) throws IOException {

        Optional<String> glowArgs = comments.stream()
                .filter(s -> s.startsWith("//GLOW "))
                .map(s -> s.substring(6).strip())
                .collect(Collectors.reducing((s1, s2) -> s1 + " " + s2));

        Set<Path> libs = findLibrariestoInclude(originalDeps, comments);

        Path war = toWar(appClasses, libs);

        try {
            Path glowJar = new File(GlowSession.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toPath();
            // TODO: instead of using the latest WildFly version from Glow, we should honour
            // the version from the org.wildfly.bom:wildfly-ee-with-tools
            Path bootableJar = runGlowScan(glowJar, war, glowArgs);
            System.out.println("Bootable Jar: " + bootableJar);

            // unzip the bootable jar in the JBang cache for the classes
            extractJar(bootableJar, appClasses);

            // delete the temp directory used by Glow to create the Bootable Jar
            deleteDirectory(bootableJar.getParent());

            Map<String, Object> results = new HashMap<>();
            results.put("main-class", "org.wildfly.core.jar.boot.Main");
            return results;
        } catch (Exception e) {
            throw new IOException("Unable to create the WildFly applciation", e);
        }
    }
}