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

package org.wildfly.glow.rules.test.classrule.unnested;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

/**
 * Tests that a 'recursive' class layer rule picks up class usage in both the
 * root and child packages.
 * {@link org.wildfly.glow.rules.test.classrule.ClassTestCase} does more thorough
 * testing of types of classes used (arrays, generics etc.)
 */
public class UnnestedClassTestCase extends AbstractLayerMetaDataTestCase {
    @Test
    public void testUnnestedClassExtends() {
        testSingleClassWar(UnnestedClassExtendsUsage.class);
    }

    @Test
    public void testUnnestedClassField() {
        testSingleClassWar(UnnestedClassFieldUsage.class);
    }

    @Test
    public void testUnnestedClassConstructorParameter() {
        testSingleClassWar(UnnestedClassConstructorParameterUsage.class);
    }

    @Test
    public void testUnnestedClassConstructorException() {
        testSingleClassWar(UnnestedClassConstructorExceptionUsage.class);
    }

    @Test
    public void testUnnestedClassConstructorExceptionCatch() {
        testSingleClassWar(UnnestedClassConstructorExceptionCatchUsage.class);
    }

    @Test
    public void testUnnestedClassConstructorBody() {
        testSingleClassWar(UnnestedClassConstructorBodyUsage.class);
    }

    @Test
    public void testUnnestedClassConstructorBodyObject() {
        testSingleClassWar(UnnestedClassConstructorBodyObjectUsage.class);
    }


    @Test
    public void testUnnestedClassMethodParameter() {
        testSingleClassWar(UnnestedClassMethodParameterUsage.class);
    }

    @Test
    public void testUnnestedClassMethodReturnType() {
        testSingleClassWar(UnnestedClassMethodReturnTypeUsage.class);
    }

    @Test
    public void testUnnestedClassMethodException() {
        testSingleClassWar(UnnestedClassMethodExceptionUsage.class);
    }

    @Test
    public void testUnnestedClassMethodExceptionCatch() {
        testSingleClassWar(UnnestedClassMethodExceptionCatchUsage.class);
    }

    @Test
    public void testUnnestedClassMethodBody() {
        testSingleClassWar(UnnestedClassMethodBodyUsage.class);
    }

    @Test
    public void testUnnestedClassMethodBodyObject() {
        testSingleClassWar(UnnestedClassMethodBodyObjectUsage.class);
    }

    @Test
    public void testUnnestedChildClassExtends() {
        testSingleClassWarNoLayers(UnnestedChildClassExtendsUsage.class);
    }

    @Test
    public void testUnnestedChildClassField() {
        testSingleClassWarNoLayers(UnnestedChildClassFieldUsage.class);
    }

    @Test
    public void testUnnestedChildClassConstructorParameter() {
        testSingleClassWarNoLayers(UnnestedChildClassConstructorParameterUsage.class);
    }

    @Test
    public void testUnnestedChildClassConstructorException() {
        testSingleClassWarNoLayers(UnnestedChildClassConstructorExceptionUsage.class);
    }

    @Test
    public void testUnnestedChildClassConstructorExceptionCatch() {
        testSingleClassWarNoLayers(UnnestedChildClassConstructorExceptionCatchUsage.class);
    }

    @Test
    public void testUnnestedChildClassConstructorBody() {
        testSingleClassWarNoLayers(UnnestedChildClassConstructorBodyUsage.class);
    }

    @Test
    public void testUnnestedChildClassConstructorBodyObject() {
        testSingleClassWarNoLayers(UnnestedChildClassConstructorBodyObjectUsage.class);
    }


    @Test
    public void testUnnestedChildClassMethodParameter() {
        testSingleClassWarNoLayers(UnnestedChildClassMethodParameterUsage.class);
    }

    @Test
    public void testUnnestedChildClassMethodReturnType() {
        testSingleClassWarNoLayers(UnnestedChildClassMethodReturnTypeUsage.class);
    }

    @Test
    public void testUnnestedChildClassMethodException() {
        testSingleClassWarNoLayers(UnnestedChildClassMethodExceptionUsage.class);
    }

    @Test
    public void testUnnestedChildClassMethodExceptionCatch() {
        testSingleClassWarNoLayers(UnnestedChildClassMethodExceptionCatchUsage.class);
    }

    @Test
    public void testUnnestedChildClassMethodBody() {
        testSingleClassWarNoLayers(UnnestedChildClassMethodBodyUsage.class);
    }

    @Test
    public void testUnnestedChildClassMethodBodyObject() {
        testSingleClassWarNoLayers(UnnestedChildClassMethodBodyObjectUsage.class);
    }

    @Override
    protected void testSingleClassWar(Class<?> clazz) {
        super.testSingleClassWar(clazz, "class-unnested");
    }

    private void testSingleClassWarNoLayers(Class<?> clazz) {
        super.testSingleClassWar(clazz);
    }
}