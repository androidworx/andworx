/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.sdkuilib.internal.repository.avd;

import java.util.Map;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.sdkuilib.internal.repository.ITask;
import com.android.sdkuilib.internal.repository.ITaskMonitor;
import com.android.utils.GrabProcessOutput;
import com.android.utils.GrabProcessOutput.IProcessOutput;
import com.android.utils.GrabProcessOutput.Wait;

/**
 * Task to execute an AVD in the Android emulator. The emulator runs in a command shell
 * from which the output is collected and filtered to inform the user of progress.
 * @author Andrew Bowley
 *
 * 17-12-2017
 */
public class EmulatorTask implements ITask {

	private final String DISPLAY = "DISPLAY";
	private final String avdName;
	private final String[] command;
	
	/**
	 * Construct EmulatorTask object 
	 * @param avdName Name of AVD to emulate
	 * @param command Dommand line parameters
	 */
	public EmulatorTask(String avdName, String[] command) {
		this.avdName = avdName;
		this.command = command;
	}

	/**
	 * A task that executes and updates a monitor to display it's status.
	 * The task will be run in a separate job.
	 * @param monitor Progress monitor
	 */
	@Override
	public void run(ITaskMonitor monitor) {
        try {
            monitor.setDescription(
                    "Starting emulator for AVD '%1$s'",
                    avdName);
            monitor.log("Starting emulator for AVD '%1$s'", avdName);

            // We'll wait 100ms*100 = 10s. The emulator sometimes seem to
            // start mostly OK just to crash a few seconds later. 10 seconds
            // seems a good wait for that case.
            int n = 100;
            monitor.setProgressMax(n);
            String[] environmentVars = null;
            if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX) {
            	// Add DISPLAY environmental variable to prevent error: "QXcbConnection: Could not connect to display"
            	for (Map.Entry<String, String> env: System.getenv().entrySet()) {
            		if (DISPLAY.equals(env.getKey())) {
            			environmentVars = new String[] { DISPLAY + "=" + env.getValue() };
            			break;
            		}
            	}
            }
            if (environmentVars == null)
            	environmentVars = new String[] {};
            Process process = Runtime.getRuntime().exec(command, environmentVars);
            GrabProcessOutput.grabProcessOutput(
                    process,
                    Wait.ASYNC,
                    new IProcessOutput() {
                        @Override
                        public void out(@Nullable String line) {
                            filterStdOut(line, monitor);
                        }

                        @Override
                        public void err(@Nullable String line) {
                            filterStdErr(line, monitor);
                        }
                    });

            // This small wait prevents the dialog from closing too fast:
            // When it works, the emulator returns immediately, even if
            // no UI is shown yet. And when it fails (because the AVD is
            // locked/running) this allows us to have time to capture the
            // error and display it.
            for (int i = 0; i < n; i++) {
                try {
                    Thread.sleep(100);
                    monitor.incProgress(1);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        } catch (Exception e) {
            monitor.logError("Failed to start emulator: %1$s",
                    e.getMessage());
        }
	}
	
    private void filterStdOut(String line, ITaskMonitor monitor) {
        if (line == null) {
            return;
        }

        // Skip some non-useful messages.
        if (line.indexOf("NSQuickDrawView") != -1) { //$NON-NLS-1$
            // Discard the MacOS warning:
            // "This application, or a library it uses, is using NSQuickDrawView,
            // which has been deprecated. Apps should cease use of QuickDraw and move
            // to Quartz."
            return;
        }

        if (line.toLowerCase().indexOf("error") != -1 ||                //$NON-NLS-1$
                line.indexOf("qemu: fatal") != -1) {                    //$NON-NLS-1$
            // Sometimes the emulator seems to output errors on stdout. Catch these.
        	monitor.logError("%1$s", line);                                   //$NON-NLS-1$
            return;
        }
        monitor.log("%1$s", line);                                            //$NON-NLS-1$
    }

    private void filterStdErr(String line, ITaskMonitor monitor) {
        if (line == null) {
            return;
        }

        if (line.indexOf("emulator: device") != -1 ||                   //$NON-NLS-1$
                line.indexOf("HAX is working") != -1) {                 //$NON-NLS-1$
            // These are not errors. Output them as regular stdout messages.
        	monitor.log("%1$s", line);                                        //$NON-NLS-1$
            return;
        }

        monitor.logError("%1$s", line);                                       //$NON-NLS-1$
    }
}
