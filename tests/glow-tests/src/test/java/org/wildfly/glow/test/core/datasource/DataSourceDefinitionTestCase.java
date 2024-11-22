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

package org.wildfly.glow.test.core.datasource;

import jakarta.annotation.sql.DataSourceDefinition;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.error.IdentifiedError;
import org.wildfly.glow.test.core.TestPackager;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Ignore;

/**
 * Tests that timer service etc. are considered strongly typed
 */
@DataSourceDefinition(name="java:jboss/datasources/batch-processingDS",
        className="org.h2.jdbcx.JdbcDataSource",
        url="jdbc:h2:mem:batch-processing;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1",
        user="sa",
        password="sa"
)
// Will un-ignore this test for WildFly 35 Final
@Ignore
public class DataSourceDefinitionTestCase {
    private final TestPackager testPackager = new TestPackager();

    @Test
    public void h2DriverUsage() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(DataSourceDefinitionTestCase.class);
        List<IdentifiedError> errors = scanResults.getErrorSession().getErrors();
        Assert.assertEquals(0, errors.size());

        Set<String> layers = scanResults.getDiscoveredLayers().stream().map(l -> l.getName()).collect(Collectors.toSet());
        Assert.assertTrue(layers.contains("h2-driver"));

    }

}
