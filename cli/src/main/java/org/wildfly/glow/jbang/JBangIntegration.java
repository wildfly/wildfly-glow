package org.wildfly.glow.jbang;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.ScanResults;
//import org.wildfly.glow.GlowSession;
//import org.wildfly.glow.ScanResults;
import org.wildfly.glow.maven.MavenResolver;

public class JBangIntegration {
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

    public static Map<String, Object> postBuild(Path appClasses, Path pomFile,
            List<Map.Entry<String, String>> repositories,
            List<Map.Entry<String, Path>> originalDeps,
            List<String> comments, boolean nativeImage) throws IOException {

        System.out.println("JBangIntegration.postBuild()");
        System.out.println(appClasses);
        System.out.println(pomFile);
        System.out.println(repositories);
        System.out.println(originalDeps);
        System.out.println(comments);
        System.out.println(nativeImage);

        Path war = toWar(appClasses);

        MavenRepoManager resolver = MavenResolver.newMavenResolver();
        Arguments arguments = Arguments.scanBuilder()
                .setBinaries(List.of(war))
                .build();
        GlowMessageWriter writer = GlowMessageWriter.DEFAULT;

        try {
            writer.info("Scanning war: " + war);
            ScanResults results = GlowSession.scan(resolver, arguments, writer);
            results.outputInformation(writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new HashMap<>();

    }

}