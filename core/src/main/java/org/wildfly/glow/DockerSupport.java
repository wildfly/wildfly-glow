/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
package org.wildfly.glow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.String.format;
import static java.lang.String.join;

/**
 *
 * @author jdenise
 */
public class DockerSupport {

    public static boolean buildApplicationImage(String image, Path jbossHome, Arguments arguments, GlowMessageWriter writer) throws IOException {
        jbossHome = jbossHome.toAbsolutePath();
        String binary = ExecUtil.resolveImageBinary(writer);
        generateDockerfile( "quay.io/wildfly/wildfly-runtime:latest", jbossHome.getParent(), jbossHome);
        writer.info(format("Building application image %s using %s.", image, binary));
        String[] dockerArgs = new String[]{"build", "-t", image, "."};

        writer.info(format("Executing the following command to build application image: '%s %s'",
                binary, join(" ", dockerArgs)));
        return ExecUtil.exec(jbossHome.getParent().toFile(), binary, writer,
                dockerArgs);
    }

    private static void generateDockerfile(String runtimeImage, Path targetDir, Path jbossHome)
            throws IOException {

        // Docker requires the source file be relative to the context. From the documentation:
        // The <src> path must be inside the context of the build; you cannot COPY ../something /something, because
        // the first step of a docker build is to send the context directory (and subdirectories) to the docker daemon.
        if (jbossHome.isAbsolute()) {
            jbossHome = targetDir.relativize(jbossHome);
        }

        Files.writeString(targetDir.resolve("Dockerfile"),
                "FROM " + runtimeImage + "\n"
                + "COPY --chown=jboss:root " + jbossHome + " $JBOSS_HOME\n"
                + "RUN chmod -R ug+rwX $JBOSS_HOME\n",
                StandardCharsets.UTF_8);
    }
}
