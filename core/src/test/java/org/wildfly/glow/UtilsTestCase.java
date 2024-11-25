/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.glow;

import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise
 */
public class UtilsTestCase {
    @Test
    public void testParseAnnotationFields() {
        {
            String str = "type,annotation";
            AnnotatedType type = Utils.parseAnnotatedType(str, null);
            Assert.assertEquals("type", type.getType());
            Assert.assertEquals("annotation", type.getAnnotation());
            Assert.assertEquals(0, type.getFields().size());
        }
        {
            String fields = "type,annotation[foo=bar]";
            Map<String, String> results = Utils.parseAnnotatedType(fields, null).getFields();
            Assert.assertEquals(1, results.size());
            Assert.assertEquals("bar", results.get("foo"));
        }
        {
            String fields = "type,annotation[foo=bar,foo2=bar2]";
            Map<String, String> results = Utils.parseAnnotatedType(fields, null).getFields();
            Assert.assertEquals(2, results.size());
            Assert.assertEquals("bar", results.get("foo"));
            Assert.assertEquals("bar2", results.get("foo2"));
        }
        {
            String fields = "type,annotation[foo=bar*,foo2=\\=b\\,a\\]r2]";
            Map<String, String> results = Utils.parseAnnotatedType(fields, null).getFields();
            Assert.assertEquals(2, results.size());
            Assert.assertEquals("bar.*", results.get("foo"));
            Assert.assertEquals("=b,a]r2", results.get("foo2"));
        }
    }
}
