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

import com.android.annotations.NonNull;
import com.android.builder.sdk.DefaultSdkLoader;
import com.android.builder.sdk.SdkInfo;
import com.android.builder.sdk.SdkLoader;
import com.android.utils.ILogger;

/**
 * AndroidBuilder class depends on non-visible SdkInfo class.
 * This class is a wrapper to create SdkInfo indirectly from DefaultSdkLoader
 * SdkInfo provides annotations.jar file and ADB location
 */
public class SdkInfoWrapper {
	
    /** General information about the SDK */
	private final SdkInfo sdkInfo;

	/**
	 * Construct SdkInfo object
	 * @param sdkLocation SDK location
	 * @param logger  Android SDK logger
	 */
	public SdkInfoWrapper(@NonNull File sdkLocation, @NonNull ILogger logger) {
		synchronized (DefaultSdkLoader.class) {
			DefaultSdkLoader.unload();
			SdkLoader sdkLoader = DefaultSdkLoader.getLoader(sdkLocation);
			sdkInfo = sdkLoader.getSdkInfo(logger);
		}
	}
    
	public SdkInfo getSdkInfo() {
		return sdkInfo;
	}
}
