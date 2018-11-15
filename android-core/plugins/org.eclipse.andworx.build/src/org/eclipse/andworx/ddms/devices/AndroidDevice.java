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
package org.eclipse.andworx.ddms.devices;

import com.android.annotations.Nullable;

/**
 * Android device instance
 */
public class AndroidDevice implements DeviceProfile {

	/** Display name */
	private final String name;
	/** Flag set true if device is an emulator */
	private final boolean isEmulator;
	/** Device manager - monitors device state */
	private final Devices devices;
	/** Target hash string - key to obtain target instance */
	private String target;
	/** Target API */
	private int apiLevel;
	/** Launch information */
	private LaunchInfo launchInfo;

	/**
	 * Construct AndroidDevice object
	 * @param name Display name
	 * @param isEmulator Flag set true if device is an emulator
	 * @param devices Device manager - monitors device state
	 */
	public AndroidDevice(String name, boolean isEmulator, Devices devices) {
		this.name = name;
		this.isEmulator = isEmulator;
		this.devices = devices;
		// Set default values to indicate "ot configured"
		target = "";
		apiLevel = 1;
	}
	
	/**
	 * Returns the device display name.
	 * @return name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Set Target hash string - key to obtain target instance
	 * @param target
	 */
	public void setTarget(String target) {
		this.target = target;
	}

	/**
	 * SetTarget API
	 * @param apiLevel
	 */
	public void setApiLevel(int apiLevel) {
		this.apiLevel = apiLevel;
	}

	@Override
	public void setLaunchInfo(LaunchInfo launchInfo) {
		this.launchInfo = launchInfo;
	}

	/**
	 * Return flag set true if the device is started
	 * @return boolean
	 */
	@Override
	public boolean isStarted() {
		String serialNumber = devices.getSerialNumberByName(name);	
		return (serialNumber != null) &&
				devices.isStarted(serialNumber);
	}

	@Override
	public boolean isEmulator() {
		return isEmulator;
	}
	
	@Override
	public String getTarget() {
		return target;
	}

	@Override
	public int getAPILevel() {
		return apiLevel;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if ((obj == null) || !(obj instanceof DeviceProfile))
			return false;
		return name.equals(((DeviceProfile)obj).getName());
	}

	@Nullable
	@Override
	public LaunchInfo getLaunchInfo() {
		return launchInfo;
	}

}
