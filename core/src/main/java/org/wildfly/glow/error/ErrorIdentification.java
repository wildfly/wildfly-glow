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

import org.wildfly.glow.Layer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.wildfly.glow.Env;

/**
 *
 * @author jdenise
 */
public interface ErrorIdentification {
    void collectErrors(Path rootPath) throws Exception;
    Map<Layer, Set<Env>> refreshErrors(Set<Layer> allBaseLayers) throws Exception;
    List<IdentifiedError> getErrors();
}
