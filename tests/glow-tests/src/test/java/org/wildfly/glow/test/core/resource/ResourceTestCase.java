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

package org.wildfly.glow.test.core.resource;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.error.AmbiguousResourceInjectionError;
import org.wildfly.glow.error.IdentifiedError;
import org.wildfly.glow.test.core.TestPackager;
import org.wildfly.glow.test.core.resource.classes.ResourceInjectionTestClass;
import org.wildfly.glow.test.core.resource.classes.ResourceOnClassNoType;
import org.wildfly.glow.test.core.resource.classes.ResourceOnClassWithResourceTypeFromLayer;
import org.wildfly.glow.test.core.resource.classes.ResourceOnClassWithTypeContext;
import org.wildfly.glow.test.core.resource.classes.ResourceOnFieldContextNoType;
import org.wildfly.glow.test.core.resource.classes.ResourceOnFieldFromLayerNoType;
import org.wildfly.glow.test.core.resource.classes.ResourceOnFieldObjectNoType;
import org.wildfly.glow.test.core.resource.classes.ResourceOnFieldObjectTypeContext;
import org.wildfly.glow.test.core.resource.classes.ResourceOnFieldObjectWithTypeFromLayer;
import org.wildfly.glow.test.core.resource.classes.ResourceOnSetterContextNoType;
import org.wildfly.glow.test.core.resource.classes.ResourceOnSetterFromLayerNoType;
import org.wildfly.glow.test.core.resource.classes.ResourceOnSetterObjectNoType;
import org.wildfly.glow.test.core.resource.classes.ResourceOnSetterObjectTypeContext;
import org.wildfly.glow.test.core.resource.classes.ResourceOnSetterWithTypeFromLayer;
import org.wildfly.glow.test.core.resource.classes.ResourceWithH2JndiName;
import org.wildfly.glow.test.core.resource.classes.ResourceWithTypeH2JndiName;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ResourceTestCase {

    TestPackager testPackager = new TestPackager();

    Set<String> getDiscoveredLayers() {
        return testPackager.getScanResults().getDiscoveredLayers().stream().map((layer)-> layer.getName()).collect(Collectors.toSet());
    }

    List<AmbiguousResourceInjectionError> getAmbiguousResourceInjectionErrors(ScanResults scanResults) {
        List<IdentifiedError> errors = scanResults.getErrorSession().getErrors();
        List<AmbiguousResourceInjectionError> ambiguousResourceInjectionErrors = errors.stream()
                .filter(e -> e instanceof AmbiguousResourceInjectionError)
                .map(e -> (AmbiguousResourceInjectionError)e)
                .collect(Collectors.toList());
        // Make sure there are no other errors
        Assert.assertEquals(errors.size(), ambiguousResourceInjectionErrors.size());
        return ambiguousResourceInjectionErrors;
    }

    @After
    public void reset() {
        testPackager.reset();
    }

    @Test
    public void testResourceOnClassNoType() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnClassNoType();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

    @Test
    public void testResourceOnClassWithResourceTypeFromLayer() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnClassWithResourceTypeFromLayer();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

    @Test
    public void testResourceOnClassWithTypeContext() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnClassWithTypeContext();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

    @Test
    public void testResourceOnFieldFromLayerNoType() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnFieldFromLayerNoType();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

   @Test
    public void testResourceOnFieldObjectNoType() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnFieldObjectNoType();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

    @Test
    public void testResourceOnFieldContextNoType() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnFieldContextNoType();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

    @Test
    public void testResourceOnFieldObjectTypeContext() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnFieldObjectTypeContext();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }


    @Test
    public void testResourceOnFieldObjectWithTypeFromLayer() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnFieldObjectWithTypeFromLayer();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

    @Test
    public void testResourceOnSetterFromLayerNoType() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnSetterFromLayerNoType();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

    @Test
    public void testResourceOnSetterContextNoType() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnSetterContextNoType();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

    @Test
    public void testResourceOnSetterObjectNoType() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnSetterObjectNoType();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

    @Test
    public void testResourceOnSetterObjectTypeContext() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnSetterObjectTypeContext();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

    @Test
    public void testResourceOnSetterWithTypeFromLayer() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceOnSetterWithTypeFromLayer();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
    }

    @Test
    public void testResourceWithH2JndiName() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceWithH2JndiName();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
        Assert.assertTrue(getDiscoveredLayers().contains("h2-datasource"));
    }

    @Test
    public void testResourceWithTypeH2JndiName() throws Exception {
        ResourceInjectionTestClass testClass = new ResourceWithTypeH2JndiName();
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(testClass);
        testClass.check(getAmbiguousResourceInjectionErrors(scanResults));
        Set<String> layers = getDiscoveredLayers();
        Assert.assertTrue(layers.toString(), layers.contains("h2-datasource"));
        Assert.assertTrue(layers.toString(), layers.contains("transactions"));
    }
}
