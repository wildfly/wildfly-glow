/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.glow;

import java.util.Map;

/**
 * Contains the content of the rule that identify layers based on an annotated type.
 * @author jdenise
 */
public class AnnotatedType {

    private final String type;
    private final Map<String, String> fields;
    private final String annotation;
    private final Layer layer;

    /**
     *
     * @param type The java type
     * @param annotation The annotation applied to the type
     * @param fields Annotation field values (can be empty).
     * @param layer The layer associated to the rule.
     */
    public AnnotatedType(String type, String annotation, Map<String, String> fields, Layer layer) {
        this.type = type;
        this.annotation = annotation;
        this.fields = fields;
        this.layer = layer;
    }

    /**
     * @return The annotation full class name.
     */
    public String getAnnotation() {
        return annotation;
    }

    /**
     * @return The annotation fields.
     */
    public Map<String, String> getFields() {
        return fields;
    }

    /**
     * @return The layer
     */
    public Layer getLayer() {
        return layer;
    }

    /**
     * @return The java type.
     */
    public String getType() {
        return type;
    }
}
