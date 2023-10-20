/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.wildfly.glow;

import java.nio.file.Path;
import java.util.Map;

/**
 *
 * @author jdenise
 */
public class OutputContent {
    public enum OutputFile {
        BOOTABLE_JAR_FILE,
        DOCKER_FILE,
        SERVER_DIR,
        PROVISIONING_XML_FILE,
        ENV_FILE
    }

    private final Map<OutputFile, Path> files;
    private final String dockerImageName;
    OutputContent(Map<OutputFile, Path> files, String dockerImageName) {
        this.files = files;
        this.dockerImageName = dockerImageName;
    }

    /**
     * @return the files
     */
    public Map<OutputFile, Path> getFiles() {
        return files;
    }

    /**
     * @return the dockerImageName
     */
    public String getDockerImageName() {
        return dockerImageName;
    }
}
