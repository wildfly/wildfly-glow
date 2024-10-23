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
package org.wildfly.glow;

/**
 *
 * @author jdenise
 */
public class SelectionMode {
    private final Layer targetLayer;
    private final String family;
    private final int maxNumber;

    public SelectionMode(Layer targetLayer, String family, int maxNumber) {
        this.targetLayer = targetLayer;
        this.family = family;
        this.maxNumber = maxNumber;
    }

    public boolean isUnBounded() {
        return maxNumber < 0;
    }
    /**
     * @return the targetLayer
     */
    public Layer getTargetLayer() {
        return targetLayer;
    }

    /**
     * @return the family
     */
    public String getFamily() {
        return family;
    }

    /**
     * @return the maxNumber
     */
    public int getMaxNumber() {
        return maxNumber;
    }
}
