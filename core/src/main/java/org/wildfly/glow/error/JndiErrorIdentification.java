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

package org.wildfly.glow.error;

import org.wildfly.glow.ContextLookupInfo;
import org.wildfly.glow.Layer;
import org.wildfly.glow.ResourceInjectionJndiInfo;
import org.wildfly.glow.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JndiErrorIdentification {
    public static final String JNDI_LAYERS_ARGUMENT = "--add-layers-for-jndi";
    public static final String JNDI_LAYERS_ARGUMENT_EXPLANATION = "In the case some layers are missing, " +
            "consider adding them manually with the " + JNDI_LAYERS_ARGUMENT + " parameter.";

    public static final String AMBIGUOUS_RESOURCE_INJECTION = "ambiguous-resource-injection";
    public static final String AMBIGUOUS_RESOURCE_INJECTION_DESCRIPTION = "ambiguous resource injection";
    private static final String AMBIGUOUS_RESOURCE_INJECTION_SUGGESTION =
            "A @Resource annotation is weakly typed and can not be used to identify a class " +
                    "from the deployment or from a layer. " + JNDI_LAYERS_ARGUMENT_EXPLANATION  +
            " Alternatively, refactor your application to make them strongly typed";

    public static final String NAMING_CONTEXT_LOOKUP = "naming-context-lookup";
    public static final String NAMING_CONTEXT_LOOKUP_DESCRIPTION = "jakarta.naming.Context or InitialContext lookup";
    private static final String CONTEXT_LOOKUP_SUGGESTION =
            "A jakarta.naming.Context.lookup() call was detected. This can not be used to identify a class " +
                    "from the deployment or from a layer. " + JNDI_LAYERS_ARGUMENT_EXPLANATION;

    static final String ENABLE_VERBOSE = "Enable verbose output to see the locations.";


    private Map<String, Set<IdentifiedError>> errors = new HashMap<>();

    public void collectErrors(
            boolean verbose,
            Map<String, ResourceInjectionJndiInfo> resourceInjectionInfos,
            Set<ContextLookupInfo> initialContextLookupInfos,
            Set<String> allClasses)  {

        for (String resourceInjectionType : resourceInjectionInfos.keySet()) {
            Set<IdentifiedError> errorSet = errors.computeIfAbsent(AMBIGUOUS_RESOURCE_INJECTION, s -> new HashSet<>());
            ResourceInjectionJndiInfo info = resourceInjectionInfos.get(resourceInjectionType);
            Set<Layer> resourceInjectionTypeLayers = info.getLayers();
            if (resourceInjectionTypeLayers.isEmpty() || Utils.layersAreBanned(resourceInjectionTypeLayers) || info.getResourceClassName().equals("javax.naming.Context")) {
                // TODO is the Layer.isBanned() check correct?
                if (!allClasses.contains(resourceInjectionType)) {
                    errorSet.add(AmbiguousResourceInjectionError.create(verbose, AMBIGUOUS_RESOURCE_INJECTION, AMBIGUOUS_RESOURCE_INJECTION_DESCRIPTION, info));
                }
            }
        }

        for (ContextLookupInfo contextLookupInfo : initialContextLookupInfos) {
            Set<IdentifiedError> errorSet = errors.computeIfAbsent(NAMING_CONTEXT_LOOKUP, s -> new HashSet<>());
            errorSet.add(NamingContextLookupError.create(verbose, NAMING_CONTEXT_LOOKUP, NAMING_CONTEXT_LOOKUP_DESCRIPTION, contextLookupInfo));
        }

    }

    List<IdentifiedError> getErrors() {
        List<IdentifiedError> ret = new ArrayList<>();
        for (Set<IdentifiedError> err : errors.values()) {
            ret.addAll(err);
        }
        return ret;
    }
}
