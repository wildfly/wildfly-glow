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
package org.wildfly.glow;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import org.jboss.galleon.api.APIVersion;
import org.jboss.galleon.api.GalleonBuilder;

/**
 *
 * @author jdenise
 */
public class StabilitySupport {

    private static final String STABILITY_CLASS_NAME = "org.jboss.galleon.Stability";
    private static final String STABILITY_FROM_METHOD = "fromString";
    private static final String STABILITY_ENABLES_METHOD = "enables";

    public static boolean enables(String stability1, String stability2) throws Exception {
        GalleonBuilder builder = new GalleonBuilder();
        URLClassLoader loader = builder.getCoreClassLoader(APIVersion.getVersion());
        Class<?> clazz = loader.loadClass(STABILITY_CLASS_NAME);
        Method m = clazz.getMethod(STABILITY_FROM_METHOD, String.class);
        Object stab1 = m.invoke(null, stability1);
        Object stab2 = m.invoke(null, stability2);
        Method enables = clazz.getMethod(STABILITY_ENABLES_METHOD, clazz);
        Boolean b = (Boolean) enables.invoke(stab1, stab2);
        return b;
    }

    public static void checkStability(String stability) throws Exception {
        GalleonBuilder builder = new GalleonBuilder();
        URLClassLoader loader = builder.getCoreClassLoader(APIVersion.getVersion());
        Class<?> clazz = loader.loadClass(STABILITY_CLASS_NAME);
        Method from = clazz.getMethod(STABILITY_FROM_METHOD, String.class);
        from.invoke(null, stability);
    }
}
