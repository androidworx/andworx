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
package org.eclipse.andworx.build;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.andworx.sdk.SdkListener;
import org.eclipse.andworx.sdk.SdkProfile;
import org.eclipse.andworx.sdk.TargetLoadStatusMonitor;

/**
 * Provides access to the current Android SDK
 */
public class SdkTracker {

    /** Current SDK details - may be null */
    private volatile SdkProfile sdkProfile;

    /** List of listeners for SDK loaded */
    private final List<SdkListener> sdkListeners;
    /** Application component object factory */
    private final AndworxFactory objectFactory;
    /** Tracks Target Data Load Status */
	private TargetLoadStatusMonitor targetLoadStatusMonitor;

	/**
	 * Construct SdkTracker object
	 * @param objectFactory Object factory
	 */
    public SdkTracker(AndworxFactory objectFactory) {
    	this.objectFactory = objectFactory;
		sdkListeners = new ArrayList<>();
    }

    /**
     * Returns Information on an Android SDK installation and environment
     * @return SdkProfile object
     */
	public SdkProfile getSdkProfile() {
		return sdkProfile;
	}

	/**
	 * Get SDK details for given SDK location. If valid, update currentSdk field and notify SDK listeners.
	 * @param sdkLocation SDK file system path
	 * @return CurrentSdk object for given location
	 */
	public SdkProfile setCurrentSdk(String sdkLocation) {
		SdkProfile trialSdk = new SdkProfile(sdkLocation);
		if (trialSdk.isValid()) {
			if (targetLoadStatusMonitor != null)
				sdkProfile.setTargetLoadStatusMonitor(targetLoadStatusMonitor);
	    	//System.out.println("Notifying " + sdkListeners.size() + " SDK listeners");
			objectFactory.setAndroidEnvironment(trialSdk);
			sdkProfile = trialSdk;
			
			synchronized(sdkListeners) {
				sdkListeners.forEach(sdkListener -> sdkListener.onLoadSdk(trialSdk));
			}
		}
		return trialSdk;
	}

	/**
	 * Adds listener to be notified when a valid SDK is loaded
	 * @param sdkListener SDK listener
	 */
	public void addSdkListener(SdkListener sdkListener) {
		synchronized(sdkListeners) {
			sdkListeners.add(sdkListener);
			if (sdkProfile != null)
				sdkListener.onLoadSdk(sdkProfile);
		}
	}

	/**
	 * Sets Target Data Load Status Monitor
	 * @param monitor
	 */
	public void setTargetLoadStatusMonitor(TargetLoadStatusMonitor monitor) {
		this.targetLoadStatusMonitor = monitor;
		if (sdkProfile != null)
			sdkProfile.setTargetLoadStatusMonitor(monitor);
	}
}
