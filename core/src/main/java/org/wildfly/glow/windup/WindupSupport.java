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
package org.wildfly.glow.windup;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.wildfly.glow.Layer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author jdenise
 */
public class WindupSupport {

    public static Set<Layer> getLayers(Map<String, Layer> all, Path mappingFile, Path jsonAnalysis) throws IOException {
        String json = new String(Files.readAllBytes(jsonAnalysis), StandardCharsets.UTF_8);
        String mapping = new String(Files.readAllBytes(mappingFile), StandardCharsets.UTF_8);
        ObjectMapper objectMapper = new ObjectMapper();
        WindupMapping wdMapping = objectMapper.readValue(mapping, WindupMapping.class);
        List<Map> maps = objectMapper.readValue(json, List.class);
        Set<String> tags = new HashSet<>();
        for (Map<String, Map<String, Object>> obj : maps) {
            List<Map<String, String>> lst = (List<Map<String, String>>) obj.get("technologyTags");
            for (Map<String, String> t : lst) {
                tags.add(t.get("name"));
            }
        }
        //System.out.println("Analysis tags\n" + tags);
        Map<String, List<String>> assoc = wdMapping.getMapping();
        Set<Layer> layers = new TreeSet<>();
        for (String t : tags) {
            List<String> l = assoc.get(t);
            for (String s : l) {
                Layer ll = all.get(s);
                layers.add(ll);
            }
        }
        return layers;
    }

    public static Path getWindupMapping() {
        Path path = null;
        String p = System.getProperty("windup");
        if (p != null) {
            path = Paths.get(p);
        }
        return path;
    }
}
