/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.glow;

import java.util.Objects;

/**
 *
 * @author jdenise
 */
public final class Space {
    public static final Space DEFAULT = new Space("default", "Default space");
    private final String name;
    private final String description;

    Space(String name, String description) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(description);
        this.name = name;
        this.description = description;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object obj) {
        if (! (obj instanceof Space)) {
            return false;
        }
        Space other = (Space) obj;
        return this.name.equals(other.name) && this.description.equals(other.description);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + Objects.hashCode(this.name);
        hash = 47 * hash + Objects.hashCode(this.description);
        return hash;
    }

}
