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

package org.wildfly.glow;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.wildfly.channel.ChannelSession;

public class BaseArgumentsBuilder {
    protected Set<String> executionProfiles = Collections.emptySet();
    protected Set<String> userEnabledAddOns = Collections.emptySet();
    protected List<Path> binaries;
    protected Path provisioningXML;
    protected OutputFormat output;
    protected String executionContext;
    protected boolean suggest;
    protected String version;
    protected String configName;
    protected Set<String> layersForJndi = Collections.emptySet();
    protected boolean verbose;
    protected boolean techPreview;

    protected Set<String> excludeJarsFromScan = Collections.emptySet();
    protected String packageStability;
    protected String configStability;
    protected boolean isCli;
    protected ChannelSession channelSession;

    protected BaseArgumentsBuilder() {

    }

    public Arguments build() {
        Set<Pattern> excludeJarsFromScan = this.excludeJarsFromScan.stream()
                .map(v -> Pattern.compile(Utils.escapePattern(v)))
                .collect(Collectors.toSet());

        return new Arguments(
                executionContext,
                executionProfiles,
                userEnabledAddOns,
                binaries,
                provisioningXML,
                output,
                suggest,
                version,
                configName,
                layersForJndi,
                verbose,
                techPreview,
                excludeJarsFromScan,
                configStability,
                packageStability,
                isCli,
                channelSession);
    }
}
