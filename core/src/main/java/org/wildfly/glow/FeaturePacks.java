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
    private static final String SPACES = "spaces/spaces.yaml";
    private static final String PROVISIONING_FILE_RADICAL = "/provisioning-";
    private static final String TECH_PREVIEW = "/tech-preview/";

    public static final String URL_PROPERTY = "wildfly-glow-galleon-feature-packs-url";

    public static Path getFeaturePacks(String version, String context, boolean techPreview) throws Exception {
        return getFeaturePacks(Space.DEFAULT, version, context, techPreview);
    }

    public static Path getFeaturePacks(Space space, String version, String context, boolean techPreview) throws Exception {
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
            try (InputStream in = new URL(rootURL + version + (techPreview ? TECH_PREVIEW : "") + PROVISIONING_FILE_RADICAL + context + ".xml").openStream()) {
                Files.copy(in, p,
                        StandardCopyOption.REPLACE_EXISTING);
            }
            p.toFile().deleteOnExit();
            return p;
        } catch (Exception ex) {
            throw new Exception("Exception occured while retrieving known Galleon feature-packs for version " + version + ". Cause: " + ex.getLocalizedMessage());
        }
    }

    public static String getFeaturePacksURL() throws Exception {
        return getFeaturePacksURL(Space.DEFAULT);
    }

    public static String getFeaturePacksURL(Space space) throws Exception {
        String rootURL = Utils.getConfigEntry(URL_PROPERTY);
        if (rootURL == null) {
            throw new Exception("No " + URL_PROPERTY + " entry found");
        }
        if (!rootURL.endsWith("/")) {
            rootURL = rootURL + "/";
        }
        if (Space.DEFAULT != space) {
           rootURL = rootURL + "/spaces/"+space.getName()+"/";
        }
        return rootURL;
    }

    public static Set<String> getAllVersions() throws Exception {
        String rootURL = getFeaturePacksURL();
        Set<String> set = new TreeSet<>();
        Yaml yaml = new Yaml();
        Map<String, String> map = yaml.load(new URI(rootURL + VERSIONS).toURL().openStream());
        for(String v : Arrays.asList(map.get("versions").split(","))) {
            set.add(v.trim());
        }
        return set;
    }

    public static Set<String> getAllVersions(String spaceName) throws Exception {
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

    public static List<Space> getAllSpaces() throws Exception {
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

    public static Space getSpace(String spaceName) throws Exception {
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

    public static String getLatestVersion() throws Exception {
        String rootURL = getFeaturePacksURL();
        Yaml yaml = new Yaml();
        Map<String, String> map = yaml.load(new URI(rootURL + VERSIONS).toURL().openStream());
        return map.get("latest");
    }
}
