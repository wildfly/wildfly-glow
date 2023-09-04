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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;

/**
 *
 * @author jdenise
 */
public class DirectoryIndexer {

    public static Index indexDirectory(File source, Indexer indexer) throws FileNotFoundException, IOException {
        scanFile(source, indexer);
        return indexer.complete();
    }

    private static void scanFile(File source, Indexer indexer) throws FileNotFoundException, IOException {
        if (source.isDirectory()) {
            File[] children = source.listFiles();
            if (children == null) {
                throw new FileNotFoundException("Source directory disappeared: " + source);
            }

            for (File child : children) {
                scanFile(child, indexer);
            }

            return;
        }

        if (!source.getName().endsWith(".class")) {
            return;
        }

        FileInputStream input = new FileInputStream(source);

        try {
            indexer.indexWithSummary(input);
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            System.err.println("ERROR: Could not index " + source.getName() + ": " + message);
        } finally {
            safeClose(input);
        }
    }

    private static void safeClose(FileInputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (Throwable t) {
                // EAT
            }
        }
    }
}
