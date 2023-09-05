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
// copied and adapted from Quarkus ExecUtil.java class at https://github.com/quarkusio/quarkus/blob/main/core/deployment/src/main/java/io/quarkus/deployment/util/ExecUtil.java
package org.wildfly.glow;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class ExecUtil {

    private static final int PROCESS_CHECK_INTERVAL = 500;

    private static class HandleOutput implements Runnable {

        private final InputStream is;
        private final GlowMessageWriter writer;

        HandleOutput(InputStream is, GlowMessageWriter writer) {
            this.is = is;
            this.writer = writer;
        }

        @Override
        public void run() {
            try (InputStreamReader isr = new InputStreamReader(is); BufferedReader reader = new BufferedReader(isr)) {

                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    writer.info(line);
                }
            } catch (IOException e) {
                System.err.println("Failed to handle output" + e);
            }
        }
    }

    /**
     * Execute the specified command from within the current directory.
     *
     * @param command The command
     * @param writer
     * @param args The command arguments
     * @return true if commands where executed successfully
     */
    public static boolean exec(String command, GlowMessageWriter writer, String... args) {
        return exec(new File("."), command, writer, args);
    }

    /**
     * Execute silently the specified command until the given timeout from
     * within the current directory.
     *
     * @param timeout The timeout
     * @param command The command
     * @param writer
     * @param args The command arguments
     * @return true if commands where executed successfully
     */
    public static boolean execSilentWithTimeout(Duration timeout, String command, GlowMessageWriter writer, String... args) {
        return execWithTimeout(new File("."), timeout, command, writer, args);
    }

    /**
     * Execute the specified command from within the specified directory.The
 method allows specifying an output filter that processes the command
 output.
     *
     * @param directory The directory
     * @param command The command
     * @param writer
     * @param args The command arguments
     * @return true if commands where executed successfully
     */
    public static boolean exec(File directory, String command, GlowMessageWriter writer,
            String... args) {
        try {
            Process process = startProcess(directory, command, args);
            new HandleOutput(process.getInputStream(), writer).run();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Execute the specified command until the given timeout from within the
     * specified directory.The method allows specifying an output filter that
 processes the command output.
     *
     * @param directory The directory
     * @param timeout The timeout
     * @param command The command
     * @param writer
     * @param args The command arguments
     * @return true if commands where executed successfully
     */
    public static boolean execWithTimeout(File directory,
            Duration timeout, String command, GlowMessageWriter writer, String... args) {
        try {
            Process process = startProcess(directory, command, args);
            Thread t = new Thread(new HandleOutput(process.getInputStream(), writer));
            t.setName("Process stdout");
            t.setDaemon(true);
            t.start();
            process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            destroyProcess(process);
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Start a process executing given command with arguments within the
     * specified directory.
     *
     * @param directory The directory
     * @param command The command
     * @param args The command arguments
     * @return the process
     */
    public static Process startProcess(File directory, String command, String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = command;
            if (args.length > 0) {
                System.arraycopy(args, 0, cmd, 1, args.length);
            }
            return new ProcessBuilder()
                    .directory(directory)
                    .command(cmd)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Input/Output error while executing command.", e);
        }
    }

    /**
     * Kill the process, if still alive, kill it forcibly
     *
     * @param process the process to kill
     */
    public static void destroyProcess(Process process) {
        process.destroy();
        int i = 0;
        while (process.isAlive() && i++ < 10) {
            try {
                process.waitFor(PROCESS_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    /**
     * Resolves which image binary to use.First {@code docker} is tried. If
 docker does not exist, then {@code podman} is tried. If podman does not
     * exist {@code null} is returned.
     *
     * @param writer
     * @return the resolved binary, or {@code null} if docker or podman was not
     * found
     */
    public static String resolveImageBinary(GlowMessageWriter writer) {
        try {
            if (execSilentWithTimeout(Duration.ofSeconds(3), "docker", writer, "-v")) {
                return "docker";
            }
        } catch (Exception ignore) {
        }
        try {
            if (execSilentWithTimeout(Duration.ofSeconds(3), "podman", writer, "-v")) {
                return "podman";
            }
        } catch (Exception ignore) {
        }
        return null;
    }

}
