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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;

public abstract class NestedWarOrExplodedArchiveFileVisitor implements FileVisitor<Path> {

    private final Path rootPath;
    private final boolean archive;

    public NestedWarOrExplodedArchiveFileVisitor(Path rootPath, boolean archive) {
        this.rootPath = rootPath;
        this.archive = archive;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        return shouldTraversePossibleArchiveDirectory(dir) ? CONTINUE : SKIP_SUBTREE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        return CONTINUE;
    }

    protected boolean shouldTraversePossibleArchiveDirectory(Path dir) {
        if (dir.equals(rootPath) || archive) {
            // If we are an archive, we should continue inspecting children as normal
            // Similarly, if we are an exploded archive, and we are the root folder we should inspect the children
            return true;
        }
        if (DeploymentScanner.ArchiveType.isArchiveName(dir.getFileName())) {
            return false;
        }
        return true;
    }
}
