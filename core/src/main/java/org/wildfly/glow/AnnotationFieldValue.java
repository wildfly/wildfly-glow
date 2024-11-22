/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.glow;

/**
 *
 * @author jdenise
 */
public class AnnotationFieldValue {

    private final String annotation;
    private final String fieldName;
    private final String fieldValue;
    private final Layer layer;

    public AnnotationFieldValue(String annotation, String fieldName, String fieldValue, Layer layer) {
        this.annotation = annotation;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        this.layer = layer;
    }

    /**
     * @return the annotation
     */
    public String getAnnotation() {
        return annotation;
    }

    /**
     * @return the fieldName
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * @return the fieldValue
     */
    public String getFieldValue() {
        return fieldValue;
    }

    /**
     * @return the layer
     */
    public Layer getLayer() {
        return layer;
    }
}
