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

package org.wildfly.glow.test.core.exploded;

import org.jboss.galleon.util.ZipUtils;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.maven.MavenResolver;
import org.wildfly.glow.test.core.exploded.jar.Pojo;
import org.wildfly.glow.test.core.exploded.jar.StatelessBean;
import org.wildfly.glow.test.core.exploded.rar.DummyRarClass;
import org.wildfly.glow.test.core.exploded.sar.SarClass;
import org.wildfly.glow.test.core.exploded.sar.SarClassMBean;
import org.wildfly.glow.test.core.exploded.war.MessagingBean;
import org.wildfly.glow.test.core.exploded.war.TestServlet;
import org.wildfly.glow.test.core.exploded.warlib.TestWarLibClass;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.function.Consumer;

/**
 * A big focus for this test is to test that various deployment descriptors get picked out when running exploded.
 * The aim is to test as many of the options from test
 */
public class ExplodedArchiveTestCase {

    private final Path archivesPath = Path.of("target/archives");
    private static final String WAR_NAME = "web.war";
    private static final String WAR_LIB_JAR_NAME = "warlib.jar";
    private static final String SAR_NAME = "sar.sar";
    private static final String RAR_NAME = "rar.rar";
    private static final String JAR_NAME = "jar.jar";
    private static final String EAR_NAME = "ear.ear";

    @Before
    public void setupArchiveDirectory() throws Exception {
        if (Files.exists(archivesPath)) {
            Files.walkFileTree(archivesPath, new SimpleFileVisitor<>() {
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
        }
        Files.createDirectories(archivesPath);
    }

    private WebArchive createWar() {
        WebArchive war = ShrinkWrap.create(WebArchive.class, WAR_NAME);
        war.addClasses(TestServlet.class, MessagingBean.class);
        war.addAsWebInfResource(TestServlet.class.getResource("microprofile-config.properties"), "classes/META-INF/microprofile-config.properties");
        // Tests nested annotations work
        JavaArchive libjar = ShrinkWrap.create(JavaArchive.class, WAR_LIB_JAR_NAME);
        libjar.addClass(TestWarLibClass.class);
        war.addAsLibrary(libjar);
        return war;
    }

    private JavaArchive createSar() {
        // Tests expected-file works
        JavaArchive sar = ShrinkWrap.create(JavaArchive.class, SAR_NAME);
        sar.addClasses(SarClass.class, SarClassMBean.class);
        sar.addAsManifestResource(SarClass.class.getResource("jboss-service.xml"), "jboss-service.xml");
        return sar;
    }

    private JavaArchive createRar() {
        JavaArchive rar = ShrinkWrap.create(JavaArchive.class, RAR_NAME);
        rar.addClass(DummyRarClass.class);
        rar.addAsManifestResource(DummyRarClass.class.getResource("ironjacamar.xml"), "ironjacamar.xml");

        return rar;
    }

    private JavaArchive createJar() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, JAR_NAME);
        jar.addClasses(Pojo.class, StatelessBean.class);
        jar.addAsManifestResource(Pojo.class.getResource("pojo-jboss-beans.xml"), "pojo-jboss-beans.xml");
        jar.addAsManifestResource(Pojo.class.getResource("test-activemq-jms.xml"), "test-activemq-jms.xml");
        jar.addAsManifestResource(Pojo.class.getResource("test-ds.xml"), "test-ds.xml");
        return jar;
    }

    private EnterpriseArchive createEar() {
        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, EAR_NAME);
        ear.addAsLibrary(createJar());
        ear.addAsLibrary(createRar());
        ear.addAsLibrary(createSar());
        ear.addAsLibrary(createWar());
        return ear;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // WAR tests
    @Test
    public void testWar() throws Exception {
        testWar(path -> {});
    }

    @Test
    public void testExplodedWar() throws Exception {
        testWar(path -> {
            unzipArchive(path);
        });
    }

    @Test
    public void testRecursivelyExplodedWar() throws Exception {
        testWar(path -> {
            unzipArchive(path);
            unzipArchive(path.resolve("WEB-INF/lib/" + WAR_LIB_JAR_NAME));
        });
    }

    private void testWar(Consumer<Path> pathConsumer) throws Exception {
        WebArchive archive = createWar();
        Path archivePath = exportArchive(archive);
        pathConsumer.accept(archivePath);
        String layers = runScan(archivePath);
        Assert.assertEquals("[cdi, microprofile-config, microprofile-reactive-messaging, " +
                "microprofile-reactive-messaging-kafka, servlet]==>ee-core-profile-server,microprofile-reactive-messaging-kafka", layers);
    }
    // WAR - end
    ///////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // SAR tests

    @Test
    public void testSar() throws Exception {
        testSar(path -> {});
    }

