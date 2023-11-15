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

package org.wildfly.glow.rules.test.multiple;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;
import org.wildfly.glow.test.rules.classes.classrule.multiple.ClassMultipleClass;

/**
 * This class tests that multiple layers defining the same class rule are discovered.
 *
 */
public class MultipleLayersTestCase extends AbstractLayerMetaDataTestCase {

    @Test
    public void testMultipleLayersSameClass() {
        testSingleClassWar(ClassMultipleClass.class);
    }
    
    @Test
    public void testMultipleLayersSameAnnotation() {
        testSingleClassWar(MultipleAnnotationClassUsage.class);
    }

    @Override
    protected void testSingleClassWar(Class<?> clazz) {
        super.testSingleClassWar(clazz, "multiple1", "multiple2");
    }
}
