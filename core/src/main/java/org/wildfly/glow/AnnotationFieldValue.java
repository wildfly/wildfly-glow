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
