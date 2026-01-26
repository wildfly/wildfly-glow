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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.wildfly.glow.AddOn;

/**
 *
 * @author jdenise
 */
public class IdentifiedError {

    private final String id;
    private final String description;
    private ErrorLevel errorLevel;
    private boolean fixed;
    private String fixMessage;
    private final Set<AddOn> possibleAddOns = new TreeSet<>();
    private final List<String> unverifiedFixes = new ArrayList<>();

    public IdentifiedError(String id, String description, ErrorLevel errorLevel) {
        this.id = id;
        this.description = description;
        this.errorLevel = errorLevel;
    }

    public void setFixed(String message) {
        this.fixed = true;
        this.fixMessage = message;
    }

    public String getFixMessage() {
        return fixMessage;
    }

    public boolean isFixed() {
        return fixed;
    }

    public Set<AddOn> getPossibleAddons() {
        return possibleAddOns;
    }

    /**
     *
     * @return a list of unverified fixes
     */
    public List<String> getUnverifiedFixes() {
        return unverifiedFixes;
    }

    public ErrorLevel getErrorLevel() {
        return errorLevel;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.id);
        hash = 97 * hash + Objects.hashCode(this.description);
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
        final IdentifiedError other = (IdentifiedError) obj;
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        return Objects.equals(this.description, other.description);
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }
}
