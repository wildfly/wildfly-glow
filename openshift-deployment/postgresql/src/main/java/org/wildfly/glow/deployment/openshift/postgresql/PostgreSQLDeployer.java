/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.glow.deployment.openshift.postgresql;

import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.glow.deployment.openshift.api.AbstractDatabaseDeployer;

/**
 *
 * @author jdenise
 */
public class PostgreSQLDeployer extends AbstractDatabaseDeployer {

    private static final String POSTGRESQL_NAME = "postgresql";
    private static final int POSTGRESQL_SERVICE_PORT = 5432;
    private static final String POSTGRESQL_SERVICE_HOST = POSTGRESQL_NAME;
    private static final String ENV_RADICAL = "POSTGRESQL";
    private static final String IMAGE = "registry.redhat.io/rhel8/postgresql-15";

    public PostgreSQLDeployer() {
        super(POSTGRESQL_NAME,
                IMAGE,
                ENV_RADICAL,
                ENV_RADICAL,
                POSTGRESQL_SERVICE_HOST,
                POSTGRESQL_SERVICE_PORT);
    }

    @Override
    protected String computeBuildTimeValue(String name, MavenRepoManager mvnResolver) throws Exception {
        MavenArtifact ma = new MavenArtifact();
        ma.setGroupId("org.postgresql");
        ma.setArtifactId("postgresql");
        ma.setVersionRange("[1.0,)");
        ma.setExtension("jar");
        String vers = mvnResolver.getLatestVersion(ma);
        return vers;
    }
}
