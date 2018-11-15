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

/**
 * Defines a device instance with emulator state information
 */
public interface DeviceProfile {

	/**
	 * Returns the instance name in simplified format
	 * @return The instance name
	 */
	String getName();
	
	/**
	 * Tests if the instance is started
	 * @return True if it is started; false otherwise
	 */
	boolean isStarted();

	/**
	 * Returns flag set true if device is emulator
	 * @return boolean
	 */
	boolean isEmulator();

	/**
	 * Returns the Android target that the instance is compliant to
	 * @return Android target that the instance is compliant to
	 */
	public String getTarget();

	/**
	 * Returns the Android API level that the instance is compliant to
	 * @return Android API level that the instance is compliant to
	 */
	public int getAPILevel();

	public void setLaunchInfo(LaunchInfo launchInfo);
	
	/**
	 * Returns launch information. This is transient data controlled by the owner.
	 * return launchInfo or null if not set
	 */
	public LaunchInfo getLaunchInfo();

}
