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
package com.android.sdkuilib.internal.repository.avd;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.sdkuilib.internal.repository.content.PackageType;

/**
 * System image wrapper which allows an AVD configuration to reference an image which does not exist in current SDK
 * @author Andrew Bowley
 *
 * 01-12-2017
 */
public class SystemImageInfo {
	private static final String BLANK = "";
    /** System image wrapped by this object */
	private SystemImage systemImage;
	/** The Android version of the owning AVD */
	private AndroidVersion avdAndroidVersion;

	/**
	 * Construct SystemImageInfo object for given AVD info
	 * @param avd The AVD information
	 */
	public SystemImageInfo(AvdInfo avd) {
		this.systemImage = (SystemImage) avd.getSystemImage();
		avdAndroidVersion = avd.getAndroidVersion();
	}

	/**
	 * Returns flag set true if AVD has system image
	 * @return boolean
	 */
	public boolean hasSystemImage() {
		return systemImage != null;
	}

	/**
	 * Returns system image
	 * @return SystemImage object
	 */
	public SystemImage getSystemImage() {
		return systemImage;
	}

	/**
	 * Set system image
	 * @param systemImage The system image
	 */
	public void setSystemImage(SystemImage systemImage) {
		this.systemImage = systemImage;
	}

	/**
	 * Returns Android version of the sustem image, if available, otherwise AVD Android version
	 * @return AndroidVersion object
	 */
	public AndroidVersion getAndroidVersion() {
		if (hasSystemImage()) {
	        DetailsTypes.ApiDetailsType details =
	                (DetailsTypes.ApiDetailsType) systemImage.getPackage().getTypeDetails();
	        return details.getAndroidVersion();
		}
		return avdAndroidVersion;
	}

	/**
	 * Returns package type characterisation of system image type
	 * @return PackageType enum
	 */
	public PackageType getPackageType() {
		if (hasSystemImage()) {
	        DetailsTypes.ApiDetailsType details =
	                (DetailsTypes.ApiDetailsType) systemImage.getPackage().getTypeDetails();
	        if (details instanceof DetailsTypes.PlatformDetailsType) {
	        	return PackageType.platforms;
	        } else if (details instanceof DetailsTypes.SysImgDetailsType) {
	        	return PackageType.system_images;
	        } else if (details instanceof DetailsTypes.AddonDetailsType) {
	        	return PackageType.add_ons;
	        }
		}
		// Default to platforms in absence of system image
		return PackageType.platforms;
	}

	/**
	 * Returns vendor of system image
	 * @return vendor or blank if vendor not available
	 */
    public String getVendor() {
		if (hasSystemImage()) {
	        PackageType packageType = getPackageType();
	        DetailsTypes.ApiDetailsType details =
	                (DetailsTypes.ApiDetailsType) systemImage.getPackage().getTypeDetails();
	        if (packageType == PackageType.system_images) {
	            IdDisplay vendorId = ((DetailsTypes.SysImgDetailsType) details).getVendor();
	            if (vendorId != null) {
	                return vendorId.getDisplay();
	            }
	        } else if (packageType == PackageType.add_ons) {
	        	return ((DetailsTypes.AddonDetailsType) details).getVendor().getDisplay();
	        }
		}
		return BLANK;
    }
}
