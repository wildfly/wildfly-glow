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

import aQute.bnd.classfile.Attribute;
import aQute.bnd.classfile.ClassFile;
import aQute.bnd.classfile.CodeAttribute;
import aQute.bnd.classfile.ConstantPool;
import aQute.bnd.classfile.FieldInfo;
import aQute.bnd.classfile.LocalVariableTableAttribute;
import aQute.bnd.classfile.LocalVariableTypeTableAttribute;
import aQute.bnd.classfile.SignatureAttribute;
import aQute.lib.io.ByteBufferDataInput;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.JarIndexer;
import org.jboss.jandex.MethodInfo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.wildfly.glow.DeploymentFileRuleInspector.ParsedRule;
import org.wildfly.glow.DeploymentFileRuleInspector.PatternOrValue;
import org.wildfly.glow.error.ErrorIdentificationSession;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.ASM9;
import org.wildfly.glow.error.ErrorLevel;
import org.wildfly.glow.error.IdentifiedError;

public class DeploymentScanner implements AutoCloseable {

    private final Path binary;
    private final Path tempDirectory;
    private boolean verbose;
    private final Set<Pattern> excludeArchivesFromScan;
    private ArchiveType archiveType;
    private DeploymentScanner parent;
    private final boolean isArchive;

    public DeploymentScanner(Path binary, boolean verbose, Set<Pattern> excludeArchivesFromScan) throws IOException {
        this(null, binary, verbose, excludeArchivesFromScan);
    }

    private DeploymentScanner(DeploymentScanner parent, Path binary, boolean verbose, Set<Pattern> excludeArchivesFromScan) throws IOException {
        this.parent = parent;
        this.tempDirectory = parent == null ? Files.createTempDirectory("glow") : parent.tempDirectory;
        this.verbose = verbose;
        this.excludeArchivesFromScan = excludeArchivesFromScan;

        if (!Files.exists(binary)) {
            throw new IllegalArgumentException(binary.normalize().toAbsolutePath() + " is not an archive");
        }
        isArchive = !Files.isDirectory(binary);
        FileNameParts fileNameParts = FileNameParts.parse(binary);
        this.archiveType = fileNameParts.archiveType;

        if (parent == null) {
            this.binary = binary;
        } else {
            if (isArchive) {
                // We need to copy the nested archive out of the containing archive
                // The binary argument comes from the Jar filesystem, while the tempDirectory is in the default filesystem
                this.binary = Files.createTempFile(tempDirectory, fileNameParts.coreName, fileNameParts.archiveType.suffix);
                Files.delete(this.binary);
                Files.copy(binary, this.binary);
            } else {
                this.binary = binary;
            }
        }
    }

    @Override
    public void close() {
        if (parent != null && binary != null) {
            try {
                if (isArchive) {
                    Files.delete(binary);
                }
            } catch (IOException ignore) {
            }
        }
        if(parent == null) {
            IoUtils.recursiveDelete(tempDirectory);
        }
    }

    public void scan(LayerMapping mapping, Set<Layer> layers, Map<String, Layer> all, ErrorIdentificationSession errorSession) throws Exception {
        Set<Layer> discoveredLayers = new LinkedHashSet<>();
        DeploymentScanContext ctx = new DeploymentScanContext(mapping, discoveredLayers, all, errorSession);
        scan(ctx);
        for (Layer l : discoveredLayers) {
            if (!l.isBanned()) {
                layers.add(l);
            }
        }

        errorSession.collectEndOfScanErrors(verbose, ctx.resourceInjectionJndiInfos, ctx.contextLookupInfos, ctx.dataSourceDefinitionInfos, ctx.allClasses);
    }

    private void scan(DeploymentScanContext ctx) throws Exception {
        scanAnnotations(ctx);
        FileSystem fs = isArchive ? ZipUtils.newFileSystem(binary) : binary.getFileSystem();
        try {
        Path rootPath = isArchive ? fs.getPath("/") : binary;
        scanTypesAndChildren(rootPath, ctx);
        ctx.layers.addAll(inspectDeployment(rootPath, ctx));
        } finally {
            if (isArchive) {
                fs.close();
            }
        }
    }

