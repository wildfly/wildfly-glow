package org.wildfly.glow.jbang;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.wildfly.glow.GlowSession;

public class JBangIntegration {

    public static void extractJar(Path jar, Path destDir) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                File entryFile = new File(destDir.toFile(), entry.getName());

                if (entry.isDirectory()) {
                    entryFile.mkdirs(); // Create directories
                } else {
                    // Ensure parent directories exist
                    entryFile.getParentFile().mkdirs();

                    try (InputStream is = jarFile.getInputStream(entry);
                            OutputStream os = new FileOutputStream(entryFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
            System.out.println("JAR extracted successfully to " + destDir);
        } catch (IOException e) {
            throw e;
        }
    }

    public static Path toWar(Path appClasses) throws IOException {
        Path parent = appClasses.getParent();
        Path webInf = parent.resolve("WEB-INF");
        Path webClassesDir = webInf.resolve("classes");

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

        String warName = parent.getFileName().toString().split("\\.")[0] + ".war";
        Path warFile = parent.resolve(warName);

        try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(warFile.toFile()))) {
            Files.walk(webInf).filter(Files::isRegularFile).forEach(file -> {
                try {
                    String zipEntryName = parent.relativize(file).toString().replace("\\", "/");
                    zipOut.putNextEntry(new ZipEntry(zipEntryName));
                    Files.copy(file, zipOut);
                    zipOut.closeEntry();
                } catch (IOException e) {
                    // ignore
                }
            });
        }

        return warFile;
    }

    public static Path runGlowScan(Path glowJar, Path war, Optional<String> glowArgs) throws Exception {
        System.out.println("Scanning " + war + " with " + glowJar);
        // todo clean up this temp dir
        Path temp = Files.createTempDirectory("glow");

        // TODO Find the right jdk version to run Glow in a forked process
        // with something like `jbang jdk list --format=json`
        // for now let's use "java"

        List<String> args = new ArrayList<>();
        args.addAll(List.of("java", "-jar", glowJar.toString(),
                "scan",
                "--provision=BOOTABLE_JAR",
                "--output-dir=" + temp));
        if (glowArgs.isPresent()) {
            args.addAll(Arrays.asList(glowArgs.get().split("\\s+")));
        }
        args.add(war.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(args)
                .redirectErrorStream(true);

        try {
            System.out.println("Running " + processBuilder.command());
            Process process = processBuilder.start();

            // Read the output of the process
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Error during WildFly Glow scan");
            }

            // find the bootable jar in the temp directory
            try (Stream<Path> files = Files.list(temp)) {
                List<Path> fileList = files.filter(Files::isRegularFile) // Only files, not directories
                        .collect(Collectors.toList());
                return (fileList.size() == 1) ? fileList.get(0) : null; // Return the single file or null
            }
        } catch (IOException | InterruptedException e) {
            throw e;
        }
    }

    public static Map<String, Object> postBuild(Path appClasses, Path pomFile,
            List<Map.Entry<String, String>> repositories,
            List<Map.Entry<String, Path>> originalDeps,
            List<String> comments, boolean nativeImage) throws IOException {

        Optional<String> glowArgs = comments.stream()
                .filter(s -> s.startsWith("//GLOW "))
                .map(s -> s.substring(6).strip())
                .collect(Collectors.reducing((s1, s2) -> s1 + " " + s2));

        Path war = toWar(appClasses);

        try {
            Path glowJar = new File(GlowSession.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toPath();
            // TODO: instead of using the latest WildFly version from Glow, we should honour
            // the version from the org.wildfly.bom:wildfly-ee-with-tools
            Path bootableJar = runGlowScan(glowJar, war, glowArgs);
            System.out.println("Bootable Jar: " + bootableJar);

            // unzip the bootable jar in the JBang cache for the classes
            extractJar(bootableJar, appClasses);

            Map<String, Object> results = new HashMap<>();
            results.put("main-class", "org.wildfly.core.jar.boot.Main");
            return results;
        } catch (Exception e) {
            throw new IOException("Unable to create the WildFly applciation", e);
        }
    }
}