/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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
package org.wildfly.glow.deployment.openshift.api;

/**
 *
 * @author jdenise
 */
public class OpenShiftConfiguration {

    private final String labelRadical;
    private final String serverImageNameRadical;
    private final String builderImage;
    private final String runtimeImage;

    private OpenShiftConfiguration(String labelRadical,
            String serverImageNameRadical,
            String builderImage,
            String runtimeImage) {
        this.labelRadical = labelRadical;
        this.serverImageNameRadical = serverImageNameRadical;
        this.builderImage = builderImage;
        this.runtimeImage = runtimeImage;
    }

    public static class Builder {

        private String labelRadical = "org.wildfly.glow";
        private String serverImageNameRadical = "wildfly-server-";
        private String builderImage = "quay.io/wildfly/wildfly-s2i:latest";
        private String runtimeImage = "quay.io/wildfly/wildfly-runtime:latest";

        public Builder setLabelRadical(String radical) {
            this.labelRadical = radical;
            return this;
        }

        public Builder setServerImageNameRadical(String radical) {
            this.serverImageNameRadical = radical;
            return this;
        }

        public Builder setBuilderImage(String img) {
            this.builderImage = img;
            return this;
        }

        public Builder setRuntimeImage(String img) {
            this.runtimeImage = img;
            return this;
        }

        public OpenShiftConfiguration build() {
            return new OpenShiftConfiguration(labelRadical, serverImageNameRadical, builderImage, runtimeImage);
        }
    }

    /**
     * @return the labelRadical
     */
    public String getLabelRadical() {
        return labelRadical;
    }

    /**
     * @return the serverImageNameRadical
     */
    public String getServerImageNameRadical() {
        return serverImageNameRadical;
    }

    /**
     * @return the builderImage
     */
    public String getBuilderImage() {
        return builderImage;
    }

    /**
     * @return the runtimeImage
     */
    public String getRuntimeImage() {
        return runtimeImage;
    }
}
