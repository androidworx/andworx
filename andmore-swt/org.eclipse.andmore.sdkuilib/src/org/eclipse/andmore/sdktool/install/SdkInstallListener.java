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

package org.eclipse.andmore.sdktool.install;

import org.eclipse.andmore.sdktool.preferences.AndroidSdk;

/**
 * Callback for completion of SDK install
 * @author Andrew Bowley
 *
 * 08-12-2017
 */
public interface SdkInstallListener {
	/**
	 * Handle SDK installed event
	 * @param androidSdk Android SDK specification
	 */
	void onSdkInstall(AndroidSdk androidSdk);

	/**
	 * Handle SDK assigned event
	 * @param androidSdk Android SDK specification
	 */
	void onSdkAssign(AndroidSdk androidSdk);

	/** Handle SDK install cancelled or failed */
	void onCancel();
}

