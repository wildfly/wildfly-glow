/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.glow;

/**
 * A layer expects add-ons from a given family to be explicitly selected.
 * @author jdenise
 */
public class AddOnSelectionMode {
    private final Layer targetLayer;
    private final String family;
    private final int maxNumber;

    /**
     * AddOnSelectionMode constructor.
     * @param targetLayer The layer that contains the rule.
     * @param family The family that the targetlayer expect add-ons from.
     * @param maxNumber The max number of expected add-ons. Can be 1 or -1 (unbounded).
     */
    public AddOnSelectionMode(Layer targetLayer, String family, int maxNumber) throws Exception {
        this.targetLayer = targetLayer;
        this.family = family;
        if (maxNumber != 1 && maxNumber != -1) {
            throw new Exception("The max number of add-on selection rule can only be 1 or -1 (unbounded)");
        }
        this.maxNumber = maxNumber;
    }

    /**
     * Is the expected number of add-ons unbounded.
     * @return true if the number of expected add-ons is unbounded.
     */
    public boolean isUnBounded() {
        return maxNumber < 0;
    }
    /**
     * Is the expected number of add-ons exactly one.
     * @return true if the number of expected add-ons is one.
     */
    public boolean isExactlyOne() {
        return maxNumber == 1;
    }

    /**
     * @return the maxNumber of add-ons a user can select. -1 means unbounded.
     */
    public boolean isOne() {
        return maxNumber == 1;
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
}
