/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.glow.rules.test.annotated.type;

import org.wildfly.glow.test.rules.classes.annotation.field.value.FieldValue;


public class AnnotatedTypeUsage {
    @FieldValue(prop = "coco")
    private Object foo;
}
