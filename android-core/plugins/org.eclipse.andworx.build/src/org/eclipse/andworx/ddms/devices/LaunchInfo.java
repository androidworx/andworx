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
package org.eclipse.andworx.ddms.devices;

/*
 * Application launch information. Must be reset when launch ends, either by successful completion or failure.
 */
public class LaunchInfo {

	public static interface Launcher {
		boolean startEmulator();
	}
	
	private String applicationName;
	private DeviceProfile deviceProfile;
	private Launcher launcher;

	/**
	 * Construct LaunchInfo object
	 * @param deviceProfile Device profile
	 * @param applicationName Name of application being launched
	 */
	public LaunchInfo(DeviceProfile deviceProfile, String applicationName) {
		this.deviceProfile = deviceProfile;
		this.applicationName = applicationName;
	}

	/**
	 * Reset to initial state
	 */
	public void reset() {
		applicationName = null;
	}

	public DeviceProfile getDeviceProfile() {
		return deviceProfile;
	}

	public String getApplicationName() {
		return applicationName;
	}

	public Launcher getLauncher() {
		return launcher;
	}

	public void setLauncher(Launcher launcher) {
		this.launcher = launcher;
	}

}
