/*
 * Copyright (C) 2011 - 2018 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.annotations.NonNull;

/** 
 * Checks status of Android SDK location configuration
 */
public class AndroidSdkValidator {

    private static final String SDK_FOLDER_NOT_FOUND = "Could not find SDK folder '%1$s'";
	private static final String PLATFORM_TOOLS_MISSING = "SDK Platform Tools component is missing!\n" +
                                                         "Please use the SDK Manager to install it.";
	private static final String TOOLS_MISSING = "SDK Tools component is missing!\n" +
                                                "Please use the SDK Manager to install it.";
	private static final String FILE_NOT_FOUND = "Could not find %1$s!";
	private static final String SDK_NOT_SETUP = "Location of the Android SDK has not been setup in the preferences.";
	
	private final AndroidSdkPreferences androidSdkPreferences;

	/**
	 * Construct AndroidSdkValidator object
	 * @param androidSdkPreferences Android SDK location persistence
	 */
	public AndroidSdkValidator(AndroidSdkPreferences androidSdkPreferences) {
		this.androidSdkPreferences = androidSdkPreferences;
	}

	/**
	 * Check if SDK location not set in preferences but available in Android home
	 */
    public boolean isSdkLocationConfigured() {
        if (!androidSdkPreferences.isSdkSpecified()) {
            // If we've recorded an SDK location in the .android settings, then the user
            // has run ADT before but possibly in a different workspace. We don't want to pop up
            // the welcome wizard each time if we can simply use the existing SDK install.
            File osSdkPath = androidSdkPreferences.getLastSdkPath();

             if ((osSdkPath != null) &&
                 osSdkPath.isDirectory() &&
                 checkSdkLocationAndId(osSdkPath, new QuietSdkValidator())) {
                     // Yes, we've seen an SDK location before and we can use it again,
                     // no need to pester the user with the welcome wizard.
                     // This also implies that the user has responded to the usage statistics
                     // question.
                     androidSdkPreferences.setSdkLocation(osSdkPath);
                     return true;
            }
            return false;
        }
        return checkSdkLocationAndId(androidSdkPreferences.getSdkLocation(), new QuietSdkValidator());
   }
   
	/**
     * Internal helper to perform the actual sdk location and id check.
     * <p/>
     * This is useful for callers who want to override what happens when the check
     * fails. Otherwise consider calling {@link #checkSdkLocationAndId()} that will
     * present a modal dialog to the user in case of failure.
     *
     * @param osSdkLocation The sdk directory, an OS path. Can be null.
     * @param errorHandler An checkSdkErrorHandler that can display a warning or an error.
     * @return False if there was an error or the result from the errorHandler invocation.
     */
    public boolean checkSdkLocationAndId(@NonNull File osSdkLocation,
                                         @NonNull CheckSdkErrorHandler errorHandler) {
        if (osSdkLocation == null) {
            return errorHandler.handleError(
                    CheckSdkErrorHandler.Solution.OPEN_ANDROID_PREFS,
                    SDK_NOT_SETUP);
        }

        if (!osSdkLocation.isDirectory()) {
            return errorHandler.handleError(
                    CheckSdkErrorHandler.Solution.OPEN_ANDROID_PREFS,
                    String.format(SDK_FOLDER_NOT_FOUND, osSdkLocation));
        }

        // Check that we have both the tools component and the platform-tools component.
        File platformTools = new File(osSdkLocation, SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER);
        if (!platformTools.isDirectory()) {
            return errorHandler.handleWarning(
                    CheckSdkErrorHandler.Solution.OPEN_SDK_MANAGER,
                    PLATFORM_TOOLS_MISSING);
        }

        File tools = new File(osSdkLocation, SdkConstants.OS_SDK_TOOLS_FOLDER);
        if (!tools.isDirectory()) {
            return errorHandler.handleError(
                    CheckSdkErrorHandler.Solution.OPEN_SDK_MANAGER,
                    TOOLS_MISSING);
        }

        // Check the path to various tools we use to make sure nothing is missing. This is
        // not meant to be exhaustive.
        File[] filesToCheck = new File[] {
                new File(osSdkLocation, getOsRelativeAdb()),
                new File(osSdkLocation, getOsRelativeEmulator())
        };
        for (File file : filesToCheck) {
            if (!file.isFile()) {
                return errorHandler.handleError(
                        CheckSdkErrorHandler.Solution.OPEN_ANDROID_PREFS,
                        String.format(FILE_NOT_FOUND, file));
            }
        }
        return true;
    }

    /** Returns the adb path relative to the sdk folder */
    public static String getOsRelativeAdb() {
        return SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER + SdkConstants.FN_ADB;
    }

    /** Returns the emulator path relative to the sdk folder */
    public static String getOsRelativeEmulator() {
        return SdkConstants.OS_SDK_TOOLS_FOLDER + SdkConstants.FN_EMULATOR;
    }

}
