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

package org.wildfly.glow.rules.test.classrule.nested;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

/**
 * Tests that a 'recursive' class layer rule picks up class usage in both the
 * root and child packages.
 * {@link org.wildfly.glow.rules.test.classrule.ClassTestCase} does more thorough
 * testing of types of classes used (arrays, generics etc.)
 */
public class NestedClassTestCase extends AbstractLayerMetaDataTestCase {
    @Test
    public void testNestedClassExtends() {
        testSingleClassWar(NestedClassExtendsUsage.class);
    }

    @Test
    public void testNestedClassField() {
        testSingleClassWar(NestedClassFieldUsage.class);
    }

    @Test
    public void testNestedClassConstructorParameter() {
        testSingleClassWar(NestedClassConstructorParameterUsage.class);
    }

    @Test
    public void testNestedClassConstructorException() {
        testSingleClassWar(NestedClassConstructorExceptionUsage.class);
    }

    @Test
    public void testNestedClassConstructorExceptionCatch() {
        testSingleClassWar(NestedClassConstructorExceptionCatchUsage.class);
    }

    @Test
    public void testNestedClassConstructorBody() {
        testSingleClassWar(NestedClassConstructorBodyUsage.class);
    }

    @Test
    public void testNestedClassConstructorBodyObject() {
        testSingleClassWar(NestedClassConstructorBodyObjectUsage.class);
    }


    @Test
    public void testNestedClassMethodParameter() {
        testSingleClassWar(NestedClassMethodParameterUsage.class);
    }

    @Test
    public void testNestedClassMethodReturnType() {
        testSingleClassWar(NestedClassMethodReturnTypeUsage.class);
    }

    @Test
    public void testNestedClassMethodException() {
        testSingleClassWar(NestedClassMethodExceptionUsage.class);
    }

    @Test
    public void testNestedClassMethodExceptionCatch() {
        testSingleClassWar(NestedClassMethodExceptionCatchUsage.class);
    }

    @Test
    public void testNestedClassMethodBody() {
        testSingleClassWar(NestedClassMethodBodyUsage.class);
    }

    @Test
    public void testNestedClassMethodBodyObject() {
        testSingleClassWar(NestedClassMethodBodyObjectUsage.class);
    }

    @Test
    public void testNestedChildClassExtends() {
        testSingleClassWar(NestedChildClassExtendsUsage.class);
    }

    @Test
    public void testNestedChildClassField() {
        testSingleClassWar(NestedChildClassFieldUsage.class);
    }

    @Test
    public void testNestedChildClassConstructorParameter() {
        testSingleClassWar(NestedChildClassConstructorParameterUsage.class);
    }

    @Test
    public void testNestedChildClassConstructorException() {
        testSingleClassWar(NestedChildClassConstructorExceptionUsage.class);
    }

    @Test
    public void testNestedChildClassConstructorExceptionCatch() {
        testSingleClassWar(NestedChildClassConstructorExceptionCatchUsage.class);
    }

    @Test
    public void testNestedChildClassConstructorBody() {
        testSingleClassWar(NestedChildClassConstructorBodyUsage.class);
    }

    @Test
    public void testNestedChildClassConstructorBodyObject() {
        testSingleClassWar(NestedChildClassConstructorBodyObjectUsage.class);
    }


    @Test
    public void testNestedChildClassMethodParameter() {
        testSingleClassWar(NestedChildClassMethodParameterUsage.class);
    }

    @Test
    public void testNestedChildClassMethodReturnType() {
        testSingleClassWar(NestedChildClassMethodReturnTypeUsage.class);
    }

    @Test
    public void testNestedChildClassMethodException() {
        testSingleClassWar(NestedChildClassMethodExceptionUsage.class);
    }

    @Test
    public void testNestedChildClassMethodExceptionCatch() {
        testSingleClassWar(NestedChildClassMethodExceptionCatchUsage.class);
    }

    @Test
    public void testNestedChildClassMethodBody() {
        testSingleClassWar(NestedChildClassMethodBodyUsage.class);
    }

    @Test
    public void testNestedChildClassMethodBodyObject() {
        testSingleClassWar(NestedChildClassMethodBodyObjectUsage.class);
    }

    @Override
    protected void testSingleClassWar(Class<?> clazz) {
        super.testSingleClassWar(clazz, "class-nested");
    }
}