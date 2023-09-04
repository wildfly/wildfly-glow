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

package org.wildfly.glow.test.core.resource.classes;

import org.junit.Assert;
import org.wildfly.glow.error.AmbiguousResourceInjectionError;

import java.util.Arrays;
import java.util.List;

public class ErrorsTestClass implements ResourceInjectionTestClass {

    protected List<ErrorInfo> expectedErrors;

    protected ErrorsTestClass(ErrorInfo...expectedErrors) {
        this.expectedErrors = Arrays.asList(expectedErrors);
    }

    @Override
    public void check(List<AmbiguousResourceInjectionError> errors) {
        Assert.assertEquals(expectedErrors.size(), errors.size());
        for (int i = 0; i < expectedErrors.size(); i++) {
            AmbiguousResourceInjectionError error = errors.get(i);
            ErrorInfo expected = expectedErrors.get(i);

            Assert.assertEquals(expected.injectionPoint, error.getInfo().getInjectionPoint());
            Assert.assertEquals(expected.jndiName, error.getInfo().getJndiName());
            Assert.assertEquals(expected.resourceClassName, error.getInfo().getResourceClassName());
        }
    }

    protected static class ErrorInfo {
        private final String resourceClassName;
        private final String injectionPoint;
        private final String jndiName;

        public ErrorInfo(String resourceClassName, String injectionPoint, String jndiName) {
            this.resourceClassName = resourceClassName;
            this.injectionPoint = injectionPoint;
            this.jndiName = jndiName;
        }
    }
}
