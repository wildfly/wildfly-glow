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

import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.universe.Channel;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.universe.FeaturePackLocation.ProducerSpec;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wildfly.glow.error.Fix;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.GalleonFeaturePackDescription;
import org.jboss.galleon.api.GalleonFeaturePackLayout;
import org.jboss.galleon.api.GalleonLayer;
import org.jboss.galleon.api.GalleonLayerDependency;
import org.jboss.galleon.api.GalleonProvisioningLayout;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.api.config.GalleonConfigurationWithLayers;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.maven.MavenChannel;

import static org.wildfly.glow.GlowSession.OFFLINE_CONTENT;
import static org.wildfly.glow.GlowSession.OFFLINE_DOCS_DIR;
import static org.wildfly.glow.GlowSession.OFFLINE_FEATURE_PACKS_DIR;
import static org.wildfly.glow.GlowSession.OFFLINE_FEATURE_PACK_DEPENDENCIES_DIR;
import static org.wildfly.glow.GlowSession.OFFLINE_ZIP;

/**
 *
 * @author jdenise
 */
public final class Utils {

    static void applyXPath(Path p, String expression, String expectedValue, Consumer<Layer> consumer, Layer layer) throws Exception {
        if (Files.readAllBytes(p).length == 0) {
            return;
        }
        try (InputStream reader = Files.newInputStream(p)) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(reader);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xPath.evaluate(expression,
                    document, XPathConstants.NODESET);
            if (nodes.getLength() != 0) {
                if (expectedValue != null) {
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Node n = nodes.item(i);
                        String content = n.getTextContent();
                        if (Utils.isPattern(expectedValue)) {
                            expectedValue = Utils.escapePattern(expectedValue);
                        }
                        Pattern pattern = Pattern.compile(expectedValue);
                        if (pattern.matcher(content).matches()) {
                            //System.out.println("RULE " + split[2] + "matched");
                            consumer.accept(layer);
                            LayerMapping.addRule(LayerMapping.RULE.XML_PATH, layer, p.toString() + "==>" + expression + "==" + content);
                            break;
                        }
                    }
                } else {
                    //System.out.println("RULE " + xpathExpression + " matched");
                    consumer.accept(layer);
                    LayerMapping.addRule(LayerMapping.RULE.XML_PATH, layer, p.toString() + "==>" + expression);
                }
            }
        }
    }

    static Set<Layer> getTransitiveDependencies(Map<String, Layer> all, Layer root, Set<Layer> seen) {
        if (seen.contains(root)) {
            return Collections.emptySet();
        }
        seen.add(root);
        Layer l = all.get(root.getName());
        if (l.getDependencies().isEmpty()) {
            return Collections.emptySet();
        }
        Set<Layer> alldeps = new TreeSet<>();
        alldeps.addAll(l.getDependencies());
        for (Layer d : l.getDependencies()) {
            alldeps.addAll(getTransitiveDependencies(all, d, seen));
        }
        return alldeps;
    }

    static void exportOffline(Provisioning provisioning, GalleonProvisioningConfig config, UniverseResolver universeResolver) throws ProvisioningException, IOException {
        Path featurePacksDir = OFFLINE_FEATURE_PACKS_DIR;
        Path featurePackDependenciesDir = OFFLINE_FEATURE_PACK_DEPENDENCIES_DIR;
        Path docsDir = OFFLINE_DOCS_DIR;
        Files.createDirectories(featurePacksDir);
        Files.createDirectories(featurePackDependenciesDir);
        Files.createDirectories(docsDir);
        int index = 0;
        for (GalleonFeaturePackConfig fp : config.getFeaturePackDeps()) {
            FeaturePackLocation fpl = fp.getLocation();
            Channel c = universeResolver.getChannel(fpl);
            Path resolved = c.resolve(fpl);
            Path target = featurePacksDir.resolve(index + "-" + resolved.getFileName().toString());
            if (!Files.exists(target)) {
                Files.copy(resolved, target);
            }
            index += 1;
            GalleonFeaturePackDescription desc = Provisioning.getFeaturePackDescription(resolved);
            for (FPID dep : desc.getDependencies()) {
                Channel depChannel = universeResolver.getChannel(dep.getLocation());
                Path resolvedDep = depChannel.resolve(dep.getLocation());
                Path depTarget = featurePackDependenciesDir.resolve(resolvedDep.getFileName().toString());
                if (!Files.exists(depTarget)) {
                    Files.copy(resolvedDep, depTarget);
                }
            }
        }
        Map<String, Layer> layers = getAllLayers(config, universeResolver, provisioning, new HashMap<>());
        for (Layer l : layers.values()) {
            for (String k : l.getProperties().keySet()) {
                if (LayerMetadata.CONFIGURATION.equals(k)) {
                    String val = l.getProperties().get(k);
                    String[] split = val.split(",");
                    for (int i = 0; i < split.length; i++) {
                        String s = split[i];
                        l.getConfiguration().add(s);
                        try (InputStream in = new URL(s).openStream()) {
                            Files.copy(in, docsDir.resolve(l.getName() + "-glow-configuration-" + i + ".yaml"),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
        }
    }

    public static Map<String, Layer> getAllLayers(GalleonProvisioningConfig config, UniverseResolver universeResolver,
            Provisioning context,
            Map<FPID, Set<ProducerSpec>> fpDependencies)
            throws ProvisioningException, IOException {
        Map<String, Layer> layersMap = new HashMap<>();
        Set<String> autoInjected = new TreeSet<>();
        Set<String> hiddens = new TreeSet<>();
        Map<String, Set<String>> unresolvedDependencies = new LinkedHashMap<>();
        try (GalleonProvisioningLayout layout = context.newProvisioningLayout(config)) {
            for (GalleonFeaturePackLayout fp : layout.getOrderedFeaturePacks()) {
                GalleonConfigurationWithLayers m = fp.loadModel("standalone");
                if (m != null) {
                    autoInjected.addAll(m.getIncludedLayers());
                }
                FPID fpid = toMavenCoordinates(fp.getFPID(), universeResolver);
                for (ConfigId layer : fp.loadLayers()) {
                    GalleonLayer spec = fp.loadLayer(layer.getModel(), layer.getName());
                    String kind = spec.getProperties().get(LayerMetadata.KIND);
                    if (kind != null && "hidden".equals(kind)) {
                        //System.out.println("Layer " + layer.getName() + " is hidden");
                        hiddens.add(layer.getName());
                        continue;
                    }
                    Set<String> dependencies = new TreeSet<>();
                    for (GalleonLayerDependency dep : spec.getLayerDeps()) {
                        dependencies.add(dep.getName());
                    }
                    // Case where a layer is redefined in multiple FP. Add all deps.

                    Layer l = layersMap.get(layer.getName());
                    if (l != null) {
                        Set<String> deps = unresolvedDependencies.get(l.getName());
                        if (deps != null) {
                            deps.addAll(dependencies);
                        }
                        l.getProperties().putAll(spec.getProperties());
                        String redefinedKind = spec.getProperties().get(LayerMetadata.KIND);
                        if (redefinedKind != null && "hidden".equals(redefinedKind)) {
                            layersMap.remove(layer.getName());
                        }
                    } else {
                        if (hiddens.contains(layer.getName())) {
                            // Could be that the layer is redefined in another feature-pack but hidden.
                            continue;
                        }
                        l = new Layer(layer.getName());
                        l.getProperties().putAll(spec.getProperties());
                        unresolvedDependencies.put(l.getName(), dependencies);
                        layersMap.put(layer.getName(), l);
                    }
                    l.getFeaturePacks().add(fpid);
                }
                Set<ProducerSpec> producers = fpDependencies.computeIfAbsent(fpid, (value) -> new HashSet<>());
                for (FPID depFpid : fp.getFeaturePackDeps()) {
                    FPID fpidDep = toMavenCoordinates(depFpid, universeResolver);
                    producers.add(fpidDep.getProducer());
                }

                for (Layer l : layersMap.values()) {
                    if (autoInjected.contains(l.getName())) {
                        l.setIsAutomaticInjection(true);
                    }
                }
            }
        }
        for (String l : unresolvedDependencies.keySet()) {
            Layer layer = layersMap.get(l);
            Set<String> deps = unresolvedDependencies.get(l);
            for (String d : deps) {
                Layer dep = layersMap.get(d);
                layer.getDependencies().add(dep);
            }
        }
        return layersMap;
    }

    static FPID toMavenCoordinates(FPID fpid, UniverseResolver universeResolver) throws ProvisioningException {
        if (!fpid.getLocation().isMavenCoordinates()) {
            Channel c = universeResolver.getChannel(fpid.getLocation());
            if (c instanceof MavenChannel) {
                MavenChannel mc = (MavenChannel) c;
                String grpId = mc.getFeaturePackGroupId();
                String artifactId = mc.getFeaturePackArtifactId();
                fpid = FeaturePackLocation.fromString(grpId + ":" + artifactId + ":" + fpid.getBuild()).getFPID();
            }
        }
        return fpid;
    }

    public static Path getOffLineContent() throws IOException {
        if (Files.exists(OFFLINE_ZIP)) {
            if (Files.exists(OFFLINE_CONTENT)) {
                IoUtils.recursiveDelete(OFFLINE_CONTENT);
            }
            Files.createDirectories(OFFLINE_CONTENT);
            ZipUtils.unzip(OFFLINE_ZIP, OFFLINE_CONTENT);
        }
        return OFFLINE_CONTENT;
    }

    public static GalleonProvisioningConfig buildOfflineProvisioningConfig(GalleonBuilder provider,
            GlowMessageWriter writer) throws Exception {
        FileSystem fs = null;
        GalleonProvisioningConfig config = null;
        Path offlineContent = getOffLineContent();

        if (Files.exists(offlineContent)) {
            GalleonProvisioningConfig.Builder builder = GalleonProvisioningConfig.builder();
            writer.info("Offline content detected");
            List<File> files = Stream.of(OFFLINE_FEATURE_PACKS_DIR.toFile().listFiles())
                    .filter(file -> !file.isDirectory())
                    .sorted()
                    .collect(Collectors.toList());
            for (File f : files) {
                FeaturePackLocation loc = provider.addLocal(f.toPath(), false);
                builder.addFeaturePackDep(loc);
            }
            List<File> depFiles = Stream.of(OFFLINE_FEATURE_PACK_DEPENDENCIES_DIR.toFile().listFiles())
                    .filter(file -> !file.isDirectory())
                    .sorted()
                    .collect(Collectors.toList());
            for (File f : depFiles) {
                provider.addLocal(f.toPath(), false);
            }
            config = builder.build();
        }
        if (fs != null) {
            fs.close();
        }
        return config;
    }

    static void provisionServer(GalleonProvisioningConfig config, Path home, MavenRepoManager resolver, GlowMessageWriter writer) throws ProvisioningException {
        try (Provisioning pm = new GalleonBuilder().addArtifactResolver(resolver).newProvisioningBuilder(config)
                .setInstallationHome(home)
                .setLogTime(false)
                .setMessageWriter(new MessageWriter() {
                    @Override
                    public void verbose(Throwable cause, CharSequence message) {
                        if (writer.isVerbose()) {
                            writer.trace(message);
                        }
                    }

                    @Override
                    public void print(Throwable cause, CharSequence message) {
                        writer.info(message);
                    }

                    @Override
                    public void error(Throwable cause, CharSequence message) {
                        writer.error(message);
                    }

                    @Override
                    public boolean isVerboseEnabled() {
                        return writer.isVerbose();
                    }

                    @Override
                    public void close() throws Exception {
                    }

                })
                .setRecordState(true)
                .build()) {
            ProvisioningTracker.initTrackers(pm, writer);

            pm.provision(config);
        }
    }

    public static Set<String> getXMLElementValues(Path p, String expression) throws Exception {
        if (Files.readAllBytes(p).length == 0) {
            return Collections.emptySet();
        }
        Set<String> values = new TreeSet<>();
        try (InputStream reader = Files.newInputStream(p)) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(reader);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xPath.evaluate(expression,
                    document, XPathConstants.NODESET);
            if (nodes.getLength() != 0) {
                for (int i = 0; i < nodes.getLength(); i++) {
                    Node n = nodes.item(i);
                    String content = n.getTextContent();
                    values.add(content);
                }
            }
        }
        return values;
    }

    public static String escapePattern(String s) {
        if (isPattern(s)) {
            StringBuilder builder = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (c == '.') {
                    builder.append('\\');
                } else {
                    if (c == '*') {
                        builder.append('.');
                    }
                }
                builder.append(c);
            }
            return builder.toString();
        } else {
            return s;
        }
    }

    public static boolean isPattern(String s) {
        return s.contains("*");
    }

    public static LayerMapping buildMapping(Map<String, Layer> layers, Set<String> profiles) {
        LayerMapping mapping = new LayerMapping();
        for (Layer l : layers.values()) {
            for (String k : l.getProperties().keySet()) {
                if (LayerMetadata.ANNOTATIONS.equals(k)) {
                    String val = l.getProperties().get(k);
                    String[] split = val.split(",");
                    for (String s : split) {
                        s = escapePattern(s);
                        Set<Layer> ll = mapping.getAnnotations().computeIfAbsent(s, value -> new HashSet<>());
                        ll.add(l);
                    }
                    continue;
                }
                if (LayerMetadata.CLASS.equals(k)) {
                    String val = l.getProperties().get(k);
                    String[] split = val.split(",");
                    for (String s : split) {
                        s = escapePattern(s);
                        Set<Layer> ll = mapping.getConstantPoolClassInfos().computeIfAbsent(s, value -> new HashSet<>());
                        ll.add(l);
                    }
                    continue;
                }
                if (LayerMetadata.CONFIGURATION.equals(k)) {
                    if (Files.exists(OFFLINE_DOCS_DIR)) {
                        List<File> files = Stream.of(OFFLINE_DOCS_DIR.toFile().listFiles())
                                .filter(file -> !file.isDirectory() && file.getName().startsWith(l.getName() + "-glow-configuration-"))
                                .sorted()
                                .collect(Collectors.toList());
                        for (File f : files) {
                            l.getConfiguration().add(f.toURI().toString());
                        }
                    } else {
                        String val = l.getProperties().get(k);
                        String[] split = val.split(",");
                        l.getConfiguration().addAll(Arrays.asList(split));
                    }
                    continue;
                }
                if (k.startsWith(LayerMetadata.NO_CONFIGURATION_IF)) {
                    mapping.getNoConfigurationConditions().put(l, k);
                    continue;
                }
                if (k.startsWith(LayerMetadata.HIDDEN_IF)) {
                    mapping.getHiddenConditions().put(l, k);
                    continue;
                }
                if (LayerMetadata.EXPECT_ADD_ON_FAMILY.equals(k)) {
                    l.setExpectFamily(l.getProperties().get(k));
                    continue;
                }
                if (LayerMetadata.ADD_ON.equals(k)) {
                    String familyAndName = l.getProperties().get(k);
                    String[] split = familyAndName.split(",");
                    String family = split[0];
                    String name = split[1];
                    // Do we have a description for it
                    String description = l.getProperties().get(LayerMetadata.ADD_ON_DESCRIPTION);
                    AddOn addon = new AddOn(name, family, description);
                    String dependencies = l.getProperties().get(LayerMetadata.ADD_ON_DEPENDS_ON);
                    if ("all-dependencies".equals(dependencies)) {
                        addon.getLayersThatExpectAllDependencies().add(l);
                    } else {
                        if (dependencies.startsWith("only:")) {
                            int i = dependencies.indexOf(":");
                            String v = dependencies.substring(i + 1);
                            String[] deps = v.split(",");
                            Set<Layer> set = addon.getLayersThatExpectSomeDependencies().get(l);
                            if (set == null) {
                                set = new TreeSet<>();
                                addon.getLayersThatExpectSomeDependencies().put(l, set);
                            }
                            for (String d : deps) {
                                Layer dl = layers.get(d);
                                if (dl != null) {
                                    set.add(dl);
                                }
                            }
                        } else {
                            if ("none".equals(dependencies)) {
                                // Such addons are always proposed, if addOn enabled, layer is always included.
                                addon.getLayersAlwaysIncluded().add(l);
                            }
                        }
                    }

                    addon.getLayers().add(l);
                    l.setAddOn(addon);
                    mapping.getAddOns().put(addon.getName(), addon);
                    Set<AddOn> members = mapping.getAddOnFamilyMembers().get(family);
                    if (members == null) {
                        members = new TreeSet<>();
                        mapping.getAddOnFamilyMembers().put(family, members);
                    }
                    members.add(addon);
                    // Cardinality
                    String cardinality = l.getProperties().get(LayerMetadata.ADD_ON_CARDINALITY);
                    if (cardinality != null) {
                        int i = Integer.parseInt(cardinality);
                        mapping.getAddOnsCardinalityInFamily().put(family, i);
                    } else {
                        if (addon.isDefault()) {
                            mapping.getAddOnsCardinalityInDefaultFamily().put(family, 1);
                        }
                    }
                    // Found the issues that get fixed by enabling this layer
                    for (String kk : l.getProperties().keySet()) {
                        if (kk.startsWith(LayerMetadata.ADD_ON_FIX)) {
                            int i = LayerMetadata.ADD_ON_FIX.length();
                            String id = kk.substring(i);
                            String value = l.getProperties().get(kk);
                            String[] items = value.split(",");
                            String fixDescription = null;
                            String fixContent = null;
                            if (items.length == 2) {
                                fixDescription = items[0];
                                fixContent = items[1];
                            }
                            Fix fix = new Fix(id, fixDescription, fixContent);
                            addon.getFixes().put(id, fix);
                            Set<AddOn> s = mapping.getFixedByAddons().get(id);
                            if (s == null) {
                                s = new TreeSet<>();
                                mapping.getFixedByAddons().put(id, s);
                            }
                            s.add(addon);
                            break;
                        }
                    }
                    continue;
                }
                if (LayerMetadata.KIND.equals(k)) {
                    String kind = l.getProperties().get(k);
                    if ("default-base-layer".equals(kind)) {
                        mapping.setDefaultBaseLayer(l);
                        //System.out.println("Default Base layer " + mapping.defaultBaseLayer);
                    } else {
                        if ("metadata-only".equals(kind)) {
                            mapping.getMetadataOnly().add(l);
                        }
                    }
                    continue;
                }
                if (LayerMetadata.BRING_DATASOURCE.equals(k)) {
                    String value = l.getProperties().get(k);
                    l.getBringDatasources().add(value);
                    continue;
                }
                if (k.startsWith(LayerMetadata.PROFILE)) {
                    int i = k.indexOf("-");
                    String profile = k.substring(i + 1);
                    String val = l.getProperties().get(k);
                    Set<Layer> set = mapping.getAllProfilesLayers().get(profile);
                    if (set == null) {
                        set = new TreeSet<>();
                        mapping.getAllProfilesLayers().put(profile, set);
                    }
                    set.add(layers.get(val));
                }
                if (LayerMetadata.INCLUSION_MODE.equals(k)) {
                    String val = l.getProperties().get(k);
                    if ("all-dependencies".equals(val)) {
                        mapping.getLayersIncludedIfAllDeps().add(l);
                    } else {
                        if (val.startsWith("only:")) {
                            int i = val.indexOf(":");
                            String v = val.substring(i + 1);
                            String[] split = v.split(",");
                            Set<String> set = mapping.getLayersIncludedIfSomeDeps().get(l);
                            if (set == null) {
                                set = new TreeSet<>();
                                mapping.getLayersIncludedIfSomeDeps().put(l, set);
                            }
                            set.addAll(Arrays.asList(split));
                        }
                    }
                    continue;
                }
                if (!profiles.isEmpty()) {
                    Set<String> referencedProfiles = getProfiles(profiles, k, l);
                    for (String s : referencedProfiles) {
                        mapping.getActiveProfilesLayers().put(s, l);
                    }
                }
            }
        }
        return mapping;
    }

    private static Set<String> getProfiles(Set<String> profiles, String k, Layer l) {
        Set<String> ret = new TreeSet<>();
        for (String p : profiles) {
            if ((LayerMetadata.PROFILE + p).equals(k)) {
                String val = l.getProperties().get(k);
                String[] split = val.split(",");
                ret.addAll(Arrays.asList(split));
            }
        }
        return ret;
    }

    public static Set<String> getAllProfiles(Map<String, Layer> layers) {
        Set<String> profiles = new TreeSet<>();
        for (Layer l : layers.values()) {
            for (String k : l.getProperties().keySet()) {
                if (k.startsWith(LayerMetadata.PROFILE)) {
                    int i = k.indexOf("-");
                    profiles.add(k.substring(i + 1));
                }
            }
        }
        profiles.add("standalone");
        return profiles;
    }

    public static String getAddOnFix(AddOn ao, String fixContent) throws URISyntaxException, IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("add-on ").append(ao.getName()).append(" fixes the problem");
        StringBuilder envBuilder = new StringBuilder();
        for (Layer l : ao.getLayers()) {
            if (!l.getConfiguration().isEmpty()) {
                for (String c : l.getConfiguration()) {
                    URI uri = new URI(c);
                    Set<Env> envs = EnvHandler.retrieveEnv(uri);
                    for (Env env : envs) {
                        if (env.isRequired()) {
                            envBuilder.append("   - ").append(env.getName()).append("=").append(env.getDescription()).append("\n");
                        }
                    }
                }
            }
        }
        if (envBuilder.length() != 0) {
            builder.append(" but you need to set the strongly suggested configuration.");
        } else {
            if (fixContent == null || fixContent.isEmpty()) {
                builder.append(" fully");
            } else {
                builder.append("   - ").append(fixContent);
            }
        }

        return builder.toString();
    }

    public static boolean layersAreBanned(Set<Layer> layers) {
        for (Layer l : layers) {
            if (!l.isBanned()) {
                return false;
            }
        }
        return true;
    }

    public static String getConfigEntry(String entry) throws IOException {

        String prop = System.getProperty(entry);
        if (prop != null) {
            return prop;
        }

        InputStream stream = Utils.class.getResourceAsStream("glow.properties");
        if (stream == null) {
            return null;
        }
        try {
            Properties properties = new Properties();
            properties.load(stream);
            String value = properties.getProperty(entry);
            return value;
        } finally {
            stream.close();
        }
    }

}
