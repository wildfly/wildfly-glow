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

package org.wildfly.glow.rules.test.annotations.unnested;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;


public class UnnestedAnnotationsTestCase extends AbstractLayerMetaDataTestCase {

    @Test
    public void testUnnestedAnnotationClassUsage() {
        testSingleClassWar(UnnestedAnnotationClassUsage.class, "annotations-unnested");
    }

    @Test
    public void testUnnestedAnnotationConstructorUsage() {
        testSingleClassWar(UnnestedAnnotationConstructorUsage.class, "annotations-unnested");
    }

    @Test
    public void testUnnestedAnnotationConstructorParameterUsage() {
        testSingleClassWar(UnnestedAnnotationConstructorParameterUsage.class, "annotations-unnested");
    }

    @Test
    public void testUnnestedAnnotationFieldUsage() {
        testSingleClassWar(UnnestedAnnotationFieldUsage.class, "annotations-unnested");
    }

    @Test
    public void testUnnestedAnnotationMethodUsage() {
        testSingleClassWar(UnnestedAnnotationMethodUsage.class, "annotations-unnested");
    }

    @Test
    public void testUnnestedAnnotationMethodParameterUsage() {
        testSingleClassWar(UnnestedAnnotationMethodParameterUsage.class, "annotations-unnested");
    }

    @Test
    public void testUnnestedChildAnnotationClassUsage() {
        testSingleClassWar(UnnestedChildAnnotationClassUsage.class);
    }

    @Test
    public void testUnnestedChildAnnotationConstructorUsage() {
        testSingleClassWar(UnnestedChildAnnotationConstructorUsage.class);
    }

    @Test
    public void testUnnestedChildAnnotationConstructorParameterUsage() {
        testSingleClassWar(UnnestedChildAnnotationConstructorParameterUsage.class);
    }

    @Test
    public void testUnnestedChildAnnotationFieldUsage() {
        testSingleClassWar(UnnestedChildAnnotationFieldUsage.class);
    }

    @Test
    public void testUnnestedChildAnnotationMethodUsage() {
        testSingleClassWar(UnnestedChildAnnotationMethodUsage.class);
    }

    @Test
    public void testUnnestedChildAnnotationMethodParameterUsage() {
        testSingleClassWar(UnnestedChildAnnotationMethodParameterUsage.class);
    }



}
