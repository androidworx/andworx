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
package org.eclipse.andworx.sdk;

import java.io.File;

import org.eclipse.jface.util.IPropertyChangeListener;

/**
 * Conceals details of how the Android SDK location is persisted
 */
public interface AndroidSdkPreferences {
	/**
	 * Returns SDK location recorded in Android home, if it exists and is valid
	 * @return File object
	 */
	File getLastSdkPath();

	/**
	 * Returns Android SDK location
	 * @return File object or null if not available
	 */
	File getSdkLocation();
	
	/**
	 * Set SDK location in preference store
	 * @param location SDK location
	 */
	void setSdkLocation(File location);
	
	/**
	 * Returns flag set true if SDK is set in preferences
	 * @return boolean
	 */
	boolean isSdkSpecified();

	/**
     * Adds a property change listener to this preference store to be notified when the SDK location changes
     * @param listener A property change listener
	 */
	void addPropertyChangeListener(IPropertyChangeListener listener);

	/**
	 * Returns SDK location value from preference store
	 * @return SDK location
	 */
	String getSdkLocationValue();

	/**
	 * Write preference store to disk
	 * @return flag set true if operation completed successfully
	 */
	boolean save();
}
