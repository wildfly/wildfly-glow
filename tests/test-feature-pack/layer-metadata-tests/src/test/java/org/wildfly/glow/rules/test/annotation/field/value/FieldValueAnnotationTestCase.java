/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.glow.rules.test.annotation.field.value;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

public class FieldValueAnnotationTestCase extends AbstractLayerMetaDataTestCase {

    @Test
    public void testValueAnnotationUsage() {
        testSingleClassWar(FieldValueAnnotationUsage.class, "annotation-field-value");
    }
    
    @Test
    public void testValueAnnotationUsage2() {
        testSingleClassWar(FieldValueAnnotationUsage2.class, "annotation-field-value2");
    }

}
