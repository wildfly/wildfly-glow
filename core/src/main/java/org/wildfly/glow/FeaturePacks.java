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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author jdenise
 */
public class FeaturePacks {

    private static final String VERSIONS = "versions.yaml";
    private static final String VARIANTS = "variants.json";
    private static final String SPACES = "spaces/spaces.yaml";
    private static final String PROVISIONING_FILE_RADICAL = "/provisioning-";

    public static final String URL_PROPERTY = "wildfly-glow-galleon-feature-packs-url";

    private final URI rootURI;

    public FeaturePacks(URI rootURI) {
        this.rootURI = rootURI;
    }

    public Path getFeaturePacks(String version, String context, String variant) throws Exception {
        return getFeaturePacks(Space.DEFAULT, version, context, variant);
    }

    public Path getFeaturePacks(Space space, String version, String context, String variant) throws Exception {
        try {
            String rootURL = getFeaturePacksURL(space);
            Yaml yaml = new Yaml();
            Map<String, String> map = yaml.load(new URI(getFeaturePacksURL() + VERSIONS).toURL().openStream());
            if (version == null) {
                version = map.get("latest");
            } else {
                String[] versions = map.get("versions").split(",");
                boolean found = false;
                for (String v : versions) {
                    if (v.trim().equals(version)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new Exception("The server version " + version + " doesn't exist.");
                }
            }
            Path p = Files.createTempFile("glow-provisioning-", context);
            String checkedVariant = getVariantDirectory(version, variant);
            try (InputStream in = new URL(rootURL + version + (checkedVariant != null ? "/"+checkedVariant+"/" : "") + PROVISIONING_FILE_RADICAL + context + ".xml").openStream()) {
                Files.copy(in, p,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            p.toFile().deleteOnExit();
            return p;
        } catch (Exception ex) {
            throw new Exception("Exception occured while retrieving known Galleon feature-packs for version " + version + ". Cause: " + ex.getLocalizedMessage());
        }
    }

    private String getFeaturePacksURL() throws Exception {
        return getFeaturePacksURL(Space.DEFAULT);
    }

    private String getFeaturePacksURL(Space space) throws Exception {
        String rootURL = rootURI.toString();
        if (!rootURL.endsWith("/")) {
            rootURL = rootURL + "/";
        }
        if (Space.DEFAULT != space) {
           rootURL = rootURL + "/spaces/"+space.getName()+"/";
        }
        return rootURL;
    }

    public Set<String> getAllVersions() throws Exception {
        return getAllVersions(Space.DEFAULT.getName());
    }

    public Set<String> getAllVersions(String spaceName) throws Exception {
        Space space = getSpace(spaceName);
        String rootURL = getFeaturePacksURL(space);
        Set<String> set = new TreeSet<>();
        Yaml yaml = new Yaml();
        Map<String, String> map = yaml.load(new URI(rootURL + VERSIONS).toURL().openStream());
        for (String v : Arrays.asList(map.get("versions").split(","))) {
            set.add(v.trim());
        }
        return set;
    }

    public List<Space> getAllSpaces() throws Exception {
        String rootURL = getFeaturePacksURL();
        List<Space> lst = new ArrayList<>();
        Yaml yaml = new Yaml();
        Map<String, List<Map<String, String>>> map = yaml.load(new URI(rootURL + SPACES).toURL().openStream());
        List<Map<String, String>> spaces = map.get("spaces");
        for(Map<String, String> space : spaces) {
            lst.add(new Space(space.get("name"), space.get("description")));
        }
        return lst;
    }

    public List<Variant> getAllVariants(String wildflyVersion) throws Exception {
        String rootURL = getFeaturePacksURL();
        List<Variant> lst = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode variantNode = mapper.readTree(new URI(rootURL + "/" + wildflyVersion + "/" + VARIANTS).toURL());
        ArrayNode nodes = (ArrayNode) variantNode.get("variants");
        for (JsonNode node : nodes) {
            String name = node.get("name").asText();
            if(name.equals("default")) {
                continue;
            }
            JsonNode directory = node.get("directory");
            lst.add(new Variant(name, node.get("description").asText(), directory == null ? null : directory.asText()));
        }
        return lst;
    }

    public String getVariantDirectory(String wildflyVersion, String variant) throws Exception {
        if (variant == null) {
            return null;
        }
        String rootURL = getFeaturePacksURL();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode variantNode = mapper.readTree(new URI(rootURL + "/" + wildflyVersion + "/" + VARIANTS).toURL());
        ArrayNode nodes = (ArrayNode) variantNode.get("variants");
        String ret = null;
        for (JsonNode node : nodes) {
            String name = node.get("name").asText();
            if (name.equals(variant)) {
                JsonNode directory = node.get("directory");
                if (directory != null) {
                    ret = directory.asText();
                } else {
                    ret = name;
                }
                break;
            }
        }
        if (ret == null) {
            throw new Exception("Unknown variant " + variant + ". Variants are " + getAllVariants(wildflyVersion));
        }
        return ret;
    }

    public Space getSpace(String spaceName) throws Exception {
        if (Space.DEFAULT.getName().equals(spaceName)) {
            return Space.DEFAULT;
        }
        String rootURL = getFeaturePacksURL();
        List<Space> lst = new ArrayList<>();
        Yaml yaml = new Yaml();
        Map<String, List<Map<String, String>>> map = yaml.load(new URI(rootURL + SPACES).toURL().openStream());
        List<Map<String, String>> spaces = map.get("spaces");
        for(Map<String, String> space : spaces) {
            if(space.get("name").equals(spaceName)) {
                return new Space(space.get("name"), space.get("description"));
            }
        }
        List<Space> knownSpaces = getAllSpaces();
        StringBuilder builder = new StringBuilder();
        for(Space space : knownSpaces) {
            builder.append(space.getName() + " ");
        }
        throw new Exception("Space " + spaceName + " doesn't exist. Known spaces are: " + builder.toString());
    }

    public String getLatestVersion() throws Exception {
        String rootURL = getFeaturePacksURL();
        Yaml yaml = new Yaml();
        Map<String, String> map = yaml.load(new URI(rootURL + VERSIONS).toURL().openStream());
        return map.get("latest");
    }
}
