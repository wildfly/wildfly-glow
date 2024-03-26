/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
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
package org.wildfly.glow;

/**
 *
 * @author jdenise
 */
public class Env implements Comparable<Env> {
    private final String name;
    private final String description;
    private final Boolean runtime;
    private final Boolean required;
    private final Boolean isProperty;
   public Env(String name, String description, Boolean buildTime, Boolean required, Boolean property) {
       this.name= name;
       this.description = description;
       this.runtime = buildTime == null ? Boolean.TRUE : !buildTime;
       this.required = required == null ? Boolean.FALSE : required;
       this.isProperty = property == null ? Boolean.FALSE : property;
   }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the runtime
     */
    public Boolean isRuntime() {
        return runtime;
    }

    /**
     * @return the property
     */
    public Boolean isProperty() {
        return isProperty;
    }

    /**
     * @return the required
     */
    public Boolean isRequired() {
        return required;
    }

    @Override
    public int compareTo(Env env) {
        return name.compareTo(env.getName());
    }
}
