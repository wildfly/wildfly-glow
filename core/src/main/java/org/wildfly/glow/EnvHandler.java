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

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author jdenise
 */
public class EnvHandler {

    public static Set<Env> retrieveEnv(URI uri) throws MalformedURLException, IOException {
        Set<Env> ret = new TreeSet<>();
        try {
            Yaml yaml = new Yaml();

            Map<String, Object> map = yaml.load(uri.toURL().openStream());
            List<Map<String, Object>> lst = (List<Map<String, Object>>) map.get("envs");
            for (Map<String, Object> env : lst) {
                String name = (String) env.get("name");
                Boolean required = (Boolean) env.get("required");
                Boolean buildTime = (Boolean) env.get("build-time");
                String description = (String) env.get("description");
                ret.add(new Env(name,description, buildTime, required));
            }
        } catch (IOException ex) {
            System.err.println("Error accessing configuration in " + uri);
        }
        return ret;
    }
}
