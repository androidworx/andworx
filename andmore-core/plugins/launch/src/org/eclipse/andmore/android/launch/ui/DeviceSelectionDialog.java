/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.eclipse.andmore.android.launch.ui;

import java.util.Collection;

import org.eclipse.andmore.android.launch.DeviceLabelProvider;
import org.eclipse.andmore.android.launch.LaunchPlugin;
import org.eclipse.andmore.android.launch.i18n.LaunchNLS;
import org.eclipse.andworx.ddms.devices.Devices;
import org.eclipse.andworx.ddms.devices.DeviceProfile;
import org.eclipse.andworx.build.AndworxFactory;
import org.eclipse.core.resources.IProject;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

/**
 * DESCRIPTION: This class implements the device selection dialog
 *
 * RESPONSIBILITY: Provides a dialog populated with the available device
 * instances
 *
 * COLABORATORS: None.
 *
 * USAGE: This class is intended to be used by Eclipse only
 */
public class DeviceSelectionDialog extends ElementListSelectionDialog {
	private static final String DEV_SELECTION_CONTEXT_HELP_ID = LaunchPlugin.PLUGIN_ID + ".deviceSelectionDialog";
	private static Devices deviceManager = AndworxFactory.instance().getDevices();

	/**
	 * Default constructor
	 * 
	 * @param parent
	 *            Parent shell
	 * @param description
	 *            Dialog description
	 */
	public DeviceSelectionDialog(Shell parent, String description, final IProject project) {
		super(parent, new DeviceLabelProvider(project, deviceManager));

		this.setTitle(LaunchNLS.UI_LaunchComposite_SelectDeviceScreenTitle);
		this.setMessage(description);
        AndworxFactory objectFactory = AndworxFactory.instance();
        Devices deviceManager = objectFactory.getDevices();
		Collection<DeviceProfile> instances = deviceManager.getAllDevicesSorted();
		if ((project != null) && (instances != null) && (instances.size() > 0)) {
			Collection<DeviceProfile> filteredInstances = deviceManager.filterInstancesByProject(instances, project);
			Object[] filteredInstancesArray = filteredInstances.toArray();
			this.setElements(filteredInstancesArray);
		}

		this.setHelpAvailable(true);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, DEV_SELECTION_CONTEXT_HELP_ID);
	}
}
