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
package org.eclipse.andmore.android.launch;

import org.eclipse.andworx.ddms.devices.Devices;
import org.eclipse.andworx.ddms.devices.DeviceProfile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

public class DeviceLabelProvider extends LabelProvider {

	private final IProject project;
	private final Devices deviceManager;
	
	public DeviceLabelProvider(IProject project, Devices deviceManager) {
		super();
		this.project = project;
		this.deviceManager = deviceManager;
	}
	
	@Override
	public String getText(Object element) {
		String result = "";
		if (element instanceof DeviceProfile) {
			DeviceProfile emulatorInstance = (DeviceProfile) element;
			result = emulatorInstance.getName();
			int emulatorApi = emulatorInstance.getAPILevel();
			String emulatorTarget = emulatorInstance.getTarget();
			result += " (" + emulatorTarget + ", Api version " + emulatorApi + ")";
		}/* else if (serialNumbered instanceof IInstance) {
			IInstance instance = (IInstance) serialNumbered;
			Properties properties = instance.getProperties();
			if (properties != null) {
				String target = properties.getProperty("ro.build.version.release"); //$NON-NLS-1$
				if (target != null) {
					result += " (Android " + target + ")";
				}
			}
		}*/
		return result;
	}

	@Override
	public Image getImage(Object element) {

		Image img = null;

		DeviceProfile emulatorInstance = (DeviceProfile) element;
		IStatus compatible = deviceManager.isCompatible(project, emulatorInstance);

		// Notify the warning state
		if (compatible.getSeverity() == IStatus.WARNING) {
			img = PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_WARN_TSK);
		}
		return img;
	}
}
