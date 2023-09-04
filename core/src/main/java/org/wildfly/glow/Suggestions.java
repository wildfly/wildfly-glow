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

import java.util.Map;
import java.util.Set;

/**
 *
 * @author jdenise
 */
public class Suggestions {

    private final Map<Layer, Set<Env>> suggestedConfigurations;
    private final Map<Layer, Set<Env>> stronglySuggestedConfigurations;
    private final Set<AddOn> possibleAddOns;
    private final Set<String> possibleProfiles;

    Suggestions(Map<Layer, Set<Env>> suggestedConfigurations,
            Map<Layer, Set<Env>> stronglySuggestedConfigurations,
            Set<AddOn> possibleAddOns,
            Set<String> possibleProfiles) {
        this.suggestedConfigurations = suggestedConfigurations;
        this.stronglySuggestedConfigurations = stronglySuggestedConfigurations;
        this.possibleAddOns = possibleAddOns;
        this.possibleProfiles = possibleProfiles;
    }

    /**
     * @return the suggestedConfigurations
     */
    public Map<Layer, Set<Env>> getSuggestedConfigurations() {
        return suggestedConfigurations;
    }

    /**
     * @return the stronglySuggestedConfigurations
     */
    public Map<Layer, Set<Env>> getStronglySuggestedConfigurations() {
        return stronglySuggestedConfigurations;
    }

    /**
     * @return the possibleAddOns
     */
    public Set<AddOn> getPossibleAddOns() {
        return possibleAddOns;
    }

    /**
     * @return the possibleProfiles
     */
    public Set<String> getPossibleProfiles() {
        return possibleProfiles;
    }
}
