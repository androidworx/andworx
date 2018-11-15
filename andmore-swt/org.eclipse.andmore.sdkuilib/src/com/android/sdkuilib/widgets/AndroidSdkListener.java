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

import org.eclipse.andmore.sdktool.preferences.AndroidSdk;
import org.eclipse.andmore.sdktool.install.SdkInstallListener;

/**
 * Callback to handle events to install a new SDK or update an existing one
 * @author Andrew Bowley
 *
 * 13-12-2017
 */
public interface AndroidSdkListener {
	/**
	 * Handle event start SDK install/update
	 * @param androidSdk Adroid SDK specification
	 * @param sdkInstallListener Callback for event installation completed
	 * @param usePacketFilter Flag set true if initial selections are for platforms only
	 */
	void onAndroidSdkCommand(AndroidSdk androidSdk, SdkInstallListener sdkInstallListener, boolean usePacketFilter);

	/**
	 * Handle event SDK changed
	 * @param androidSdk The dirty SDK
	 */
	void onAndroidSdkDirty(AndroidSdk androidSdk);
}
