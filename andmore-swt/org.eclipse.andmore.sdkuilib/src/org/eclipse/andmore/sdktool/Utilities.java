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
/**
 * 
 */
package org.eclipse.andmore.sdktool;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.avd.AvdInfo;

/**
 * @author Andrew Bowley
 *
 * 11-11-2017
 */
public class Utilities {
    public enum Compatibility {
        YES,
        NO,
        UNKNOWN,
    };

	/**
	 * Format file size given value as number of bytes.
	 * Taken from deprecated Archive class
	 * @param size Number of bytes
	 * @return text size formatted according to scale up to gigabytes
	 */
	public static String formatFileSize(long size) {
        String sizeStr;
        if (size < 1024) {
            sizeStr = String.format("%d Bytes", size);
        } else if (size < 1024 * 1024) {
            sizeStr = String.format("%d KiB", Math.round(size / 1024.0));
        } else if (size < 1024 * 1024 * 1024) {
            sizeStr = String.format("%.1f MiB",
                    Math.round(10.0 * size / (1024 * 1024.0))/ 10.0);
        } else {
            sizeStr = String.format("%.1f GiB",
                    Math.round(10.0 * size / (1024 * 1024 * 1024.0))/ 10.0);
        }
        return sizeStr;
    }


    @Nullable
    public static AndroidVersion getDeviceVersion(@NonNull IDevice device) {
        try {
        	Future<String> future = device.getSystemProperty(IDevice.PROP_BUILD_API_LEVEL);
            String apiLevel = null;
				apiLevel = future.get();
            if (apiLevel == null) {
                return null;
            }
            future = device.getSystemProperty(IDevice.PROP_BUILD_CODENAME);
            return new AndroidVersion(Integer.parseInt(apiLevel),
            		future.get());
        } catch (NumberFormatException | InterruptedException | ExecutionException e) {
            return null;
        }
    }

    /**
     * Returns whether the specified AVD can run the given project that is built against
     * a particular SDK and has the specified minApiLevel.
     * @param avd AVD to check compatibility for
     * @param avdTarget AVD target
     * @param projectTarget project build target
     * @param minApiVersion project min api level
     * @return whether the given AVD can run the given application
     */
    public static Compatibility canRun(AvdInfo avd, IAndroidTarget avdTarget, IAndroidTarget projectTarget,
            AndroidVersion minApiVersion) {
        if (avd == null) {
            return Compatibility.UNKNOWN;
        }

        if (avdTarget == null) {
            return Compatibility.UNKNOWN;
        }

        // for platform targets, we only need to check the min api version
        if (projectTarget.isPlatform()) {
            return avdTarget.getVersion().canRun(minApiVersion) ?
                    Compatibility.YES : Compatibility.NO;
        }

        // for add-on targets, delegate to the add on target to check for compatibility
        return projectTarget.canRunOn(avdTarget) ? Compatibility.YES : Compatibility.NO;
    }
}
