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

import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author jdenise
 */
public abstract class LayerMetadata {

    public static final String PREFIX = "org.wildfly.rule.";

    public static final String ADD_ON = PREFIX + "add-on";
    public static final String ADD_ON_CARDINALITY = PREFIX + "add-on-cardinality";
    public static final String ADD_ON_DEPENDS_ON = PREFIX + "add-on-depends-on";
    public static final String ADD_ON_DESCRIPTION = PREFIX + "add-on-description";
    public static final String ADD_ON_FIX = PREFIX + "add-on-fix-";
    public static final String ANNOTATIONS = PREFIX + "annotations";
    public static final String BRING_DATASOURCE = PREFIX + "bring-datasource";
    public static final String CLASS = PREFIX + "class";
    public static final String CONFIGURATION = PREFIX + "configuration";
    public static final String EXPECT_ADD_ON_FAMILY = PREFIX + "expect-add-on-family";
    public static final String EXPECTED_FILE = PREFIX + "expected-file";
    public static final String HIDDEN_IF = PREFIX + "hidden-if";
    public static final String INCLUSION_MODE = PREFIX + "inclusion-mode";
    public static final String KIND = PREFIX + "kind";
    public static final String NO_CONFIGURATION_IF = PREFIX + "no-configuration-if";
    public static final String NOT_EXPECTED_FILE = PREFIX + "not-expected-file";
    public static final String PROFILE = PREFIX + "profile-";
    public static final String PROPERTIES_FILE_MATCH = PREFIX + "properties-file-match";
    public static final String XML_PATH = PREFIX + "xml-path";
    private static final Set<String> ALL_RULES = new TreeSet<>();
    private static final Set<String> FULLY_NAMED_RULES = new TreeSet<>();
    private static final Set<String> RULES_WITH_SUFFIX = new TreeSet<>();
    private static final Set<String> CONDITION_RULES = new TreeSet<>();

    static {
        FULLY_NAMED_RULES.add(ADD_ON);
        FULLY_NAMED_RULES.add(ADD_ON_CARDINALITY);
        FULLY_NAMED_RULES.add(ADD_ON_DEPENDS_ON);
        FULLY_NAMED_RULES.add(ADD_ON_DESCRIPTION);

        FULLY_NAMED_RULES.add(ANNOTATIONS);
        FULLY_NAMED_RULES.add(BRING_DATASOURCE);
        FULLY_NAMED_RULES.add(CLASS);
        FULLY_NAMED_RULES.add(CONFIGURATION);
        FULLY_NAMED_RULES.add(EXPECT_ADD_ON_FAMILY);
        FULLY_NAMED_RULES.add(INCLUSION_MODE);
        FULLY_NAMED_RULES.add(KIND);

        RULES_WITH_SUFFIX.add(ADD_ON_FIX);
        RULES_WITH_SUFFIX.add(EXPECTED_FILE);
        RULES_WITH_SUFFIX.add(NOT_EXPECTED_FILE);
        RULES_WITH_SUFFIX.add(PROFILE);
        RULES_WITH_SUFFIX.add(PROPERTIES_FILE_MATCH);
        RULES_WITH_SUFFIX.add(XML_PATH);

        CONDITION_RULES.add(HIDDEN_IF);
        CONDITION_RULES.add(NO_CONFIGURATION_IF);

        ALL_RULES.addAll(FULLY_NAMED_RULES);
        ALL_RULES.addAll(RULES_WITH_SUFFIX);
        ALL_RULES.addAll(CONDITION_RULES);

    }

    public static Set<String> getAllRules() {
        return ALL_RULES;
    }

    public static Set<String> getFullyNamedRules() {
        return FULLY_NAMED_RULES;
    }

    public static Set<String> getRadicalOnlyNamedRules() {
        return RULES_WITH_SUFFIX;
    }

    public static Set<String> getConditionRules() {
        return CONDITION_RULES;
    }

    public static String getRuleClass(String k) {
        if (FULLY_NAMED_RULES.contains(k)) {
            return k;
        }
        for (String c : CONDITION_RULES) {
            if (k.startsWith(c)) {
                return c;
            }
        }
        for (String c : RULES_WITH_SUFFIX) {
            if (k.startsWith(c)) {
                return c;
            }
        }
        return null;
    }
}
