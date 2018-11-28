/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.sdkuilib.internal.tasks;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.Locale;

import org.eclipse.andworx.build.AndworxContext;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.andworx.context.AndroidEnvironment;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdkuilib.internal.repository.avd.AvdAgent;
import com.android.sdkuilib.internal.repository.avd.EmulatorTask;
import com.android.sdkuilib.internal.repository.avd.SdkTargets;
import com.android.sdkuilib.internal.repository.avd.SystemImageInfo;
import com.android.sdkuilib.internal.widgets.AvdStartDialog;

/**
 * Launches virtual device preceded by options dialog. 
 */
public class EmulatorLauncher {

	public boolean launchEmulator(String avdName, Shell shell) {
		AndworxContext objectFactory = AndworxFactory.instance();
        AvdManager avdManager = objectFactory.getAvdManager();
        // Only get valid AVDs
		AvdInfo avdInfo = avdManager.getAvd(avdName, true);
		if (avdInfo == null) {
	        MessageDialog.openError(shell, AvdStartDialog.STARTING_EMULATOR, "AVD \"" + avdName + "\" is not found or not valid");
	        return false;
		}
		AvdAgent avdAgent = null;
        SystemImageInfo systemImageInfo = new SystemImageInfo(avdInfo);
        // The system image can be null if not found by AVD Manager
        if (systemImageInfo.hasSystemImage()) {
        	// Look up targets to find one with same Android version as this system image
        	AndroidEnvironment env = objectFactory.getAndroidEnvironment();
        	if (!env.isValid())
        		throw new IllegalStateException(SdkProfile.SDK_NOT_AVAILABLE_ERROR);
        	SdkTargets sdkTargets = new SdkTargets(env.getAndroidSdkHandler(), objectFactory.getSdkTracker().getSdkProfile().getProgressIndicator());
        	IAndroidTarget target = sdkTargets.getTargetForSysImage(systemImageInfo.getSystemImage());
        	if (target != null)
        		// The AVD Agent binds the target to the AVD Info object
        		avdAgent = new AvdAgent(target, avdInfo);
        } 
        if (avdAgent == null) {
	        MessageDialog.openError(shell, AvdStartDialog.STARTING_EMULATOR, "AVD \"" + avdName + "\" needs to be repaired");
	        return false;
		}
		return launchEmulator(avdAgent, shell);
	}
	
	public boolean launchEmulator(AvdAgent avdAgent, Shell shell) {
        AvdStartDialog dialog = new AvdStartDialog(
        		shell,
                avdAgent);
        int result[] = new int[1];
        Display.getDefault().syncExec(new Runnable() {

			@Override
			public void run() {
				result[0] = dialog.open();
				
			}});
        if (result[0] == Window.OK) {
            final String avdName = avdAgent.getAvd().getName();

            // build the command line based on the available parameters.
            ArrayList<String> list = new ArrayList<String>();
            list.add(dialog.getEmulatorPath().getAbsolutePath());
            list.add("-avd");       
            //$NON-NLS-1$
            list.add(avdName);
            //list.add("-use-system-libs");
            if (dialog.hasWipeData()) {
                list.add("-wipe-data");                   //$NON-NLS-1$
            }
            if (dialog.hasSnapshot()) {
                if (!dialog.hasSnapshotLaunch()) {
                    list.add("-no-snapshot-load");
                }
                if (!dialog.hasSnapshotSave()) {
                    list.add("-no-snapshot-save");
                }
            }
            float scale = dialog.getScale();
            if (scale != 0.f) {
                // do the rounding ourselves. This is because %.1f will write .4899 as .4
                scale = Math.round(scale * 100);
                scale /=  100.f;
                list.add("-scale");                       //$NON-NLS-1$
                // because the emulator expects English decimal values, don't use String.format
                // but a Formatter.
                Formatter formatter = new Formatter(Locale.US);
                formatter.format("%.2f", scale);   //$NON-NLS-1$
                list.add(formatter.toString());
                formatter.close();
            }

            // convert the list into an array for the call to exec.
            final String[] command = list.toArray(new String[list.size()]);

            // launch the emulator
            EmulatorTask emulatorTask = new EmulatorTask(avdName, command);
            Display.getDefault().syncExec(new Runnable() {

				@Override
				public void run() {
		            final ProgressTask progress = new ProgressTask(shell, AvdStartDialog.STARTING_EMULATOR);
		            progress.start(emulatorTask, null);
				}});
             return true;
        }
        return false;
	}
}
