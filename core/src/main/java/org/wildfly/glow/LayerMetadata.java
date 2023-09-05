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
public interface LayerMetadata {
    String PREFIX = "org.wildfly.rule.";

    String ADD_ON = PREFIX + "add-on";
    String ADD_ON_CARDINALITY = PREFIX + "add-on-cardinality";
    String ADD_ON_DEPENDS_ON = PREFIX + "add-on-depends-on";
    String ADD_ON_DESCRIPTION = PREFIX + "add-on-description";
    String ADD_ON_FIX = PREFIX + "add-on-fix-";
    String ANNOTATIONS = PREFIX + "annotations";
    String BRING_DATASOURCE = PREFIX + "bring-datasource";
    String CLASS = PREFIX + "class";
    String CONFIGURATION = PREFIX + "configuration";
    String EXPECT_ADD_ON_FAMILY = PREFIX + "expect-add-on-family";
    String EXPECTED_FILE = PREFIX + "expected-file";
    String HIDDEN_IF = PREFIX + "hidden-if";
    String INCLUSION_MODE = PREFIX + "inclusion-mode";
    String KIND = PREFIX + "kind";
    String NO_CONFIGURATION_IF = PREFIX + "no-configuration-if";
    String NOT_EXPECTED_FILE = PREFIX + "not-expected-file";
    String PROFILE = PREFIX + "profile-";
    String PROPERTIES_FILE_MATCH = PREFIX + "properties-file-match";
    String XML_PATH = PREFIX + "xml-path";
}
