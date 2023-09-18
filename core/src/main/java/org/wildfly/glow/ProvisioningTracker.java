/*
 * Copyright 2016-2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.glow;

import org.jboss.galleon.Constants;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.jboss.galleon.api.Provisioning;

/**
 *
 * @author jdenise@redhat.com
 * @param <T>
 */
public class ProvisioningTracker<T> implements ProgressCallback<T> {

    private static final String DELAYED_EXECUTION_MSG = "Delayed generation, waiting...";
    //private final Log log;
    private final String msgStart;
    private long lastTime;
    private final boolean delayed;
    private final GlowMessageWriter writer;

    private ProvisioningTracker(String msgStart, boolean delayed, GlowMessageWriter writer) {
        //this.log = log;
        this.msgStart = msgStart;
        this.delayed = delayed;
        this.writer = writer;
    }

    @Override
    public void starting(ProgressTracker<T> tracker) {
        writer.info(msgStart);
        lastTime = System.currentTimeMillis();
    }

    @Override
    public void processing(ProgressTracker<T> tracker) {
        // The case of config generated in forked process.
        if (delayed && tracker.getItem() == null) {
            writer.info(DELAYED_EXECUTION_MSG);
            return;
        }
        // Print a message every 5 seconds
        if (System.currentTimeMillis() - lastTime > 5000) {
            if (tracker.getTotalVolume() > 0) {
                writer.info(String.format("%s of %s (%s%%)",
                        tracker.getProcessedVolume(), tracker.getTotalVolume(),
                        ((double) Math.round(tracker.getProgress() * 10)) / 10));
            } else {
                writer.info("In progress...");
            }
            lastTime = System.currentTimeMillis();
        }
    }

    @Override
    public void processed(ProgressTracker<T> tracker) {
    }

    @Override
    public void pulse(ProgressTracker<T> tracker) {
    }

    @Override
    public void complete(ProgressTracker<T> tracker) {
    }

    public static void initTrackers(Provisioning pm, GlowMessageWriter writer) {
        pm.setProgressCallback(Constants.TRACK_PACKAGES,
                new ProvisioningTracker<String>("Installing packages", false, writer));
        pm.setProgressCallback(Constants.TRACK_CONFIGS,
                new ProvisioningTracker<String>("Generating configurations", true, writer));
        pm.setProgressCallback(Constants.TRACK_LAYOUT_BUILD,
                new ProvisioningTracker<String>("Resolving feature-packs", false, writer));
        pm.setProgressCallback("JBMODULES",
                new ProvisioningTracker<String>("Resolving artifacts", false, writer));
        pm.setProgressCallback("JBEXTRACONFIGS",
                new ProvisioningTracker<String>("Generating extra configurations", true, writer));
    }
}
