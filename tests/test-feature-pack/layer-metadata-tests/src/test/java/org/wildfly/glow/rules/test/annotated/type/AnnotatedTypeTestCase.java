/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.glow.rules.test.annotated.type;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

public class AnnotatedTypeTestCase extends AbstractLayerMetaDataTestCase {

    @Test
    public void testValueAnnotationUsage() {
        testSingleClassWar(AnnotatedTypeUsage.class, "annotated-type");
    }

}
