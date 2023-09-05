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

package org.wildfly.glow.test.core.resource.ejb;

import jakarta.annotation.Resource;
import jakarta.ejb.MessageDrivenContext;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.error.AmbiguousResourceInjectionError;
import org.wildfly.glow.error.IdentifiedError;
import org.wildfly.glow.test.core.TestPackager;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tests that timer service etc. are considered strongly typed
 */
public class EJBResourceTestCase {
    @Resource
    MessageDrivenContext messageDrivenContext;

    private final TestPackager testPackager = new TestPackager();

    @Test
    public void EjbResourcesNotWeaklyTyped() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(EJBResourceTestCase.class);
        List<IdentifiedError> errors = scanResults.getErrorSession().getErrors();
        List<AmbiguousResourceInjectionError> ambiguousResourceInjectionErrors = errors.stream()
                .filter(e -> e instanceof AmbiguousResourceInjectionError)
                .map(e -> (AmbiguousResourceInjectionError)e)
                .collect(Collectors.toList());
        // Make sure there are no other errors
        Assert.assertEquals(errors.size(), ambiguousResourceInjectionErrors.size());

        Assert.assertEquals(0, errors.size());

        Set<String> layers = scanResults.getDiscoveredLayers().stream().map(l -> l.getName()).collect(Collectors.toSet());
        Assert.assertTrue(layers.contains("ejb"));

    }

}
