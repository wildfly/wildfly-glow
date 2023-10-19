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
package org.wildfly.glow.error;

import java.util.Objects;

/**
 *
 * @author jdenise
 */
public class Fix {

    private final String description;
    private final String content;
    private final String forId;

    public Fix(String forId, String description, String content) {
        this.description = description;
        this.content = content;
        this.forId = forId;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.description);
        hash = 47 * hash + Objects.hashCode(this.content);
        hash = 47 * hash + Objects.hashCode(this.forId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Fix other = (Fix) obj;
        if (!Objects.equals(this.description, other.description)) {
            return false;
        }
        if (!Objects.equals(this.content, other.content)) {
            return false;
        }
        return Objects.equals(this.forId, other.forId);
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    public boolean isEnv() {
        return description.endsWith(" env");
    }

    public String getEnvName() {
        String[] split = content.split("=");
        return split[0];
    }

    /**
     * @return the content
     */
    public String getContent() {
        return content;
    }

    /**
     * @return the forId
     */
    public String getForId() {
        return forId;
    }

    public static String getEnvValue(String value) {
        String[] split = value.split("=");
        return split[1];
    }
}
