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

import org.eclipse.andworx.log.SdkLogger;

import com.android.ddmlib.IDevice;

/**
 * Handles events associated with launching an application on a device
 */
public class LaunchStartListener implements DeviceStartListener {

	private static SdkLogger logger = SdkLogger.getLogger(LaunchStartListener.class.getName());
	
	private volatile boolean isStarted = false;
	private volatile boolean isSignalled = false;
	
	public boolean isStarted() {
		return isStarted;
	}

	public boolean isSignalled() {
		return isSignalled;
	}
	
	@Override
	public void onDeviceStart(IDevice device) {
		logger.info("Device %s connected", device.getName());
		isStarted = true;
		signal(); 
	}

	@Override
	public void onError(String message) {
		if (!isSignalled) {
			logger.error(null, message);
			signal();
		}
	}

	@Override
	public void onTimeout(String instanceName) {
		if (!isSignalled) {
			logger.error(null, "%s timed out waiting for start up", instanceName);
			signal(); 
		}
	}

	private void signal() {
		synchronized(this) {
			isSignalled = true;
			notifyAll();
		}
	}
}
