/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.glow;


public class DataSourceDefinitionInfo {
    private final String name;
    private final String url;
    private final String className;
    private final Layer driverLayer;
    public DataSourceDefinitionInfo(String name, String url, String className, Layer driverLayer) {
        this.name = name;
        this.url = url;
        this.className = className;
        this.driverLayer = driverLayer;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the url
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return the className
     */
    public String getClassName() {
        return className;
    }

    /**
     * @return the driverLayer
     */
    public Layer getDriverLayer() {
        return driverLayer;
    }
}
