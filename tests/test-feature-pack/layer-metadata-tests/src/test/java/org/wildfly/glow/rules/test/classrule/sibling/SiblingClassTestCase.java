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

package org.wildfly.glow.rules.test.classrule.sibling;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

/**
 * Tests that a 'recursive' class layer rule picks up class usage in both the
 * root and child packages.
 * {@link org.wildfly.glow.rules.test.classrule.ClassTestCase} does more thorough
 * testing of types of classes used (arrays, generics etc.)
 */
public class SiblingClassTestCase extends AbstractLayerMetaDataTestCase {
    @Test
    public void testSiblingNestedClassExtends() {
        testSingleClassNestedWar(SiblingNestedClassExtendsUsage.class);
    }

    @Test
    public void testSiblingNestedClassField() {
        testSingleClassNestedWar(SiblingNestedClassFieldUsage.class);
    }

    @Test
    public void testSiblingNestedClassConstructorParameter() {
        testSingleClassNestedWar(SiblingNestedClassConstructorParameterUsage.class);
    }

    @Test
    public void testSiblingNestedClassConstructorException() {
        testSingleClassNestedWar(SiblingNestedClassConstructorExceptionUsage.class);
    }

    @Test
    public void testSiblingNestedClassConstructorExceptionCatch() {
        testSingleClassNestedWar(SiblingNestedClassConstructorExceptionCatchUsage.class);
    }

    @Test
    public void testSiblingNestedClassConstructorBody() {
        testSingleClassNestedWar(SiblingNestedClassConstructorBodyUsage.class);
    }

    @Test
    public void testSiblingNestedClassConstructorBodyObject() {
        testSingleClassNestedWar(SiblingNestedClassConstructorBodyObjectUsage.class);
    }


    @Test
    public void testSiblingNestedClassMethodParameter() {
        testSingleClassNestedWar(SiblingNestedClassMethodParameterUsage.class);
    }

    @Test
    public void testSiblingNestedClassMethodReturnType() {
        testSingleClassNestedWar(SiblingNestedClassMethodReturnTypeUsage.class);
    }

    @Test
    public void testSiblingNestedClassMethodException() {
        testSingleClassNestedWar(SiblingNestedClassMethodExceptionUsage.class);
    }

    @Test
    public void testSiblingNestedClassMethodExceptionCatch() {
        testSingleClassNestedWar(SiblingNestedClassMethodExceptionCatchUsage.class);
    }

    @Test
    public void testSiblingNestedClassMethodBody() {
        testSingleClassNestedWar(SiblingNestedClassMethodBodyUsage.class);
    }

