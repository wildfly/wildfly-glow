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
package org.wildfly.glow.deployment.openshift.mariadb;

import org.wildfly.glow.deployment.openshift.api.AbstractDatabaseDeployer;

/**
 *
 * @author jdenise
 */
public class MariaDBDeployer extends AbstractDatabaseDeployer {

    private static final String MARIADB_NAME = "mariadb";
    private static final int MARIADB_SERVICE_PORT = 3306;
    private static final String MARIADB_SERVICE_HOST = MARIADB_NAME;
    private static final String ENV_RADICAL = "MARIADB";
    private static final String DB_ENV_RADICAL = "MYSQL";
    private static final String IMAGE = "registry.redhat.io/rhel8/mariadb-103";

    public MariaDBDeployer() {
        super(MARIADB_NAME,
                IMAGE,
                DB_ENV_RADICAL,
                ENV_RADICAL,
                MARIADB_SERVICE_HOST,
                MARIADB_SERVICE_PORT);
    }
}
