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
package org.wildfly.glow.rules.test.addon.select.family;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.ScanArguments;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.error.IdentifiedError;
import org.wildfly.glow.maven.MavenResolver;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

public class SelectFamilyTestCase extends AbstractLayerMetaDataTestCase {

    @Test
    public void testError() throws Exception {
        manualCheck();
        Set<String> layers = new HashSet<>();
        layers.add("select-family-consumer");
        ScanArguments.Builder argumentsBuilder = Arguments.scanBuilder().setJndiLayers(layers);
        Arguments arguments = argumentsBuilder.build();
        try (ScanResults scanResults = GlowSession.scan(MavenResolver.newMavenResolver(), arguments, GlowMessageWriter.DEFAULT)) {
            List<IdentifiedError> errors = scanResults.getErrorSession().getErrors();
            Assert.assertEquals(1, errors.size());
            IdentifiedError error = errors.get(0);
            Assert.assertEquals("missing-add-on", error.getId());
        }
    }
    
    @Test
    public void testValid() throws Exception {
        manualCheck();
        Set<String> layers = new HashSet<>();
        layers.add("select-family-consumer");
        Set<String> addOns = new HashSet<>();
        addOns.add("add-on1");
        ScanArguments.Builder argumentsBuilder = Arguments.scanBuilder().setJndiLayers(layers).setUserEnabledAddOns(addOns);
        Arguments arguments = argumentsBuilder.build();
        try (ScanResults scanResults = GlowSession.scan(MavenResolver.newMavenResolver(), arguments, GlowMessageWriter.DEFAULT)) {
            List<IdentifiedError> errors = scanResults.getErrorSession().getErrors();
            Assert.assertEquals(0, errors.size());
            Set<String> foundLayers = scanResults.getDiscoveredLayers().stream().map(l -> l.getName()).collect(Collectors.toSet());
            Assert.assertEquals(2, foundLayers.size());
            Assert.assertTrue(foundLayers.toString(), foundLayers.contains("select-family-consumer"));
            Assert.assertTrue(foundLayers.toString(),foundLayers.contains("f1-add-on"));
            
        }
    }
    
    @Test
    public void testOnlyOneError() throws Exception {
        manualCheck();
        Set<String> layers = new HashSet<>();
        layers.add("select-only-one-family-consumer");
        ScanArguments.Builder argumentsBuilder = Arguments.scanBuilder().setJndiLayers(layers);
        Arguments arguments = argumentsBuilder.build();
        try (ScanResults scanResults = GlowSession.scan(MavenResolver.newMavenResolver(), arguments, GlowMessageWriter.DEFAULT)) {
            List<IdentifiedError> errors = scanResults.getErrorSession().getErrors();
            Assert.assertEquals(1, errors.size());
            IdentifiedError error = errors.get(0);
            Assert.assertEquals("missing-add-on", error.getId());
        }
    }
    
    @Test
    public void testOnlyOneValid() throws Exception {
        manualCheck();
        Set<String> layers = new HashSet<>();
        layers.add("select-only-one-family-consumer");
        Set<String> addOns = new HashSet<>();
        addOns.add("f2-add-on");
        ScanArguments.Builder argumentsBuilder = Arguments.scanBuilder().setJndiLayers(layers).setUserEnabledAddOns(addOns);
        Arguments arguments = argumentsBuilder.build();
        try (ScanResults scanResults = GlowSession.scan(MavenResolver.newMavenResolver(), arguments, GlowMessageWriter.DEFAULT)) {
            List<IdentifiedError> errors = scanResults.getErrorSession().getErrors();
            Assert.assertEquals(0, errors.size());
            Set<String> foundLayers = scanResults.getDiscoveredLayers().stream().map(l -> l.getName()).collect(Collectors.toSet());
            Assert.assertEquals(2, foundLayers.size());
            Assert.assertTrue(foundLayers.toString(), foundLayers.contains("select-only-one-family-consumer"));
            Assert.assertTrue(foundLayers.toString(),foundLayers.contains("f2-add-on"));
            
        }
    }
    
    @Test
    public void testOnlyOneErrorTooMuch() throws Exception {
        manualCheck();
        Set<String> layers = new HashSet<>();
        layers.add("select-only-one-family-consumer");
        Set<String> addOns = new HashSet<>();
        addOns.add("f2-add-on");
        addOns.add("f2-add-on2");
        ScanArguments.Builder argumentsBuilder = Arguments.scanBuilder().setJndiLayers(layers).setUserEnabledAddOns(addOns);
        Arguments arguments = argumentsBuilder.build();
        try (ScanResults scanResults = GlowSession.scan(MavenResolver.newMavenResolver(), arguments, GlowMessageWriter.DEFAULT)) {
            List<IdentifiedError> errors = scanResults.getErrorSession().getErrors();
            Assert.assertEquals(1, errors.size());
            IdentifiedError error = errors.get(0);
            Assert.assertEquals("too-many-add-on", error.getId());
        }
    }
}
