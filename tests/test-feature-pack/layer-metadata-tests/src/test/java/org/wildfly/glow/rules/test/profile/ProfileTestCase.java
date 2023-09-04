/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.glow.rules.test.profile;

import org.junit.Test;
import org.wildfly.glow.rules.test.AbstractLayerMetaDataTestCase;

import java.nio.file.Path;
import java.util.Collections;

public class ProfileTestCase extends AbstractLayerMetaDataTestCase {

    @Test
    public void testNoProfile() {
        Path p = createArchive();
        checkLayersForArchive(p, "profile-default");
    }

    @Test
    public void testHaProfile() {
        Path p = createArchive();
        checkLayersForArchive(p, sb -> sb.setExecutionProfiles(Collections.singleton("ha")), new ExpectedLayers("profile-default", "profile-ha"));
    }

    @Test(expected = Exception.class)
    public void testBadProfile() {
        Path p = createArchive();
        checkLayersForArchive(p, sb -> sb.setExecutionProfiles(Collections.singleton("bad")), "XXX");
    }

    private Path createArchive() {
        return createArchiveBuilder(ArchiveType.JAR)
                .addFile("profile.txt", "")
                .build();
    }
}
