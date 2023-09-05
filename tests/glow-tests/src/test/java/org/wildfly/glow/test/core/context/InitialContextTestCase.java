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

package org.wildfly.glow.test.core.context;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.error.NamingContextLookupError;
import org.wildfly.glow.error.IdentifiedError;
import org.wildfly.glow.test.core.TestPackager;
import org.wildfly.glow.test.core.context.classes.ContextLookupFromConstructor;
import org.wildfly.glow.test.core.context.classes.ContextLookupFromConstructorLambda;
import org.wildfly.glow.test.core.context.classes.ContextLookupFromInnerClassMethod;
import org.wildfly.glow.test.core.context.classes.ContextLookupFromMethod;
import org.wildfly.glow.test.core.context.classes.ContextLookupFromMethodLambda;
import org.wildfly.glow.test.core.context.classes.InitialContextLookupFromConstructor;
import org.wildfly.glow.test.core.context.classes.InitialContextLookupFromConstructorLambda;
import org.wildfly.glow.test.core.context.classes.InitialContextLookupFromInnerClassMethod;
import org.wildfly.glow.test.core.context.classes.InitialContextLookupFromMethod;
import org.wildfly.glow.test.core.context.classes.InitialContextLookupFromMethodLambda;

import java.util.List;
import java.util.stream.Collectors;

public class InitialContextTestCase {
    TestPackager testPackager = new TestPackager();

    List<NamingContextLookupError> getContextLookupErrors(ScanResults scanResults) {
        List<IdentifiedError> errors = scanResults.getErrorSession().getErrors();
        List<NamingContextLookupError> namingContextLookupErrors = errors.stream()
                .filter(e -> e instanceof NamingContextLookupError)
                .map(e -> (NamingContextLookupError)e)
                .collect(Collectors.toList());
        // Make sure there are no other errors
        Assert.assertEquals(errors.size(), namingContextLookupErrors.size());
        return namingContextLookupErrors;
    }

    NamingContextLookupError assertSingleContextLookupError(ScanResults scanResults, String method) {
        List<NamingContextLookupError> errors = getContextLookupErrors(scanResults);
        Assert.assertEquals(1, errors.size());
        NamingContextLookupError error = errors.get(0);
        Assert.assertEquals(method, error.getContextLookupInfo().getMethod());
        return error;
    }

    @After
    public void reset() {
        testPackager.reset();
    }

    @Test
    public void testInitialContextLookupFromMethod() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(InitialContextLookupFromMethod.class);
        assertSingleContextLookupError(scanResults, InitialContextLookupFromMethod.class.getName() + ".findByName()");
    }

    @Test
    public void testContextLookupFromMethod() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(ContextLookupFromMethod.class);
        assertSingleContextLookupError(scanResults, ContextLookupFromMethod.class.getName() + ".lookupByName()");
    }

    @Test
    public void testInitialContextLookupFromConstructor() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(InitialContextLookupFromConstructor.class);
        assertSingleContextLookupError(scanResults, InitialContextLookupFromConstructor.class.getName() + ".<init>()");
    }

    @Test
    public void testContextLookupFromConstructor() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(ContextLookupFromConstructor.class);
        assertSingleContextLookupError(scanResults, ContextLookupFromConstructor.class.getName() + ".<init>()");
    }

    @Test
    public void testInitialContextLookupFromInnerClassMethod() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(InitialContextLookupFromInnerClassMethod.InnerInitial.class);
        assertSingleContextLookupError(scanResults, InitialContextLookupFromInnerClassMethod.InnerInitial.class.getName() + ".lookup()");
    }

    @Test
    public void testContextLookupFromInnerClassMethod() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(ContextLookupFromInnerClassMethod.InnerContext.class);
        assertSingleContextLookupError(scanResults, ContextLookupFromInnerClassMethod.InnerContext.class.getName() + ".find()");
    }

    @Test
    public void testInitialContextLookupFromMethodLambda() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(InitialContextLookupFromMethodLambda.class);
        assertSingleContextLookupError(scanResults, InitialContextLookupFromMethodLambda.class.getName() + ".lambda$findInitialContextLambda$0()");
    }

    @Test
    public void testContextLookupFromMethodLambda() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(ContextLookupFromMethodLambda.class);
        assertSingleContextLookupError(scanResults, ContextLookupFromMethodLambda.class.getName() + ".lambda$findContextLambda$0()");
    }

    @Test
    public void testInitialContextLookupFromConstructorLambda() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(InitialContextLookupFromConstructorLambda.class);
        assertSingleContextLookupError(scanResults, InitialContextLookupFromConstructorLambda.class.getName() + ".lambda$new$0()");
    }

    @Test
    public void testContextLookupFromConstructorLambda() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(ContextLookupFromConstructorLambda.class);
        assertSingleContextLookupError(scanResults, ContextLookupFromConstructorLambda.class.getName() + ".lambda$new$0()");
    }

}