    @Test
    public void testSiblingNestedClassMethodBodyObject() {
        testSingleClassNestedWar(SiblingNestedClassMethodBodyObjectUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassExtends() {
        testSingleClassNestedWar(SiblingNestedChildClassExtendsUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassField() {
        testSingleClassNestedWar(SiblingNestedChildClassFieldUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassConstructorParameter() {
        testSingleClassNestedWar(SiblingNestedChildClassConstructorParameterUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassConstructorException() {
        testSingleClassNestedWar(SiblingNestedChildClassConstructorExceptionUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassConstructorExceptionCatch() {
        testSingleClassNestedWar(SiblingNestedChildClassConstructorExceptionCatchUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassConstructorBody() {
        testSingleClassNestedWar(SiblingNestedChildClassConstructorBodyUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassConstructorBodyObject() {
        testSingleClassNestedWar(SiblingNestedChildClassConstructorBodyObjectUsage.class);
    }


    @Test
    public void testSiblingNestedChildClassMethodParameter() {
        testSingleClassNestedWar(SiblingNestedChildClassMethodParameterUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassMethodReturnType() {
        testSingleClassNestedWar(SiblingNestedChildClassMethodReturnTypeUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassMethodException() {
        testSingleClassNestedWar(SiblingNestedChildClassMethodExceptionUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassMethodExceptionCatch() {
        testSingleClassNestedWar(SiblingNestedChildClassMethodExceptionCatchUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassMethodBody() {
        testSingleClassNestedWar(SiblingNestedChildClassMethodBodyUsage.class);
    }

    @Test
    public void testSiblingNestedChildClassMethodBodyObject() {
        testSingleClassNestedWar(SiblingNestedChildClassMethodBodyObjectUsage.class);
    }

    protected void testSingleClassNestedWar(Class<?> clazz) {
        super.testSingleClassWar(clazz, "class-sibling");
    }

    @Test
    public void testSiblingUnnestedClassExtends() {
        testSingleClassUnnestedWar(SiblingUnnestedClassExtendsUsage.class);
    }

    @Test
    public void testSiblingUnnestedClassField() {
        testSingleClassUnnestedWar(SiblingUnnestedClassFieldUsage.class);
    }

    @Test
    public void testSiblingUnnestedClassConstructorParameter() {
        testSingleClassUnnestedWar(SiblingUnnestedClassConstructorParameterUsage.class);
    }

    @Test
    public void testSiblingUnnestedClassConstructorException() {
        testSingleClassUnnestedWar(SiblingUnnestedClassConstructorExceptionUsage.class);
    }

    @Test
    public void testSiblingUnnestedClassConstructorExceptionCatch() {
        testSingleClassUnnestedWar(SiblingUnnestedClassConstructorExceptionCatchUsage.class);
    }

    @Test
    public void testSiblingUnnestedClassConstructorBody() {
        testSingleClassUnnestedWar(SiblingUnnestedClassConstructorBodyUsage.class);
    }

    @Test
    public void testSiblingUnnestedClassConstructorBodyObject() {
        testSingleClassUnnestedWar(SiblingUnnestedClassConstructorBodyObjectUsage.class);
    }


    @Test
    public void testSiblingUnnestedClassMethodParameter() {
        testSingleClassUnnestedWar(SiblingUnnestedClassMethodParameterUsage.class);
    }

    @Test
    public void testSiblingUnnestedClassMethodReturnType() {
        testSingleClassUnnestedWar(SiblingUnnestedClassMethodReturnTypeUsage.class);
    }

    @Test
    public void testSiblingUnnestedClassMethodException() {
        testSingleClassUnnestedWar(SiblingUnnestedClassMethodExceptionUsage.class);
    }

    @Test
    public void testSiblingUnnestedClassMethodExceptionCatch() {
        testSingleClassUnnestedWar(SiblingUnnestedClassMethodExceptionCatchUsage.class);
    }

    @Test
    public void testSiblingUnnestedClassMethodBody() {
        testSingleClassUnnestedWar(SiblingUnnestedClassMethodBodyUsage.class);
    }

    @Test
    public void testSiblingUnnestedClassMethodBodyObject() {
        testSingleClassUnnestedWar(SiblingUnnestedClassMethodBodyObjectUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassExtends() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassExtendsUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassField() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassFieldUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassConstructorParameter() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassConstructorParameterUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassConstructorException() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassConstructorExceptionUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassConstructorExceptionCatch() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassConstructorExceptionCatchUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassConstructorBody() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassConstructorBodyUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassConstructorBodyObject() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassConstructorBodyObjectUsage.class);
    }


    @Test
    public void testSiblingUnnestedChildClassMethodParameter() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassMethodParameterUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassMethodReturnType() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassMethodReturnTypeUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassMethodException() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassMethodExceptionUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassMethodExceptionCatch() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassMethodExceptionCatchUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassMethodBody() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassMethodBodyUsage.class);
    }

    @Test
    public void testSiblingUnnestedChildClassMethodBodyObject() {
        testSingleClassWarNoLayers(SiblingUnnestedChildClassMethodBodyObjectUsage.class);
    }

    protected void testSingleClassUnnestedWar(Class<?> clazz) {
        super.testSingleClassWar(clazz, "class-sibling");
    }

    private void testSingleClassWarNoLayers(Class<?> clazz) {
        super.testSingleClassWar(clazz);
    }
}