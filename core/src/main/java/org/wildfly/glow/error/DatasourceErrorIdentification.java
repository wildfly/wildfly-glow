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
import org.wildfly.glow.Utils;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.wildfly.glow.Env;

import static org.wildfly.glow.Utils.getAddOnFix;

/**
 *
 * @author jdenise
 */
public class DatasourceErrorIdentification implements ErrorIdentification {

    private static final String DEFAULT_DATASOURCE_JNDI_NAME="java:comp/DefaultDataSource";

    private static final String UNBOUND_DATASOURCES_ERROR = "unbound-datasources";
    private static final String NO_DEFAULT_DATASOURCE_ERROR = "no-default-datasource";
    private static final String UNBOUND_DATASOURCES_ERROR_DESCRIPTION = "unbound datasources error";
    private static final String NO_DEFAULT_DATASOURCE_ERROR_DESCRIPTION = "no default datasource found error";
    Map<String, Set<IdentifiedError>> errors = new HashMap<>();

    @Override
    public void collectErrors(Path rootPath) throws Exception {
        Path persistence = rootPath.resolve("/WEB-INF/classes/META-INF/persistence.xml");
        Set<String> expectedDataSources = null;
        boolean persistenceExists = Files.exists(persistence);
        if (persistenceExists) {
            expectedDataSources = Utils.getXMLElementValues(persistence, "/persistence/persistence-unit/jta-data-source");
            //System.out.println("DS " + expectedDataSources);
        }
        if (expectedDataSources != null && !expectedDataSources.isEmpty()) {
            // Retrieve all in war DS
            Set<String> allDS = new TreeSet<>();
            Pattern p = Pattern.compile("/WEB-INF/.*.xml");
            Files.walkFileTree(rootPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (p.matcher(file.toString()).matches()) {
                        try {
                            Set<String> ds = Utils.getXMLElementValues(file, "/datasources/datasource/@jndi-name");
                            allDS.addAll(ds);
                            Set<String> xads = Utils.getXMLElementValues(file, "/datasources/xa-datasource/@jndi-name");
                            allDS.addAll(xads);
                        } catch (Exception ex) {
                            throw new IOException(ex);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

            });
            //System.out.println("ALL configured DS " + allDS);
            Set<String> unboundDatasources = new TreeSet<>();
            for (String ds : expectedDataSources) {
                if (!allDS.contains(ds)) {
                    if (DEFAULT_DATASOURCE_JNDI_NAME.equals(ds)) {
                        Set<IdentifiedError> errs = new HashSet<>();
                        errs.add(new MissingDefaultDatasourceError(NO_DEFAULT_DATASOURCE_ERROR,
                                NO_DEFAULT_DATASOURCE_ERROR_DESCRIPTION));
                        errors.put(NO_DEFAULT_DATASOURCE_ERROR, errs);
                    } else {
                        unboundDatasources.add(ds);
                    }
                }
            }
            if (!unboundDatasources.isEmpty()) {
                Set<IdentifiedError> errs = errors.get(UNBOUND_DATASOURCES_ERROR);
                if (errs == null) {
                    errs = new HashSet<>();
                    errors.put(UNBOUND_DATASOURCES_ERROR, errs);
                }
                for (String ds : unboundDatasources) {
                    errs.add(new UnboundDatasourceError(UNBOUND_DATASOURCES_ERROR,
                            UNBOUND_DATASOURCES_ERROR_DESCRIPTION, ds));
                }
            }
        } else {
            if (persistenceExists) {
                Set<IdentifiedError> errs = new HashSet<>();
                errs.add(new MissingDefaultDatasourceError(NO_DEFAULT_DATASOURCE_ERROR,
                        NO_DEFAULT_DATASOURCE_ERROR_DESCRIPTION));
                errors.put(NO_DEFAULT_DATASOURCE_ERROR, errs);
            }
        }
    }

    @Override
    public Map<Layer, Set<Env>> refreshErrors(Set<Layer> allBaseLayers) throws Exception {
        Set<IdentifiedError> unboundDatasourcesErrors = errors.get(UNBOUND_DATASOURCES_ERROR);
        Set<String> toRemove = new HashSet<>();
        Map<Layer, Set<Env>> ret = new HashMap<>();
        if (unboundDatasourcesErrors != null) {
            for (IdentifiedError error : unboundDatasourcesErrors) {
                UnboundDatasourceError uds = (UnboundDatasourceError) error;
                for (Layer l : allBaseLayers) {
                    if (l.getBringDatasources().contains(uds.unboundDatasource)) {
                        // The error is directly handled, we can remove it.
                        toRemove.add(uds.unboundDatasource);
                        break;
                    } else {
                        if (l.getAddOn() != null) {
                            Fix fix = l.getAddOn().getFixes().get(error.getId());
                            if (fix != null) {
                                String content = null;
                                if (!l.getBringDatasources().contains(uds.unboundDatasource)) {
                                    content = fix.getContent();
                                    if (content != null) {
                                        content = content.replaceAll("##ITEM##", uds.unboundDatasource);
                                    }
                                    if (fix.isEnv()) {
                                        Set<Env> envs = ret.get(l);
                                        if (envs == null) {
                                            envs = new HashSet<>();
                                            ret.put(l, envs);
                                        }
                                        envs.add(new Env(fix.getEnvName(), Fix.getEnvValue(content), false, true, false));
                                    }
                                }
                                String errorMessage = getAddOnFix(l.getAddOn(), content);
                                error.setFixed(errorMessage);
                            }
                        }
                    }
                }
            }
            Iterator<IdentifiedError> it = unboundDatasourcesErrors.iterator();
            while (it.hasNext()) {
                UnboundDatasourceError uds = (UnboundDatasourceError) it.next();
                if (toRemove.contains(uds.unboundDatasource)) {
                    it.remove();
                }
            }
        }
        Set<IdentifiedError> noDefaultDataspourceErrors = errors.get(NO_DEFAULT_DATASOURCE_ERROR);
        if (noDefaultDataspourceErrors != null) {
            for (IdentifiedError error : noDefaultDataspourceErrors) {
                for (Layer l : allBaseLayers) {
                    if (l.getAddOn() != null) {
                        Fix fix = l.getAddOn().getFixes().get(error.getId());
                        if (fix != null) {
                            String content = fix.getContent();
                            if (fix.isEnv()) {
                                Set<Env> envs = ret.get(l);
                                if (envs == null) {
                                    envs = new HashSet<>();
                                    ret.put(l, envs);
                                }
                                envs.add(new Env(fix.getEnvName(), Fix.getEnvValue(content), false, true, false));
                            }
                            String errorMessage = getAddOnFix(l.getAddOn(), content);
                            error.setFixed(errorMessage);
                        }
                    }
                }
            }
        }
        return ret;
    }

    @Override
    public List<IdentifiedError> getErrors() {
        List<IdentifiedError> ret = new ArrayList<>();
        for (Set<IdentifiedError> err : errors.values()) {
            ret.addAll(err);
        }
        return ret;
    }
}
