/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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
package org.wildfly.glow.deployment.openshift.api;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise
 */
public class OpenShiftSupportTestCase {

    @Test
    public void testAppName() {
        {
            String name = "foo";
            Assert.assertEquals(name, OpenShiftSupport.generateValidName(name));
        }
        {
            String name = "web-1.0";
            Assert.assertEquals("web-1-0", OpenShiftSupport.generateValidName(name));
        }
        {
            String name = "990-web-1.0";
            Assert.assertEquals("app-990-web-1-0", OpenShiftSupport.generateValidName(name));
        }
        {
            String name = "-web-1.0-foo";
            Assert.assertEquals("app--web-1-0-foo", OpenShiftSupport.generateValidName(name));
        }
        {
            String name = "web$970*";
            Assert.assertEquals("web-9700", OpenShiftSupport.generateValidName(name));
        }
        {
            String name = "web_970_456_foo";
            Assert.assertEquals("web-970-456-foo", OpenShiftSupport.generateValidName(name));
        }
        {
            String name = "ROOT";
            Assert.assertEquals("root", OpenShiftSupport.generateValidName(name));
        }
        {
            String name = "0123456789012345678901234567890123456789012345678901234567890123456789";
            Assert.assertEquals("app-01234567890123456789012345678901234567890123456789012345678", OpenShiftSupport.generateValidName(name));
        }
    }
}