    @Test
    public void testExplodedSar() throws Exception {
        testSar(path -> {
            unzipArchive(path);
        });
    }

    // Sars don't seem to accept sub-deployments, so no recursive test for now

    private void testSar(Consumer<Path> pathConsumer) throws Exception {
        JavaArchive archive = createSar();
        Path archivePath = exportArchive(archive);
        pathConsumer.accept(archivePath);
        String layers = runScan(archivePath);
        Assert.assertEquals("[sar]==>ee-core-profile-server,sar", layers);
    }


    // SAR - end
    ///////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // RAR tests

    @Test
    public void testRar() throws Exception {
        testRar(path -> {});
    }

    @Test
    public void testExplodedRar() throws Exception {
        testRar(path -> {
            unzipArchive(path);
        });
    }

    // Although rars seem to accept nested deployments, we are testing those in other places

    private void testRar(Consumer<Path> pathConsumer) throws Exception {
        JavaArchive archive = createRar();
        Path archivePath = exportArchive(archive);
        pathConsumer.accept(archivePath);
        String layers = runScan(archivePath);
        Assert.assertEquals("[resource-adapters]==>ee-core-profile-server,resource-adapters", layers);
    }

    // RAR - end
    ///////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // JAR tests

    @Test
    public void testJar() throws Exception {
        testJar(path -> {});
    }

    @Test
    public void testExplodedJar() throws Exception {
        testJar(path -> {
            unzipArchive(path);
        });
    }

    // Jars don't seem to accept sub-deployments, so no recursive test for now

    private void testJar(Consumer<Path> pathConsumer) throws Exception {
        JavaArchive archive = createJar();
        Path archivePath = exportArchive(archive);
        pathConsumer.accept(archivePath);
        String layers = runScan(archivePath);
        Assert.assertEquals("[ejb-lite, embedded-activemq, h2-driver, pojo]==>ee-core-profile-server,ejb-lite,embedded-activemq,h2-driver,pojo", layers);
    }

    // JAR - end
    ///////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // EAR tests
    @Test
    public void testEar() throws Exception {
        testEar(path -> {});
    }

    @Test
    public void testExplodedEar() throws Exception {
        testEar(path -> {
            unzipArchive(path);
        });
    }

    @Test
    public void testRecursivelyExplodedEar() throws Exception {
        testEar(path -> {
            unzipArchive(path);
            unzipArchive(path.resolve("lib/web.war"));
            unzipArchive(path.resolve("lib/web.war/WEB-INF/lib/warlib.jar"));
            unzipArchive(path.resolve("lib/sar.sar"));
            unzipArchive(path.resolve("lib/rar.rar"));
            unzipArchive(path.resolve("lib/jar.jar"));
        });
    }


    private void testEar(Consumer<Path> pathConsumer) throws Exception {
        EnterpriseArchive archive = createEar();
        Path archivePath = exportArchive(archive);
        pathConsumer.accept(archivePath);
        String layers = runScan(archivePath);
        Assert.assertEquals("[cdi, ejb-lite, embedded-activemq, h2-driver, microprofile-config, " +
                "microprofile-reactive-messaging, microprofile-reactive-messaging-kafka, pojo, resource-adapters, " +
                "sar, servlet]==>" +
                "ee-core-profile-server,ejb-lite,embedded-activemq,h2-driver,microprofile-reactive-messaging-kafka,pojo,sar", layers);
    }

    // EAR - end
    ///////////////////////

    private Path exportArchive(Archive archive) throws Exception {
        ZipExporter exporter = archive.as(ZipExporter.class);
        Path path = archivesPath.resolve(archive.getName());
        exporter.exportTo(path.toFile());
        return path;
    }

    private void unzipArchive(Path archivePath) {
        try {
            Path copy = archivePath.getParent().resolve(archivePath.getFileName().toString() + ".bak");
            Files.move(archivePath, copy);
            Files.createDirectory(archivePath);
            try (FileSystem fs = ZipUtils.newFileSystem(copy)) {
                Path rootPath = fs.getPath("/");
                Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path target = getTargetPath(dir);
                        Files.createDirectories(target);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path target = getTargetPath(file);
                        Files.copy(file, target);
                        return FileVisitResult.CONTINUE;
                    }

                    private Path getTargetPath(Path zipPath) {
                        Path relative = rootPath.relativize(zipPath);
                        Path explodedPath = archivePath.resolve(relative.toString());
                        return explodedPath;
                    }
                });
            }


            Files.delete(copy);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String runScan(Path archive) throws Exception {
        Arguments arguments = Arguments.scanBuilder().setBinaries(Collections.singletonList(archive)).build();
        ScanResults scanResults = GlowSession.scan(MavenResolver.newMavenResolver(), arguments, GlowMessageWriter.DEFAULT);
        return scanResults.getCompactInformation();
    }
}
