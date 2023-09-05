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

package org.wildfly.glow.rules.test.annotations.sibling;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;


public class SiblingAnnotationsTestCase extends AbstractLayerMetaDataTestCase {
    @Test
    public void testSiblingNestedAnnotationClassUsage() {
        testSingleClassWar(SiblingNestedAnnotationClassUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingNestedAnnotationConstructorUsage() {
        testSingleClassWar(SiblingNestedAnnotationConstructorUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingNestedAnnotationConstructorParameterUsage() {
        testSingleClassWar(SiblingNestedAnnotationConstructorParameterUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingNestedAnnotationFieldUsage() {
        testSingleClassWar(SiblingNestedAnnotationFieldUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingNestedAnnotationMethodUsage() {
        testSingleClassWar(SiblingNestedAnnotationMethodUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingNestedAnnotationMethodParameterUsage() {
        testSingleClassWar(SiblingNestedAnnotationMethodParameterUsage.class, "annotations-sibling");
    }



    @Test
    public void testSiblingNestedChildAnnotationClassUsage() {
        testSingleClassWar(SiblingNestedChildAnnotationClassUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingNestedChildAnnotationConstructorUsage() {
        testSingleClassWar(SiblingNestedChildAnnotationConstructorUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingNestedChildAnnotationConstructorParameterUsage() {
        testSingleClassWar(SiblingNestedChildAnnotationConstructorParameterUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingNestedChildAnnotationFieldUsage() {
        testSingleClassWar(SiblingNestedChildAnnotationFieldUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingNestedChildAnnotationMethodUsage() {
        testSingleClassWar(SiblingNestedChildAnnotationMethodUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingNestedChildAnnotationMethodParameterUsage() {
        testSingleClassWar(SiblingNestedChildAnnotationMethodParameterUsage.class, "annotations-sibling");
    }


    @Test
    public void testSiblingUnnestedAnnotationClassUsage() {
        testSingleClassWar(SiblingUnnestedAnnotationClassUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingUnnestedAnnotationConstructorUsage() {
        testSingleClassWar(SiblingUnnestedAnnotationConstructorUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingUnnestedAnnotationConstructorParameterUsage() {
        testSingleClassWar(SiblingUnnestedAnnotationConstructorParameterUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingUnnestedAnnotationFieldUsage() {
        testSingleClassWar(SiblingUnnestedAnnotationFieldUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingUnnestedAnnotationMethodUsage() {
        testSingleClassWar(SiblingUnnestedAnnotationMethodUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingUnnestedAnnotationMethodParameterUsage() {
        testSingleClassWar(SiblingUnnestedAnnotationMethodParameterUsage.class, "annotations-sibling");
    }

    @Test
    public void testSiblingUnnestedChildAnnotationClassUsage() {
        testSingleClassWar(SiblingUnnestedChildAnnotationClassUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildAnnotationConstructorUsage() {
        testSingleClassWar(SiblingUnnestedChildAnnotationConstructorUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildAnnotationConstructorParameterUsage() {
        testSingleClassWar(SiblingUnnestedChildAnnotationConstructorParameterUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildAnnotationFieldUsage() {
        testSingleClassWar(SiblingUnnestedChildAnnotationFieldUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildAnnotationMethodUsage() {
        testSingleClassWar(SiblingUnnestedChildAnnotationMethodUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildAnnotationMethodParameterUsage() {
        testSingleClassWar(SiblingUnnestedChildAnnotationMethodParameterUsage.class);
    }

}