    private void scanAnnotations(DeploymentScanContext ctx) throws IOException {
        Indexer indexer = new Indexer();
        Index index = isArchive ? JarIndexer.createJarIndex(binary.toFile(),
                indexer, false, true, false).getIndex()
                : DirectoryIndexer.indexDirectory(binary.toFile(), indexer);
        for (ClassInfo ci : index.getKnownClasses()) {
            //System.out.println(ci.name());
            for (AnnotationInstance ai : ci.annotations()) {
                handleResourceInjectionAnnotations(ai, ctx);

                //System.out.println("   " + ai.name().packagePrefix());
                Set<Layer> l = ctx.mapping.getAnnotations().get(ai.name().toString());
                if (l != null) {
                    ctx.layers.addAll(l);
                    LayerMapping.addRule(LayerMapping.RULE.ANNOTATION,l, ai.name().toString());
                    //System.out.println("Find an annotation " + ai.name().toString() + " layer being " + l);
                } else {
                    l = ctx.mapping.getAnnotations().get(ai.name().packagePrefix());
                    if (l != null) {
                        ctx.layers.addAll(l);
                        //System.out.println("Find an annotation " + ai.name().packagePrefix() + " layer being " + l);
                        LayerMapping.addRule(LayerMapping.RULE.ANNOTATION, l, ai.name().packagePrefix() + ".*");
                    } else {
                        // Pattern?
                        for (String s : ctx.mapping.getAnnotations().keySet()) {
                            if (Utils.isPattern(s)) {
                                Pattern p = Pattern.compile(s);
                                if (p.matcher(ai.name().toString()).matches()) {
                                    Set<Layer> layers = ctx.mapping.getAnnotations().get(s);
                                    if  (layers != null) {
                                        LayerMapping.addRule(LayerMapping.RULE.ANNOTATION, layers, s);
                                        ctx.layers.addAll(layers);
                                    }
                                }
                            }
                        }
                    }
                    Map<String, List<AnnotationFieldValue>> fields = ctx.mapping.getAnnotationFieldValues().get(ai.name().toString());
                    if (fields != null) {
                        Layer foundLayer = null;
                        for(Entry<String, List<AnnotationFieldValue>> f : fields.entrySet()) {
                            String val = getAnnotationValue(ai, f.getKey());
                            if (val != null) {
                                List<AnnotationFieldValue> lstFields = f.getValue();
                                for (AnnotationFieldValue fv : lstFields) {
                                    if (Utils.isPattern(fv.getFieldValue())) {
                                        Pattern p = Pattern.compile(fv.getFieldValue());
                                        if (p.matcher(val).matches()) {
                                            foundLayer = fv.getLayer();
                                            LayerMapping.addRule(LayerMapping.RULE.ANNOTATION_VALUE, foundLayer, ai.name().toString() + "_" + f.getKey() + "=" + fv.getFieldValue());
                                            ctx.layers.add(fv.getLayer());
                                        }
                                    } else {
                                        if (val.equals(fv.getFieldValue())) {
                                            foundLayer = fv.getLayer();
                                            LayerMapping.addRule(LayerMapping.RULE.ANNOTATION_VALUE, foundLayer, ai.name().toString() + "_" + f.getKey() + "=" + fv.getFieldValue());
                                            ctx.layers.add(fv.getLayer());
                                        }
                                    }
                                }
                            }
                        }
                        // DataSourceDefinition are only added based on layers discovered in the above nested loop.
                        handleDataSourceDefinitionAnnotations(ai, ctx, foundLayer);
                    }
                    if (ai.target().kind() == AnnotationTarget.Kind.FIELD) {
                        Map<String, List<AnnotatedType>> annotatedTypes = ctx.mapping.getAnnotatedTypes().get(ai.name().toString());
                        if (annotatedTypes != null) {
                            String type = ai.target().asField().type().toString();
                            List<AnnotatedType> annotations = annotatedTypes.get(type);
                            if (annotations != null) {
                                for (AnnotatedType at : annotations) {
                                    if (at.getFields().isEmpty()) {
                                        LayerMapping.addRule(LayerMapping.RULE.ANNOTATED_TYPE, at.getLayer(), "@" + ai.name().toString() + "\n" + at.getType());
                                        ctx.layers.add(at.getLayer());
                                    } else {
                                        for (Entry<String, String> entry : at.getFields().entrySet()) {
                                            String val = getAnnotationValue(ai, entry.getKey());
                                            if (val != null) {
                                                if (Utils.isPattern(entry.getValue())) {
                                                    Pattern p = Pattern.compile(entry.getValue());
                                                    if (p.matcher(val).matches()) {
                                                        LayerMapping.addRule(LayerMapping.RULE.ANNOTATED_TYPE, at.getLayer(), "@" + ai.name().toString() + "_" + entry.getKey() + "=" + entry.getValue());
                                                        ctx.layers.add(at.getLayer());
                                                    }
                                                } else {
                                                    if (val.equals(entry.getValue())) {
                                                        LayerMapping.addRule(LayerMapping.RULE.ANNOTATED_TYPE, at.getLayer(), "@" + ai.name().toString() + entry.getKey() + "=" + entry.getValue() + "\n" + at.getType());
                                                        ctx.layers.add(at.getLayer());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        int i = binary.toFile().getName().lastIndexOf(".");
        String ext = binary.toFile().getName().substring(i + 1);
        String name = binary.toFile().getName().substring(0, i) + "-jandex";
        Path parent = binary.getParent();
        Path jd = parent == null ? Paths.get(name + "." + ext) : parent.resolve(name + "." + ext);
        if (Files.exists(jd)) {
            Files.delete(jd);
        }
    }

    private void handleResourceInjectionAnnotations(AnnotationInstance annotationInstance, DeploymentScanContext ctx) {
        if (annotationInstance.name().toString().equals("jakarta.annotation.Resource")) {
            AnnotationTarget tgt = annotationInstance.target();
            AnnotationValue typeFromAnnotation = annotationInstance.value("type");
            String resourceClassName = null;

            if (typeFromAnnotation != null) {
                resourceClassName = typeFromAnnotation.asClass().toString();
            } else {
                if (tgt.kind() == AnnotationTarget.Kind.FIELD) {
                    resourceClassName = tgt.asField().type().toString();
                } else if (tgt.kind() == AnnotationTarget.Kind.METHOD) {
                    MethodInfo mi = tgt.asMethod();
                    if (isSetter(mi)) {
                        resourceClassName = mi.parameterTypes().get(0).toString();
                    }
                } else if (tgt.kind() == AnnotationTarget.Kind.CLASS) {
                    // We cannot infer a type and the default for the type value is Object
                    resourceClassName = Object.class.getName();
                }
            }

            String injectionPoint = null;
            if (tgt.kind() == AnnotationTarget.Kind.CLASS) {
                injectionPoint = tgt.asClass().toString();
            } else if (tgt.kind() == AnnotationTarget.Kind.FIELD) {
                injectionPoint = tgt.asField().declaringClass().toString() + "." + tgt.asField().name();
            } else if (tgt.kind() == AnnotationTarget.Kind.METHOD && isSetter(tgt.asMethod())) {
                // For now, I don't want to include the parameters here since I didn't want to include the field type above
                injectionPoint = tgt.asMethod().declaringClass().toString() + "." + tgt.asMethod().name() + "()";
            }

            Set<Layer> resourceLayers = new HashSet<>();
            String jndiName = findJndiName(annotationInstance);
            if (jndiName != null) {
                // A layer can bring the referenced resource.
                Layer l = lookupJndi(jndiName, ctx);
                if (l != null) {
                    resourceLayers.add(l);
                }
            }
            if (resourceClassName != null) {
                Set<Layer> layer = lookup(resourceClassName, ctx);
                if (layer != null) {
                    resourceLayers.addAll(layer);
                }
                ResourceInjectionJndiInfo info = new ResourceInjectionJndiInfo(resourceLayers, resourceClassName, injectionPoint, jndiName);
                //System.out.println(info);
                ctx.resourceInjectionJndiInfos.put(resourceClassName, info);
            }

        }
    }

    private void handleDataSourceDefinitionAnnotations(AnnotationInstance annotationInstance, DeploymentScanContext ctx, Layer foundLayer) {
        if (annotationInstance.name().toString().equals("jakarta.annotation.sql.DataSourceDefinition")) {
            String name = getAnnotationValue(annotationInstance, "name");
            String url = getAnnotationValue(annotationInstance, "url");
            String className = getAnnotationValue(annotationInstance, "className");
            if (name != null) {
                DataSourceDefinitionInfo di = new DataSourceDefinitionInfo(name, url, className, foundLayer);
                ctx.dataSourceDefinitionInfos.put(name, di);
            }
        }
    }

    private boolean isSetter(MethodInfo methodInfo) {
        return methodInfo.name().startsWith("set") && methodInfo.parameterTypes().size() == 1;
    }

    private String findJndiName(AnnotationInstance annotationInstance) {
        String lookup = getAnnotationValue(annotationInstance, "mappedName");
        if (lookup != null) {
            return lookup;
        }
        lookup = getAnnotationValue(annotationInstance, "lookup");
        if (lookup != null) {
            return lookup;
        }

        return null;
    }

    private String getAnnotationValue(AnnotationInstance instance, String name) {
        AnnotationValue value = instance.value(name);
        if (value == null) {
            return null;
        }
        return value.asString();
    }

    private void scanTypesAndChildren(Path archiveContentRoot, DeploymentScanContext ctx) throws Exception {
        Files.walkFileTree(archiveContentRoot, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new NestedWarOrExplodedArchiveFileVisitor(archiveContentRoot, isArchive) {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".class")) {
                    if (archiveType != ArchiveType.EAR) {
                        scanClass(file, ctx);
                    }
                } else if (ArchiveType.isArchiveName(file)) {
                    Path relativeFile = archiveContentRoot.relativize(file);
                    if (archiveType.isValidArchiveLocation(relativeFile)) {
                        scanWithNestedScanner(file, ctx);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                FileVisitResult result = super.preVisitDirectory(dir, attrs);
                if (result == FileVisitResult.CONTINUE) {
                    return FileVisitResult.CONTINUE;
                }
                Path relativeFile = archiveContentRoot.relativize(dir);
                if (archiveType.isValidArchiveLocation(relativeFile)) {
                    scanWithNestedScanner(dir, ctx);
                }
                return result;
            }
        });
    }

    private void scanWithNestedScanner(Path file, DeploymentScanContext ctx) throws IOException {
        // Check it is not an excluded archive
        for (Pattern exclude : excludeArchivesFromScan) {
            if (exclude.matcher(file.getFileName().toString()).matches()) {
                return;
            }
        }

        try (DeploymentScanner nestedScanner = new DeploymentScanner(DeploymentScanner.this, file, verbose, excludeArchivesFromScan)) {
            try {
                nestedScanner.scan(ctx);
            } catch (RuntimeException | IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void scanClass(Path file, DeploymentScanContext ctx) throws IOException {
        byte[] content = Files.readAllBytes(file);
        DataInput in = ByteBufferDataInput.wrap(content);
        ClassFile clazz = ClassFile.parseClassFile(in);
        ctx.allClasses.add(clazz.this_class.replaceAll("/", "."));
        for (int i = 0; i < clazz.constant_pool.size(); i++) {
            Object obj = clazz.constant_pool.entry(i);
            if (obj instanceof ConstantPool.ClassInfo) {
                ConstantPool.ClassInfo ci = (ConstantPool.ClassInfo) obj;
                String className = (String) clazz.constant_pool.entry(ci.class_index);
                //System.out.println(className);
                className = className.replaceAll("/", ".");
                lookup(className, ctx);
            } else {
                if (obj instanceof ConstantPool.FieldrefInfo) {
                    ConstantPool.FieldrefInfo info = (ConstantPool.FieldrefInfo) obj;
                    ConstantPool.NameAndTypeInfo ntinfo = (ConstantPool.NameAndTypeInfo) clazz.constant_pool.entry(info.name_and_type_index);
                    String className = formatClassName((String) clazz.constant_pool.entry(ntinfo.descriptor_index));
                    //System.out.println("CLASSNAME " + className);
                    lookup(className, ctx);
                }
            }
        }
        for (int i = 0; i < clazz.fields.length; i++) {
            FieldInfo fi = clazz.fields[i];
            String descriptor = formatClassName(fi.descriptor);
            lookup(descriptor, ctx);
            for (String type : extractClassesFromFieldSignatureAttribute(fi)) {
                lookup(type, ctx);
            }
        }
        for (int i = 0; i < clazz.methods.length; i++) {
            aQute.bnd.classfile.MethodInfo mi = clazz.methods[i];
            //System.out.println("Method descriptor " + mi.descriptor);
            for (String type : parseMethodDescriptor(mi.descriptor)) {
                lookup(type, ctx);
            }
            for (String type : extractTypeVariablesFromMethodSignatureAttribute(mi)) {
                lookup(type, ctx);
            }
            for (String type : parseLocalVariableAndLocalVariableTypeTables(mi)) {
                lookup(type, ctx);
            }
            for (String type : parseLocalVariableTypeTable(mi)) {
                lookup(type, ctx);
            }
        }
        lookForContextLookups(content, ctx);
    }

    private String trimArrayDimensionsFromDescriptor(String descriptor) {
        //'[' at the start of the descriptor means it is an array. Trim those
        for (int j = 0; j < descriptor.length(); j++) {
            if (descriptor.charAt(j) != '[') {
                if (j > 0) {
                    descriptor = descriptor.substring(j);
                }
                break;
            }
        }

        return descriptor;
    }

    private String formatClassName(String className) {
        className = trimArrayDimensionsFromDescriptor(className);
        if (className.startsWith("L")) {
            // class descriptor L<class>;
            className = className.substring(1, className.length() - 1);
        }
        className = className.replaceAll("/", ".");
        return className;
    }

    Set<String> parseMethodDescriptor(String descriptor) {
        Set<String> types = new HashSet<>();
        StringBuilder builder = null;
        for (char c : descriptor.toCharArray()) {
            if (c == 'L') {
                builder = new StringBuilder();
                builder.append(c);
            } else {
                if (c == ';') {
                    builder.append(c);
                    types.add(formatClassName(builder.toString()));
                    builder = null;
                } else {
                    if (builder != null) {
                        builder.append(c);
                    }
                }
            }
        }
        return types;
    }

    private Set<String> extractTypeVariablesFromMethodSignatureAttribute(aQute.bnd.classfile.MethodInfo mi) {
        String signature = getSignatureAttributeSignature(mi.attributes);
        if (signature == null) {
            return Collections.emptySet();
        }

        String[] parts = signature.split("\\(|\\)", 0);
        List<String> list = Arrays.stream(parts)
                .map(v -> v.trim())
                .filter(v -> v.length() > 0)
                .map(v -> trimArrayDimensionsFromDescriptor(v))
                .filter(v -> v.startsWith("L"))
                .collect(Collectors.toList());

        Set<String> types = new HashSet<>();
        for (String current : list) {
            types.addAll(extractClassesSignatureForMethod(current));
        }
        return types;
    }

    private String getSignatureAttributeSignature(Attribute[] attributes) {
        for (Attribute attribute : attributes) {
            if (attribute instanceof SignatureAttribute) {
                return ((SignatureAttribute) attribute).signature;
            }
        }
        return null;
    }

    private Set<String> extractClassesFromFieldSignatureAttribute(FieldInfo fieldInfo) {
        String signature = getSignatureAttributeSignature(fieldInfo.attributes);
        if (signature == null) {
            return Collections.emptySet();
        }
        return extractClassesSignatureForField(signature);
    }

    private Set<String> extractClassesSignatureForField(String signature) {
        String[] parts = signature.split("<|>|,");
        Set<String> set =
                Arrays.stream(parts)
                        .map(v -> v.trim())
                        .filter(v -> v.length() > 0)
                        .map(v -> formatClassName(v))
                        .collect(Collectors.toSet());
        return set;
    }

    private Set<String> extractClassesSignatureForMethod(String signature) {
        String[] parts = signature.split("<|>|,|;");
        Set<String> types = new HashSet<>();
        for (String part : parts) {
            part = part.trim();
            if (part.length() > 0) {
                part = trimArrayDimensionsFromDescriptor(part);
                if (part.startsWith("L")) {
                    // The regexp got rid of this and it is expected for formatClassName()
                    part = part + ";";
                    types.add(formatClassName(part));
                }
            }
        }
        return types;
    }


    private Set<String> parseLocalVariableAndLocalVariableTypeTables(aQute.bnd.classfile.MethodInfo mi) {
        Set<String> types = new HashSet<>();
        for (Attribute attr : mi.attributes) {
            if (attr instanceof CodeAttribute) {
                CodeAttribute codeAttribute = (CodeAttribute) attr;

                for (Attribute attribute : codeAttribute.attributes) {
                    if (attribute instanceof LocalVariableTableAttribute) {
                        LocalVariableTableAttribute localVariableTableAttribute = (LocalVariableTableAttribute) attribute;
                        for (LocalVariableTableAttribute.LocalVariable lv : localVariableTableAttribute.local_variable_table) {
                            String desc = lv.descriptor;
                            types.add(formatClassName(desc));
                        }
                    } else if (attribute instanceof LocalVariableTypeTableAttribute) {
                        LocalVariableTypeTableAttribute localVariableTypeTableAttribute = (LocalVariableTypeTableAttribute) attribute;
                        for (LocalVariableTypeTableAttribute.LocalVariableType lv : localVariableTypeTableAttribute.local_variable_type_table) {
                            String desc = lv.signature;
                            types.addAll(extractClassesSignatureForMethod(desc));
                        }
                    }
                }
            }
        }
        return types;
    }

    private Set<String> parseLocalVariableTypeTable(aQute.bnd.classfile.MethodInfo mi) {
        Set<String> types = new HashSet<>();
        for (Attribute attr : mi.attributes) {
            if (attr instanceof CodeAttribute) {
                CodeAttribute codeAttribute = (CodeAttribute) attr;

                for (Attribute attribute : codeAttribute.attributes) {
                    if (attribute instanceof LocalVariableTableAttribute) {
                        LocalVariableTableAttribute localVariableTableAttribute = (LocalVariableTableAttribute) attribute;
                        for (LocalVariableTableAttribute.LocalVariable lv : localVariableTableAttribute.local_variable_table) {
                            String desc = lv.descriptor;
                            types.add(formatClassName(desc));
                        }
                    }
                }
            }
        }
        return types;
    }


    private void lookForContextLookups(byte[] classBytes, DeploymentScanContext ctx) {
        ClassReader cr = new ClassReader(classBytes);
        ClassVisitor classVisitor = new ContextLookupClassVisitor(ctx);
        cr.accept(classVisitor, 0);
    }

    private Layer lookupJndi(String jndiName, DeploymentScanContext ctx) {
        Layer layer = null;
        for (Layer l : ctx.allLayers.values()) {
            // Datasources jndi name.
            if (l.getBringDatasources().contains(jndiName)) {
                layer = l;
                // System.out.print("Layer " + l.getName() + " is included by JNDI name " + jndiName);
                ctx.layers.add(l);
                LayerMapping.addRule(LayerMapping.RULE.BRING_DATASOURCE, l, jndiName);
            }
            // TODO, add the rule to layers that bring a jndi resource (eg: mail).
        }
        return layer;
    }

    private Set<Layer> lookup(String className, DeploymentScanContext ctx) {
        Map<String, Set<Layer>> map = ctx.mapping.getConstantPoolClassInfos();

        Set<Layer> l = map.get(className);
        if (l == null) {
            int index = className.lastIndexOf(".");
            if (index != -1) {
                String pkgPrefix = className.substring(0, index);
                l = map.get(pkgPrefix);
                if (l != null) {
                    LayerMapping.addRule(LayerMapping.RULE.JAVA_TYPE,l, pkgPrefix + ".*");
                }
            }
            if (l == null) {
                // Pattern?
                for (String s : map.keySet()) {
                    if (Utils.isPattern(s)) {
                        Pattern p = Pattern.compile(s);
                        if (p.matcher(className).matches()) {
                            l = map.get(s);
                        }
                        if (l != null) {
                            LayerMapping.addRule(LayerMapping.RULE.JAVA_TYPE,l, s);
                            break;
                        }
                    }
                }
            }
        } else {
            LayerMapping.addRule(LayerMapping.RULE.JAVA_TYPE, l, className);
        }
        if (l != null) {
            ctx.layers.addAll(l);
        }
        return l;
    }

    Set<Layer> inspectDeployment(Path rootPath,
            DeploymentScanContext ctx) throws Exception {

        DeploymentFileRuleInspector inspector = new DeploymentFileRuleInspector(rootPath, isArchive);

        Set<Layer> set = new TreeSet<>();

        for (String layer : ctx.allLayers.keySet()) {
            Layer l = ctx.allLayers.get(layer);
            for (String k : l.getProperties().keySet()) {
                List<Boolean> matchingRule = new ArrayList<>();
                matchingRule.add(Boolean.FALSE);
                final boolean isCondition = LayerMapping.isCondition(k);
                final Consumer<Layer> consumer = isCondition ? (ll) -> {
                    matchingRule.set(0, Boolean.TRUE);
                } : (ll) -> {
                    set.add(ll);
                    matchingRule.set(0, Boolean.TRUE);
                };
                final String originalKey = k;
                k = LayerMapping.cleanupKey(k);
                final String val = l.getProperties().get(originalKey);
                if (k.startsWith(LayerMetadata.XML_PATH)) {
                    ParsedRule rule = inspector.extractParsedRule(val);
                    rule.iterateMatchedPaths((path, values) -> {
                        try {
                            Utils.applyXPath(path, values.get(0).getValue(), values.size() == 1 ? null : values.get(1).getValue(), consumer, l);
                        } catch(Exception ex) {
                            String id = "invalidXML" + path;
                            boolean allreadySet = false;
                            for (IdentifiedError err : ctx.errorSession.getErrors()) {
                                if (id.equals(err.getId())) {
                                    allreadySet = true;
                                }
                            }
                            if (!allreadySet) {
                                ctx.errorSession.addError(new IdentifiedError(id, "Exception parsing " + path + ": " + ex, ErrorLevel.WARN));
                            }
                        }
                    });
                } else if (k.startsWith(LayerMetadata.PROPERTIES_FILE_MATCH)) {
                    ParsedRule parsedRule = inspector.extractParsedRule(val);
                    parsedRule.iterateMatchedPaths((path, values) -> {
                        Properties props = new Properties();
                        try (InputStream reader = Files.newInputStream(path)) {
                            props.load(reader);
                            for (String prop : props.stringPropertyNames()) {
                                if (parsedRule.getValueParts().size() >= 1) {
                                    PatternOrValue key = parsedRule.getValueParts().get(0);
                                    // Check matches key
                                    boolean match = key.equalsOrMatches(prop);
                                    PatternOrValue value = null;
                                    if (match && parsedRule.getValueParts().size() == 2) {
                                        value = parsedRule.getValueParts().get(1);
                                        if (value != null) {
                                            match = value.equalsOrMatches(props.getProperty(prop));
                                        }
                                    }
                                    if (match) {
                                        consumer.accept(l);
                                        LayerMapping.addRule(LayerMapping.RULE.PROPERTIES_FILE, l,
                                                path.toString() + "==>" + prop + (value != null ? "==" + props.getProperty(prop) : ""));
                                    }
                                }
                            }
                        }
                    });
                } else if (k.startsWith(LayerMetadata.EXPECTED_FILE)) {
                    ParsedRule parsedRule = inspector.extractParsedRule(val);
                    parsedRule.iterateMatchedPaths((path, values) -> {
                        consumer.accept(l);
                        LayerMapping.addRule(LayerMapping.RULE.EXPECTED_FILE, l, path.toString());
                    });
                } else if (k.startsWith(LayerMetadata.NOT_EXPECTED_FILE)) {
                    ParsedRule parsedRule = inspector.extractParsedRule(val);
                    List<Path> paths = parsedRule.getMatchedPaths();
                    if (paths.size() == 0) {
                        LayerMapping.addRule(LayerMapping.RULE.NOT_EXPECTED_FILE, l, val);
                        consumer.accept(l);
                    }
                }
                if (isCondition && matchingRule.get(0)) {
                    String condition = ctx.mapping.getNoConfigurationConditions().get(l);
                    if (originalKey.equals(condition)) {
                        //System.out.println("Remove all configurations from this layer");
                        l.getConfiguration().clear();
                    }
                    String hiddenCondition = ctx.mapping.getHiddenConditions().get(l);
                    if (originalKey.equals(hiddenCondition)) {
                        //System.out.println("condition " + originalKey + " makes layer " + l.getName() + " banned.");
                        l.setBanned(true);
                    }
                }
            }
        }

        ctx.errorSession.collectErrors(rootPath);

        return set;
    }

    private String pathRelativeToRoot(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return path;
    }

    private String adjustPatternInputRelativeToRoot(Path rootPath, String pathPattern) {
        if (rootPath.toString().endsWith("/")) {
            return rootPath + pathRelativeToRoot(pathPattern);
        } else {
            return rootPath + pathPattern;
        }
    }

    private static class FileNameParts {

        private final String coreName;
        private final ArchiveType archiveType;

        public FileNameParts(String coreName, ArchiveType archiveType) {
            this.coreName = coreName;
            this.archiveType = archiveType;
        }

        static FileNameParts parse(Path binary) {
            String filename = binary.getFileName().toString();
            int index = filename.lastIndexOf(".");
            String suffix = filename.substring(index + 1);
            String core = filename.substring(0, index);
            return new FileNameParts(core, ArchiveType.parse(suffix));
        }
    }

    enum ArchiveType {
        EAR(".ear") {
            @Override
            public boolean isValidArchiveLocation(Path pathInArchive) {
                // Accept all war and jar files no matter the location
                return ArchiveType.isJar(pathInArchive) || ArchiveType.isWar(pathInArchive) || ArchiveType.isRar(pathInArchive) || ArchiveType.isSar(pathInArchive);
            }
        },
        WAR(".war") {
            @Override
            public boolean isValidArchiveLocation(Path pathInArchive) {

                // Only /WEB-INF/lib/*.jar is allowed
                if (!ArchiveType.isJar(pathInArchive)) {
                    return false;
                }
                if (pathInArchive.getNameCount() != 3) {
                    return false;
                }
                if (!pathInArchive.getName(0).toString().equals("WEB-INF") || !pathInArchive.getName(1).toString().equals("lib")) {
                    return false;
                }
                return true;
            }
        },
        JAR(".jar"),
        RAR(".rar") {
            @Override
            public boolean isValidArchiveLocation(Path pathInArchive) {
                return pathInArchive.getNameCount() == 1 && ArchiveType.isJar(pathInArchive);
            }
        },
        SAR(".sar");

        private final String suffix;

        ArchiveType(String suffix) {
            this.suffix = suffix;
        }

        public boolean isValidArchiveLocation(Path pathInArchive) {
            return false;
        }

        static ArchiveType parse(String s) {
            switch (s) {
                case "ear":
                    return EAR;
                case "war":
                    return WAR;
                case "jar":
                    return JAR;
                case "rar":
                    return RAR;
                case "sar":
                    return SAR;
                default:
                    throw new IllegalArgumentException(s);
            }
        }

        private static boolean isJar(Path pathInArchive) {
            return hasSuffix(pathInArchive, JAR.suffix);
        }

        private static boolean isWar(Path pathInArchive) {
            return hasSuffix(pathInArchive, WAR.suffix);
        }

        private static boolean isRar(Path pathInArchive) {
            return hasSuffix(pathInArchive, RAR.suffix);
        }

        private static boolean isSar(Path pathInArchive) {
            return hasSuffix(pathInArchive, SAR.suffix);
        }

        private static boolean hasSuffix(Path pathInArchive, String suffix) {
            return pathInArchive.getFileName().toString().endsWith(suffix);
        }

        static boolean isArchiveName(Path path) {
            for (ArchiveType type : ArchiveType.values()) {
                if (path.getFileName().toString().endsWith(type.suffix)) {
                    return true;
                }
            }
            return false;
        }
    }

    static class DeploymentScanContext {

        private final LayerMapping mapping;
        private final Set<Layer> layers;
        private final Map<String, Layer> allLayers;
        private final ErrorIdentificationSession errorSession;
        private final Set<String> allClasses = new HashSet<>();
        private final Map<String, ResourceInjectionJndiInfo> resourceInjectionJndiInfos = new HashMap<>();
        private final Map<String, DataSourceDefinitionInfo> dataSourceDefinitionInfos = new HashMap<>();
        public Set<ContextLookupInfo> contextLookupInfos = new HashSet<>();

        private DeploymentScanContext(LayerMapping mapping, Set<Layer> layers, Map<String, Layer> allLayers, ErrorIdentificationSession errorSession) {
            this.mapping = mapping;
            this.layers = layers;
            this.allLayers = allLayers;
            this.errorSession = errorSession;
        }
    }

    private class ContextLookupClassVisitor extends ClassVisitor {

        private DeploymentScanContext ctx;
        private String clazz;

        public ContextLookupClassVisitor(DeploymentScanContext ctx) {
            super(ASM9);
            this.ctx = ctx;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            clazz = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitEnd() {
            clazz = null;
            super.visitEnd();
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor visitor = new ContextLookupMethodVisitor(clazz, name, ctx);
            return visitor;
        }
    }

    private class ContextLookupMethodVisitor extends MethodVisitor {

        private DeploymentScanContext ctx;
        private String clazz;
        private String method;

        public ContextLookupMethodVisitor(String clazz, String method, DeploymentScanContext ctx) {
            super(ASM9);
            this.clazz = clazz;
            this.method = method;
            this.ctx = ctx;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (!"lookup".equals(name)) {
                return;
            }
            if ("javax/naming/Context".equals(owner) || "javax/naming/InitialContext".equals(owner)) {
                // Makes sure the naming layer gets added
                String lookupClass = owner.replace('/', '.');
                lookup(lookupClass, ctx);
                String method = clazz.replace('/', '.') + "." + this.method + "()";

                ctx.contextLookupInfos.add(new ContextLookupInfo(method));
            }
        }
    }


}
