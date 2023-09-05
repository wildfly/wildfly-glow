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
package org.wildfly.glow.plugin.arquillian;

import java.util.Collections;
import java.util.List;

public class FeaturePack {

    private String groupId;
    private String artifactId;
    private String version;
    private String classifier;
    private String extension = "zip";
    private List<String> includedPackages = Collections.emptyList();

    public List<String> getIncludedPackages() {
        return includedPackages;
    }

    public void setIncludedPackages(List<String> includedPackages) {
        this.includedPackages = includedPackages;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getMavenCoords() {
        StringBuilder builder = new StringBuilder();
        builder.append(getGroupId()).append(":").append(getArtifactId());
        String type = getExtension();
        if (getClassifier() != null || type != null) {
            builder.append(":").append(getClassifier() == null ? "" : getClassifier()).append(":")
                    .append(type == null ? "" : type);
        }
        if (getVersion() != null) {
            builder.append(":").append(getVersion());
        }
        return builder.toString();
    }
}
