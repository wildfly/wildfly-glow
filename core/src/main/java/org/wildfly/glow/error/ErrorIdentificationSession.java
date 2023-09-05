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
package org.wildfly.glow.error;

import org.wildfly.glow.AddOn;
import org.wildfly.glow.ContextLookupInfo;
import org.wildfly.glow.Layer;
import org.wildfly.glow.LayerMapping;
import org.wildfly.glow.ResourceInjectionJndiInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author jdenise
 */
public class ErrorIdentificationSession {

    private final DatasourceErrorIdentification ds = new DatasourceErrorIdentification();
    private final JndiErrorIdentification jndiErrorIdentification = new JndiErrorIdentification();
    private final List<IdentifiedError> set = new ArrayList<>();

    public void addError(IdentifiedError error) {
        set.add(error);
    }

    public void collectErrors(Path rootPath) throws Exception {
        ds.collectErrors(rootPath);
    }

    public void collectEndOfScanErrors(
            boolean verbose,
            Map<String, ResourceInjectionJndiInfo> resourceInjectionInfos,
            Set<ContextLookupInfo> initialContextLookupInfos,
            Set<String> allClasses) {
        jndiErrorIdentification.collectErrors(verbose, resourceInjectionInfos, initialContextLookupInfos, allClasses);
    }

    public void refreshErrors(Set<Layer> allBaseLayers, LayerMapping mapping, Set<AddOn> enabledAddOns) throws Exception {
        ds.refreshErrors(allBaseLayers);
        // We could have an Enabbled addOn
        for (IdentifiedError error : getErrors()) {
            if (!error.isFixed()) {
                Set<AddOn> possibleFixingAddons = mapping.getFixedByAddons().get(error.getId());
                if (possibleFixingAddons != null) {
                    error.getPossibleAddons().addAll(possibleFixingAddons);
                }
            }
        }
    }

    public List<IdentifiedError> getErrors() {
        List<IdentifiedError> ret = new ArrayList<>();
        ret.addAll(set);
        ret.addAll(ds.getErrors());
        ret.addAll(jndiErrorIdentification.getErrors());
        return ret;
    }

    public boolean hasErrors() {
        for(IdentifiedError err : getErrors()) {
            if(!err.isFixed()) {
                return true;
            }
        }
        return false;
    }


}
