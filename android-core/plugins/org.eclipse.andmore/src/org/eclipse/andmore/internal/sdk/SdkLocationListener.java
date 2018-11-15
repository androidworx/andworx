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

package org.eclipse.andmore.internal.sdk;

import java.io.File;
import java.util.List;

import com.android.sdklib.IAndroidTarget;

/**
 * Callback to handle event SDK location changed
 * @author Andrew Bowley
 *
 */
public interface SdkLocationListener {
	/**
	 * Handle event SDK location changed
	 * @param sdkLocation SDK location
	 * @param isValid Flag set true if location contains the minimum required packages
	 * @param targetList IAndroidTarget list, possibly empty
	 */
	void onSdkLocationChanged(File sdkLocation, boolean isValid, List<IAndroidTarget> targetList);
}
