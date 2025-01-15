/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.junit.After;

/**
 * Tests that timer service etc. are considered strongly typed
 */
@DataSourceDefinition(name="java:jboss/datasources/batch-processingDS",
        className="org.h2.jdbcx.JdbcDataSource",
        url="jdbc:h2:mem:batch-processing;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1",
        user="sa",
        password="sa"
)

public class DataSourceDefinitionTestCase {
    private final TestPackager testPackager = new TestPackager();

    @After
    public void reset() {
        testPackager.reset();
    }

    @Test
    public void h2DriverUsage() throws Exception {
        ScanResults scanResults = testPackager.packageTestAsArchiveAndScan(DataSourceDefinitionTestCase.class);
        List<IdentifiedError> errors = scanResults.getErrorSession().getErrors();
        Assert.assertEquals(0, errors.size());

        Set<String> layers = scanResults.getDiscoveredLayers().stream().map(l -> l.getName()).collect(Collectors.toSet());
        Assert.assertTrue(layers.contains("h2-driver"));

    }

}
