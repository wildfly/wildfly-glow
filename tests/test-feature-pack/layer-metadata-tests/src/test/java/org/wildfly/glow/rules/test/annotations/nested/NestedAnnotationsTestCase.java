/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package org.wildfly.glow.rules.test.annotations.nested;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

public class NestedAnnotationsTestCase extends AbstractLayerMetaDataTestCase {

    @Test
    public void testNestedAnnotationClassUsage() {
        testSingleClassWar(NestedAnnotationClassUsage.class, "annotations-nested");
    }

    @Test
    public void testNestedAnnotationConstructorUsage() {
        testSingleClassWar(NestedAnnotationConstructorUsage.class, "annotations-nested");
    }

    @Test
    public void testNestedAnnotationConstructorParameterUsage() {
        testSingleClassWar(NestedAnnotationConstructorParameterUsage.class, "annotations-nested");
    }

    @Test
    public void testNestedAnnotationFieldUsage() {
        testSingleClassWar(NestedAnnotationFieldUsage.class, "annotations-nested");
    }

    @Test
    public void testNestedAnnotationMethodUsage() {
        testSingleClassWar(NestedAnnotationMethodUsage.class, "annotations-nested");
    }

    @Test
    public void testNestedAnnotationMethodParameterUsage() {
        testSingleClassWar(NestedAnnotationMethodParameterUsage.class, "annotations-nested");
    }



    @Test
    public void testNestedChildAnnotationClassUsage() {
        testSingleClassWar(NestedChildAnnotationClassUsage.class, "annotations-nested");
    }

    @Test
    public void testNestedChildAnnotationConstructorUsage() {
        testSingleClassWar(NestedChildAnnotationConstructorUsage.class, "annotations-nested");
    }

    @Test
    public void testNestedChildAnnotationConstructorParameterUsage() {
        testSingleClassWar(NestedChildAnnotationConstructorParameterUsage.class, "annotations-nested");
    }

    @Test
    public void testNestedChildAnnotationFieldUsage() {
        testSingleClassWar(NestedChildAnnotationFieldUsage.class, "annotations-nested");
    }

    @Test
    public void testNestedChildAnnotationMethodUsage() {
        testSingleClassWar(NestedChildAnnotationMethodUsage.class, "annotations-nested");
    }

    @Test
    public void testNestedChildAnnotationMethodParameterUsage() {
        testSingleClassWar(NestedChildAnnotationMethodParameterUsage.class, "annotations-nested");
    }



}
