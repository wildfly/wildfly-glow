/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2024 Red Hat, Inc., and individual contributors
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
package org.wildfly.glow.cli.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author jdenise
 */
public class Utils {

    public static void setSystemProperties(Set<String> systemProperties) throws Exception {
        if (!systemProperties.isEmpty()) {
            for (String p : systemProperties) {
                if (p.startsWith("-D")) {
                    int i = p.indexOf("=");
                    String propName;
                    String value = "";
                    if (i > 0) {
                        propName = p.substring(2, i);
                        value = p.substring(i + 1);
                    } else {
                        propName = p.substring(2);
                    }
                    System.setProperty(propName, value);
                } else {
                    throw new Exception("Invalid system property " + p + ". A property must start with -D");
                }
            }
        }
    }

    public static Map<String, String> handleOpenShiftEnvFile(Path envFile) throws Exception {
        Map<String, String> extraEnv = new HashMap<>();
        if (!Files.exists(envFile)) {
            throw new Exception(envFile + " file doesn't exist");
        }
        for (String l : Files.readAllLines(envFile)) {
            l = l.trim();
            if (!l.isEmpty() && !l.startsWith("#")) {
                int i = l.indexOf("=");
                if (i < 0 || i == l.length() - 1) {
                    throw new Exception("Invalid environment variable " + l + " in " + envFile);
                }
                extraEnv.put(l.substring(0, i), l.substring(i + 1));
            }
        }
        return extraEnv;
    }

    public static Map<String, String> readConfigFile(Path file) throws Exception {
        if (file == null) {
            return Collections.emptyMap();
        }
        if(!Files.exists(file)) {
            throw new Exception("File " + file + " doesn't exist.");
        }
        Map<String, String> map = new HashMap<>();
        for (String l : Files.readAllLines(file)) {
            l = l.trim();
            if (!l.startsWith("#")) {
                String[] split = l.split("=");
                map.put(split[0].trim(), split[1].trim());
            }
        }
        return map;
    }

    public static void addAddOnsFromConfig(Map<String, String> config, Set<String> addOns) throws Exception {
        String val = config.get("add-ons");
        if(val != null) {
            String[] addOnsArray = val.split(",");
            for(String addOn : addOnsArray) {
                addOns.add(addOn.trim());
            }
        }
    }
    public static String getServerVersionFromConfig(Map<String, String> config) throws Exception {
        return config.get("server-version");
    }
    public static void addDisableDeployersFromConfig(Map<String, String> config, Set<String> addOns) throws Exception {
        String val = config.get("disable-deployers");
        if(val != null) {
            String[] addOnsArray = val.split(",");
            for(String addOn : addOnsArray) {
                addOns.add(addOn.trim());
            }
        }
    }
    public static void addEnableDeployersFromConfig(Map<String, String> config, Set<String> addOns) throws Exception {
        String val = config.get("enable-deployers");
        if(val != null) {
            String[] addOnsArray = val.split(",");
            for(String addOn : addOnsArray) {
                addOns.add(addOn.trim());
            }
        }
    }

    public static Boolean getHaFromConfig(Map<String, String> config) {
        String val = config.get("ha");
        return Boolean.valueOf(val);
    }
}
