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

package com.android.sdkuilib.widgets;

import org.eclipse.andmore.base.resources.PluginResourceProvider;
import org.eclipse.andmore.sdktool.preferences.AndroidSdk;
import org.eclipse.swt.widgets.Composite;

import com.android.utils.ILogger;

/**
 * Contract for control which contains an AndroidSdkSelector
 * @author Andrew Bowley
 *
 * 20-12-2017
 */
public interface AndroidSdkComposite {
	/**
	 * Returns parent composite of AndroidSdkSelector
	 * @return Composite object
	 */
	Composite getParent();
	
	/**
	 * Returns image resources 
	 * @return PluginResourceProvider object
	 */
	PluginResourceProvider getResourceProvider();
	
	/**
	 * Handle event specified Android SDK has changed
	 * @param androidSdk Android SDK specification
	 */
	void onSdkChange(AndroidSdk androidSdk);
	
	/**
	 * Sets flag for page complete which for a wizard controls the "Finish" button
	 * @param isPageComplete Flag set ture if page is in a valid state to complete
	 */
	void setPageComplete(boolean isPageComplete);
	
	ILogger getLogger();
}
