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

package org.wildfly.glow.rules.test.classrule;

import org.junit.Ignore;
import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

/**
 * This class tests all the combinations I can think of for through testing of
 * class usage.
 * The nested/unnested packages do some cursory checks of recursive and
 * non-recursive package inclusion
 *
 */
public class ClassTestCase extends AbstractLayerMetaDataTestCase {
    @Test
    public void testNestedClassExtends() {
        testSingleClassWar(ClassExtendsUsage.class);
    }

    @Test
    public void testNestedClassField() {
        testSingleClassWar(ClassFieldUsage.class);
    }

    @Test
    public void testNestedClassFieldArray() {
        testSingleClassWar(ClassFieldArrayUsage.class);
    }

    @Test
    public void testNestedClassFieldTypeVariable() {
        testSingleClassWar(ClassFieldTypeVariableUsage.class);
    }

    @Test
    public void testNestedClassFieldGenericType() {
        testSingleClassWar(ClassFieldGenericTypeUsage.class);
    }

    @Test
    public void testNestedClassConstructorParameter() {
        testSingleClassWar(ClassConstructorParameterUsage.class);
    }

    @Test
    public void testNestedClassConstructorParameterArray() {
        testSingleClassWar(ClassConstructorParameterArrayUsage.class);
    }

    @Test
    public void testNestedClassConstructorParameterGenericType() {
        testSingleClassWar(ClassConstructorParameterGenericTypeUsage.class);
    }

    @Test
    public void testNestedClassConstructorParameterTypeVariable() {
        testSingleClassWar(ClassConstructorParameterTypeVariableUsage.class);
    }

    @Test
    public void testNestedClassConstructorException() {
        testSingleClassWar(ClassConstructorExceptionUsage.class);
    }

    @Test
    public void testNestedClassConstructorExceptionCatch() {
        testSingleClassWar(ClassConstructorExceptionCatchUsage.class);
    }

    @Test
    public void testNestedClassConstructorBody() {
        testSingleClassWar(ClassConstructorBodyUsage.class);
    }

    @Test
    public void testNestedClassConstructorBodyObject() {
        testSingleClassWar(ClassConstructorBodyObjectUsage.class);
    }

    @Test
    public void testNestedClassConstructorBodyArray() {
        testSingleClassWar(ClassConstructorBodyArrayUsage.class);
    }

    @Test
    public void testNestedClassConstructorBodyObjectArray() {
        testSingleClassWar(ClassConstructorBodyObjectArrayUsage.class);
    }

    @Test
    public void testNestedClassConstructorBodyTypeVariable() {
        testSingleClassWar(ClassConstructorBodyTypeVariableUsage.class);
    }

    @Ignore("This doesn't seem to show up in the bytecode")
    @Test
    public void testNestedClassConstructorBodyObjectTypeVariable() {
        testSingleClassWar(ClassConstructorBodyObjectTypeVariableUsage.class);
    }

    @Test
    public void testNestedClassConstructorBodyGenericType() {
        testSingleClassWar(ClassConstructorBodyGenericTypeUsage.class);
    }

    @Test
    public void testNestedClassConstructorBodyObjectGenericType() {
        testSingleClassWar(ClassConstructorBodyObjectGenericTypeUsage.class);
    }

    @Test
    public void testNestedClassMethodParameter() {
        testSingleClassWar(ClassMethodParameterUsage.class);
    }

    @Test
    public void testNestedClassMethodParameterArray() {
        testSingleClassWar(ClassMethodParameterArrayUsage.class);
    }

    @Test
    public void testNestedClassMethodParameterTypeVariable() {
        testSingleClassWar(ClassMethodParameterTypeVariableUsage.class);
    }

    @Test
    public void testNestedClassMethodParameterGenericType() {
        testSingleClassWar(ClassMethodParameterGenericTypeUsage.class);
    }

    @Test
    public void testNestedClassMethodReturnType() {
        testSingleClassWar(ClassMethodReturnTypeUsage.class);
    }

    @Test
    public void testNestedClassMethodReturnTypeArray() {
        testSingleClassWar(ClassMethodReturnTypeArrayUsage.class);
    }

    @Test
    public void testNestedClassMethodReturnTypeTypeVariable() {
        testSingleClassWar(ClassMethodReturnTypeTypeVariableUsage.class);
    }

    @Test
    public void testNestedClassMethodReturnTypeGenericType() {
        testSingleClassWar(ClassMethodReturnTypeGenericTypeUsage.class);
    }

    @Test
    public void testNestedClassMethodException() {
        testSingleClassWar(ClassMethodExceptionUsage.class);
    }

    @Test
    public void testNestedClassMethodExceptionCatch() {
        testSingleClassWar(ClassMethodExceptionCatchUsage.class);
    }

    @Test
    public void testNestedClassMethodBody() {
        testSingleClassWar(ClassMethodBodyUsage.class);
    }

    @Test
    public void testNestedClassMethodBodyObject() {
        testSingleClassWar(ClassMethodBodyObjectUsage.class);
    }

    @Test
    public void testNestedClassMethodBodyArray() {
        testSingleClassWar(ClassMethodBodyArrayUsage.class);
    }

    @Test
    public void testNestedClassMethodBodyObjectArray() {
        testSingleClassWar(ClassMethodBodyObjectArrayUsage.class);
    }


    @Test
    public void testNestedClassMethodBodyTypeVariable() {
        testSingleClassWar(ClassMethodBodyTypeVariableUsage.class);
    }

    @Ignore("This doesn't seem to show up in the bytecode")
    @Test
    public void testNestedClassMethodBodyObjectTypeVariable() {
        testSingleClassWar(ClassMethodBodyObjectTypeVariableUsage.class);
    }

    @Test
    public void testNestedClassMethodBodyGenericType() {
        testSingleClassWar(ClassMethodBodyGenericTypeUsage.class);
    }

    @Test
    public void testNestedClassMethodBodyObjectGenericType() {
        testSingleClassWar(ClassMethodBodyObjectGenericTypeUsage.class);
    }

    @Override
    protected void testSingleClassWar(Class<?> clazz) {
        super.testSingleClassWar(clazz, "class");
    }
}
